package tn.binaa.app.reports

import tn.binaa.app.data.Repository
import tn.binaa.app.domain.Formats
import tn.binaa.app.domain.Money
import tn.binaa.app.domain.PayrollWeek
import java.time.LocalDate
import java.time.ZoneId

/** Money cell (millimes) — exported as a real number in Excel, formatted in PDF. */
data class M(val millimes: Long)

data class Report(
    val title: String,
    val subtitle: String,
    val headers: List<String>,
    val rows: List<List<Any>>,
    val totals: List<Any>? = null,
    val fileName: String,
)

fun cellText(v: Any): String = when (v) {
    is M -> Money.format(v.millimes)
    else -> v.toString()
}

class ReportBuilder(private val repo: Repository) {

    private suspend fun workerNames() = repo.workers.getAll().associate { it.id to it.fullName }
    private suspend fun projectNames() = repo.projects.getAll().associate { it.id to it.name }

    suspend fun dailyAttendance(date: LocalDate): Report {
        val names = workerNames(); val projects = projectNames()
        val recs = repo.attendance.getByDate(date.toEpochDay()).sortedBy { names[it.workerId] }
        val rows = recs.map { listOf(names[it.workerId] ?: "-", it.status.label, it.projectId?.let { p -> projects[p] } ?: "-", M(it.amount)) }
        return Report(
            "تقرير الحضور اليومي", "${Formats.dayName(date)} ${Formats.date(date)}",
            listOf("العامل", "الحالة", "المشروع", "الأجر"), rows,
            listOf("المجموع", "${recs.count { it.status.name != "ABSENT" }} حاضر", "", M(recs.sumOf { it.amount })),
            "attendance_${date}"
        )
    }

    suspend fun weeklyPayroll(weekStart: LocalDate): Report {
        val rows = repo.weekRows(weekStart).filter { it.summary.gross != 0L || it.summary.advances != 0L || it.payroll != null }
        return Report(
            "كشف الرواتب الأسبوعي", "الأسبوع: ${PayrollWeek.label(weekStart)} (يشمل الأحد ${Formats.date(weekStart)})",
            listOf("العامل", "أيام كاملة", "أنصاف", "غياب", "الإجمالي", "التسبقات", "الباقي", "الحالة"),
            rows.map {
                listOf(it.worker.fullName, it.summary.fullDays, it.summary.halfDays, it.summary.absentDays,
                    M(it.summary.gross), M(it.summary.advances), M(it.summary.net),
                    it.payroll?.let { p -> "مدفوع ${Formats.dateTime(p.paidAt)}" } ?: "غير مدفوع")
            },
            listOf("المجموع", rows.sumOf { it.summary.fullDays }, rows.sumOf { it.summary.halfDays }, rows.sumOf { it.summary.absentDays },
                M(rows.sumOf { it.summary.gross }), M(rows.sumOf { it.summary.advances }), M(rows.sumOf { it.summary.net }), ""),
            "payroll_week_${weekStart}"
        )
    }

    /** Monthly = all payroll weeks whose Saturday pay day falls in that month. */
    suspend fun monthlyPayroll(year: Int, month: Int): Report {
        val first = LocalDate.of(year, month, 1)
        var ws = PayrollWeek.startOf(first)
        val weeks = mutableListOf<LocalDate>()
        while (true) {
            val pay = PayrollWeek.payDay(ws)
            if (pay.year == year && pay.monthValue == month) weeks += ws
            if (pay.isAfter(first.plusMonths(1))) break
            ws = PayrollWeek.next(ws)
        }
        data class Acc(var full: Int = 0, var half: Int = 0, var gross: Long = 0, var adv: Long = 0, var net: Long = 0, var paid: Long = 0)
        val acc = linkedMapOf<String, Acc>()
        for (w in weeks) for (r in repo.weekRows(w)) {
            if (r.summary.gross == 0L && r.summary.advances == 0L && r.payroll == null) continue
            val a = acc.getOrPut(r.worker.fullName) { Acc() }
            a.full += r.summary.fullDays; a.half += r.summary.halfDays
            a.gross += r.summary.gross; a.adv += r.summary.advances; a.net += r.summary.net
            a.paid += r.payroll?.paidAmount ?: 0
        }
        val rows = acc.entries.sortedBy { it.key }.map { (n, a) -> listOf<Any>(n, a.full, a.half, M(a.gross), M(a.adv), M(a.net), M(a.paid)) }
        val t = acc.values
        return Report(
            "كشف الرواتب الشهري", "${Formats.monthName(month)} $year — ${weeks.size} أسابيع (حسب يوم السبت)",
            listOf("العامل", "أيام كاملة", "أنصاف", "الإجمالي", "التسبقات", "الصافي", "المدفوع"), rows,
            listOf("المجموع", t.sumOf { it.full }, t.sumOf { it.half }, M(t.sumOf { it.gross }), M(t.sumOf { it.adv }), M(t.sumOf { it.net }), M(t.sumOf { it.paid })),
            "payroll_month_${year}_$month"
        )
    }

    /** Chronological ledger with running balance. */
    suspend fun workerStatement(workerId: Long): Report {
        val w = repo.workers.get(workerId)!!
        val projects = projectNames()
        data class Line(val sortKey: Long, val date: String, val desc: String, val credit: Long, val debit: Long)
        val lines = mutableListOf<Line>()
        for (a in repo.attendance.getByWorker(workerId)) {
            val d = LocalDate.ofEpochDay(a.date)
            val millis = d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            lines += Line(millis, Formats.date(d), "${Formats.dayName(d)} — ${a.status.label}" + (a.projectId?.let { " — ${projects[it]}" } ?: ""), a.amount, 0)
        }
        for (wk in repo.workerWeeks(workerId)) for (a in repo.advances.getByWorkerWeek(workerId, wk.toEpochDay())) {
            lines += Line(a.timestamp, Formats.dateTime(a.timestamp), "تسبقة" + (if (a.notes.isNotBlank()) " — ${a.notes}" else ""), 0, a.amount)
        }
        for (wk in repo.workerWeeks(workerId)) repo.payrolls.get(workerId, wk.toEpochDay())?.let { p ->
            lines += Line(p.paidAt, Formats.dateTime(p.paidAt), "دفع أسبوع ${PayrollWeek.label(wk)}", 0, p.paidAmount)
        }
        var bal = 0L
        val rows = lines.sortedBy { it.sortKey }.map { l ->
            bal += l.credit - l.debit
            listOf<Any>(l.date, l.desc, if (l.credit != 0L) M(l.credit) else "", if (l.debit != 0L) M(l.debit) else "", M(bal))
        }
        return Report(
            "كشف حساب عامل", "${w.fullName} — الأجر اليومي ${Money.format(w.dailyWage)}",
            listOf("التاريخ", "البيان", "له", "عليه / دُفع", "الرصيد"), rows,
            listOf("الرصيد المستحق", "", M(lines.sumOf { it.credit }), M(lines.sumOf { it.debit }), M(bal)),
            "statement_${w.id}"
        )
    }

    suspend fun projectLaborCost(): Report {
        val projects = repo.projects.getAll()
        val cost = repo.attendance.getLaborCost().associateBy { it.projectId }
        val received = repo.payments.getAllWithProject().filter { !it.payment.voided }.groupBy { it.payment.projectId }.mapValues { e -> e.value.sumOf { it.payment.amount } }
        val rows = projects.map {
            val c = cost[it.id]; val r = received[it.id] ?: 0
            listOf<Any>(it.name, it.status.label, c?.fullDays ?: 0, c?.halfDays ?: 0, M(c?.total ?: 0), M(r), M(r - (c?.total ?: 0)))
        }
        return Report(
            "تكلفة اليد العاملة حسب المشروع", "حتى ${Formats.date(LocalDate.now())}",
            listOf("المشروع", "الحالة", "أيام كاملة", "أنصاف", "تكلفة العمال", "المقبوض", "الفرق"), rows,
            listOf("المجموع", "", rows.sumOf { it[2] as Int }, rows.sumOf { it[3] as Int },
                M(cost.values.sumOf { it.total }), M(received.values.sum()), M(received.values.sum() - cost.values.sumOf { it.total })),
            "labor_cost"
        )
    }

    suspend fun projectPayments(): Report {
        val all = repo.payments.getAllWithProject()
        val rows = all.map {
            listOf<Any>(Formats.date(it.payment.date), it.projectName, M(it.payment.amount), it.payment.method, it.payment.notes,
                if (it.payment.voided) "ملغاة: ${it.payment.voidReason}" else "")
        }
        return Report(
            "سجل المقبوضات من المالكين", "كل المشاريع",
            listOf("التاريخ", "المشروع", "المبلغ", "طريقة الدفع", "ملاحظات", "الحالة"), rows,
            listOf("المجموع", "", M(all.filter { !it.payment.voided }.sumOf { it.payment.amount }), "", "", ""),
            "project_payments"
        )
    }

    suspend fun advances(from: LocalDate, to: LocalDate): Report {
        val names = workerNames()
        val zone = ZoneId.systemDefault()
        val list = repo.advances.getRange(from.atStartOfDay(zone).toInstant().toEpochMilli(), to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1)
        val rows = list.map { listOf<Any>(Formats.dateTime(it.timestamp), names[it.workerId] ?: "-", M(it.amount), PayrollWeek.label(LocalDate.ofEpochDay(it.weekStart)), it.notes) }
        return Report(
            "التسبقات", "${Formats.date(from)} - ${Formats.date(to)}",
            listOf("التاريخ والوقت", "العامل", "المبلغ", "أسبوع الخصم", "ملاحظات"), rows,
            listOf("المجموع", "", M(list.sumOf { it.amount }), "", ""),
            "advances_${from}_$to"
        )
    }

    suspend fun outstanding(): Report {
        val bal = repo.balances()
        val rows = repo.workers.getAll().mapNotNull { w ->
            val b = bal[w.id] ?: return@mapNotNull null
            listOf<Any>(w.fullName, w.status.label, M(b.earned), M(b.advances), M(b.paid), M(b.outstanding))
        }
        val v = bal.values
        return Report(
            "الأرصدة المستحقة للعمال", "حتى ${Formats.date(LocalDate.now())} (تشمل الأسبوع الجاري غير المدفوع)",
            listOf("العامل", "الحالة", "مجموع المكتسب", "التسبقات", "المدفوع", "المستحق"), rows,
            listOf("المجموع", "", M(v.sumOf { it.earned }), M(v.sumOf { it.advances }), M(v.sumOf { it.paid }), M(v.sumOf { it.outstanding })),
            "outstanding"
        )
    }
}
