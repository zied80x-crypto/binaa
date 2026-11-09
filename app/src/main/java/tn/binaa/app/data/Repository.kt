package tn.binaa.app.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import tn.binaa.app.domain.AttendanceStatus
import tn.binaa.app.domain.DayRecord
import tn.binaa.app.domain.Formats
import tn.binaa.app.domain.Money
import tn.binaa.app.domain.PayrollCalculator
import tn.binaa.app.domain.PayrollWeek
import tn.binaa.app.domain.WeekSummary
import java.time.LocalDate

/** Thrown for any business rule violation; message is shown to the user as-is (Arabic). */
class BusinessException(message: String) : Exception(message)

/** Thrown when an action touches a PAID week and needs explicit authorization. */
class PaidWeekLockedException(val weekStart: LocalDate) :
    Exception("هذا الأسبوع (${PayrollWeek.label(weekStart)}) مدفوع. أي تعديل يتطلب تصريحاً وسبباً.")

/** Explicit authorization for correcting a paid week. */
data class Authorization(val reason: String)

data class WorkerBalance(val earned: Long, val advances: Long, val paid: Long) {
    val outstanding: Long get() = PayrollCalculator.balance(earned, advances, paid)
}

data class WeekDetail(
    val worker: Worker,
    val weekStart: LocalDate,
    val attendance: List<Attendance>,
    val advances: List<Advance>,          // includes voided, for display
    val summary: WeekSummary,             // live calculation from records
    val payroll: PayrollRecord?,          // snapshot if paid
) {
    val isPaid: Boolean get() = payroll != null
    /** Difference introduced by authorized corrections after payment. */
    val correctionDelta: Long get() = payroll?.let { summary.net - it.net } ?: 0L
}

data class WorkerWeekRow(val worker: Worker, val summary: WeekSummary, val payroll: PayrollRecord?)

data class Forecast(val weekStart: LocalDate, val total: Long, val perWorker: Map<Long, Long>)

class Repository(private val db: AppDatabase, private val onFinancialChange: suspend () -> Unit = {}) {

    val workers = db.workers()
    val projects = db.projects()
    val payments = db.payments()
    val attendance = db.attendance()
    val advances = db.advances()
    val payrolls = db.payrolls()
    val audit = db.audit()

    /** Emits whenever any table that affects money or lists changes (Room invalidation). */
    val changes: Flow<Unit> = combine(
        listOf<Flow<Any>>(
            workers.observeAll(), projects.observeAll(), attendance.observeEarnedByWorker(),
            attendance.observeLaborCost(), advances.observeRecent(1), advances.observeSumsByWorker(),
            payrolls.observeRecent(1), payments.observeAllWithProject(),
        )
    ) { }

    private suspend fun log(type: String, id: Long, action: String, details: String) {
        audit.insert(AuditEntry(entityType = type, entityId = id, action = action, details = details))
    }

    // ------------------------------------------------------------------ Workers

    suspend fun saveWorker(w: Worker): Long = db.withTransaction {
        val name = w.fullName.trim()
        if (name.isEmpty()) throw BusinessException("الاسم الكامل مطلوب")
        if (w.dailyWage <= 0) throw BusinessException("الأجر اليومي يجب أن يكون أكبر من صفر")
        if (workers.countByName(name, w.id) > 0) throw BusinessException("يوجد عامل مسجّل بنفس الاسم")
        val clean = w.copy(fullName = name, phone = w.phone.trim(), notes = w.notes.trim(), updatedAt = System.currentTimeMillis())
        if (w.id == 0L) {
            val id = workers.insert(clean)
            log("worker", id, "CREATE", "إضافة عامل: $name — أجر ${Money.format(w.dailyWage)}")
            id
        } else {
            val old = workers.get(w.id) ?: throw BusinessException("العامل غير موجود")
            workers.update(clean)
            if (old.dailyWage != clean.dailyWage)
                log("worker", w.id, "WAGE_CHANGE", "$name: ${Money.format(old.dailyWage)} ← ${Money.format(clean.dailyWage)} (لا يغيّر الأيام المسجلة سابقاً)")
            if (old.status != clean.status) log("worker", w.id, "STATUS", "$name: ${old.status.label} ← ${clean.status.label}")
            w.id
        }
    }

    fun observeBalances(): Flow<Map<Long, WorkerBalance>> =
        combine(attendance.observeEarnedByWorker(), advances.observeSumsByWorker(), payrolls.observePaidByWorker()) { e, a, p ->
            val em = e.associate { it.id to it.total }
            val am = a.associate { it.id to it.total }
            val pm = p.associate { it.id to it.total }
            (em.keys + am.keys + pm.keys).associateWith { WorkerBalance(em[it] ?: 0, am[it] ?: 0, pm[it] ?: 0) }
        }

    suspend fun balances(): Map<Long, WorkerBalance> {
        val em = attendance.getEarnedByWorker().associate { it.id to it.total }
        val am = advances.getSumsByWorker().associate { it.id to it.total }
        val pm = payrolls.getPaidByWorker().associate { it.id to it.total }
        return (em.keys + am.keys + pm.keys).associateWith { WorkerBalance(em[it] ?: 0, am[it] ?: 0, pm[it] ?: 0) }
    }

    // ------------------------------------------------------------------ Projects

    suspend fun saveProject(p: Project): Long = db.withTransaction {
        val name = p.name.trim()
        if (name.isEmpty()) throw BusinessException("اسم المشروع أو المالك مطلوب")
        val clean = p.copy(name = name, ownerPhone = p.ownerPhone.trim(), address = p.address.trim(), notes = p.notes.trim(), updatedAt = System.currentTimeMillis())
        if (p.id == 0L) {
            val id = projects.insert(clean)
            log("project", id, "CREATE", "إضافة مشروع: $name")
            id
        } else {
            val old = projects.get(p.id) ?: throw BusinessException("المشروع غير موجود")
            projects.update(clean)
            if (old.status != clean.status) log("project", p.id, "STATUS", "$name: ${old.status.label} ← ${clean.status.label}")
            p.id
        }
    }

    suspend fun addProjectPayment(projectId: Long, amount: Long, date: LocalDate, method: String, notes: String): Long {
        if (amount <= 0) throw BusinessException("المبلغ يجب أن يكون أكبر من صفر")
        if (date.isAfter(LocalDate.now())) throw BusinessException("لا يمكن تسجيل دفعة بتاريخ مستقبلي")
        val id = db.withTransaction {
            val p = projects.get(projectId) ?: throw BusinessException("المشروع غير موجود")
            val id = payments.insert(ProjectPayment(projectId = projectId, amount = amount, date = date.toEpochDay(), method = method.trim(), notes = notes.trim()))
            log("project_payment", id, "CREATE", "${p.name}: استلام ${Money.format(amount)} بتاريخ ${Formats.date(date)}")
            id
        }
        onFinancialChange()
        return id
    }

    /** Payments are never deleted or overwritten: a wrong entry is voided with a reason. */
    suspend fun voidProjectPayment(id: Long, reason: String) {
        if (reason.isBlank()) throw BusinessException("يجب كتابة سبب الإلغاء")
        db.withTransaction {
            val p = payments.get(id) ?: throw BusinessException("الدفعة غير موجودة")
            if (p.voided) throw BusinessException("الدفعة ملغاة مسبقاً")
            payments.update(p.copy(voided = true, voidReason = reason.trim()))
            log("project_payment", id, "VOID", "إلغاء دفعة ${Money.format(p.amount)} — السبب: $reason")
        }
        onFinancialChange()
    }

    // ------------------------------------------------------------------ Attendance

    suspend fun isWeekPaid(workerId: Long, weekStart: LocalDate): Boolean =
        payrolls.get(workerId, weekStart.toEpochDay()) != null

    /**
     * Create or edit the single attendance row of (worker, date).
     * If the payroll week is already PAID, [auth] is mandatory and an audit
     * record + "corrected" flag are written; the paid snapshot itself is kept.
     */
    suspend fun setAttendance(workerId: Long, date: LocalDate, status: AttendanceStatus, projectId: Long?, auth: Authorization? = null) {
        if (date.isAfter(LocalDate.now())) throw BusinessException("لا يمكن تسجيل الحضور ليوم لم يأتِ بعد")
        db.withTransaction {
            val worker = workers.get(workerId) ?: throw BusinessException("العامل غير موجود")
            if (projectId != null && projects.get(projectId) == null) throw BusinessException("المشروع غير موجود")
            val weekStart = PayrollWeek.startOf(date)
            val paid = payrolls.get(workerId, weekStart.toEpochDay())
            if (paid != null && auth == null) throw PaidWeekLockedException(weekStart)
            val existing = attendance.getByWorkerDate(workerId, date.toEpochDay())
            val wage = existing?.wageSnapshot ?: worker.dailyWage
            val amount = Money.dayAmount(wage, status)
            val pid = if (status == AttendanceStatus.ABSENT) null else projectId
            if (existing == null) {
                val id = attendance.insert(
                    Attendance(workerId = workerId, date = date.toEpochDay(), weekStart = weekStart.toEpochDay(), status = status, projectId = pid, wageSnapshot = wage, amount = amount)
                )
                if (paid != null) log("attendance", id, "CORRECTION", "${worker.fullName} ${Formats.date(date)}: إضافة ${status.label} بعد الدفع — السبب: ${auth!!.reason}")
            } else {
                if (existing.status == status && existing.projectId == pid) return@withTransaction
                attendance.update(existing.copy(status = status, projectId = pid, amount = amount, updatedAt = System.currentTimeMillis()))
                val action = if (paid != null) "CORRECTION" else "EDIT"
                val reason = if (paid != null) " — السبب: ${auth!!.reason}" else ""
                log("attendance", existing.id, action,
                    "${worker.fullName} ${Formats.date(date)}: ${existing.status.label} (${Money.format(existing.amount)}) ← ${status.label} (${Money.format(amount)})" +
                        (if (existing.projectId != pid) " | تغيير المشروع" else "") + reason)
            }
            if (paid != null) payrolls.update(paid.copy(corrected = true))
        }
        onFinancialChange()
    }

    /** Removes an attendance entry (day not recorded). Same paid-week protection. */
    suspend fun clearAttendance(workerId: Long, date: LocalDate, auth: Authorization? = null) {
        db.withTransaction {
            val existing = attendance.getByWorkerDate(workerId, date.toEpochDay()) ?: return@withTransaction
            val weekStart = PayrollWeek.startOf(date)
            val paid = payrolls.get(workerId, weekStart.toEpochDay())
            if (paid != null && auth == null) throw PaidWeekLockedException(weekStart)
            val worker = workers.get(workerId)
            attendance.delete(existing.id)
            log("attendance", existing.id, if (paid != null) "CORRECTION" else "DELETE",
                "${worker?.fullName} ${Formats.date(date)}: حذف تسجيل ${existing.status.label} (${Money.format(existing.amount)})" +
                    (if (paid != null) " — السبب: ${auth!!.reason}" else ""))
            if (paid != null) payrolls.update(paid.copy(corrected = true))
        }
        onFinancialChange()
    }

    // ------------------------------------------------------------------ Advances

    /** First week, starting at the week of [date], that is not yet paid for this worker. */
    suspend fun suggestedAdvanceWeek(workerId: Long, date: LocalDate): LocalDate {
        var w = PayrollWeek.startOf(date)
        while (isWeekPaid(workerId, w)) w = PayrollWeek.next(w)
        return w
    }

    suspend fun addAdvance(workerId: Long, amount: Long, timestamp: Long, weekStart: LocalDate, notes: String): Long {
        if (amount <= 0) throw BusinessException("مبلغ التسبقة يجب أن يكون أكبر من صفر")
        val id = db.withTransaction {
            val worker = workers.get(workerId) ?: throw BusinessException("العامل غير موجود")
            if (isWeekPaid(workerId, weekStart))
                throw BusinessException("أسبوع ${PayrollWeek.label(weekStart)} مدفوع مسبقاً لهذا العامل. اختر الأسبوع التالي.")
            val id = advances.insert(Advance(workerId = workerId, amount = amount, timestamp = timestamp, weekStart = weekStart.toEpochDay(), notes = notes.trim()))
            log("advance", id, "CREATE", "${worker.fullName}: تسبقة ${Money.format(amount)} — أسبوع ${PayrollWeek.label(weekStart)}")
            id
        }
        onFinancialChange()
        return id
    }

    suspend fun voidAdvance(id: Long, reason: String, auth: Authorization? = null) {
        if (reason.isBlank()) throw BusinessException("يجب كتابة سبب الإلغاء")
        db.withTransaction {
            val a = advances.get(id) ?: throw BusinessException("التسبقة غير موجودة")
            if (a.voided) throw BusinessException("التسبقة ملغاة مسبقاً")
            val weekStart = LocalDate.ofEpochDay(a.weekStart)
            val paid = payrolls.get(a.workerId, a.weekStart)
            if (paid != null && auth == null) throw PaidWeekLockedException(weekStart)
            advances.update(a.copy(voided = true, voidReason = reason.trim()))
            log("advance", id, if (paid != null) "CORRECTION" else "VOID", "إلغاء تسبقة ${Money.format(a.amount)} — السبب: $reason")
            if (paid != null) payrolls.update(paid.copy(corrected = true))
        }
        onFinancialChange()
    }

    // ------------------------------------------------------------------ Payroll

    private fun toRecords(list: List<Attendance>) = list.map {
        DayRecord(LocalDate.ofEpochDay(it.date), it.status, it.amount, it.projectId)
    }

    suspend fun weekSummary(workerId: Long, weekStart: LocalDate): WeekSummary {
        val recs = attendance.getByWorkerWeek(workerId, weekStart.toEpochDay())
        val adv = advances.getByWorkerWeek(workerId, weekStart.toEpochDay())
        return PayrollCalculator.summarize(weekStart, toRecords(recs), adv.map { it.amount })
    }

    suspend fun weekDetail(workerId: Long, weekStart: LocalDate): WeekDetail {
        val worker = workers.get(workerId) ?: throw BusinessException("العامل غير موجود")
        val ws = weekStart.toEpochDay()
        return WeekDetail(
            worker = worker,
            weekStart = weekStart,
            attendance = attendance.getByWorkerWeek(workerId, ws),
            advances = advances.getByWorkerWeekIncludingVoided(workerId, ws),
            summary = weekSummary(workerId, weekStart),
            payroll = payrolls.get(workerId, ws),
        )
    }

    /** All weeks that have any activity for this worker, newest first. */
    suspend fun workerWeeks(workerId: Long): List<LocalDate> {
        val all = attendance.weeksForWorker(workerId) + advances.weeksForWorker(workerId) + payrolls.weeksForWorker(workerId) +
            PayrollWeek.startOf(LocalDate.now()).toEpochDay()
        return all.toSet().sortedDescending().map { LocalDate.ofEpochDay(it) }
    }

    /** Payroll table for one week: every worker that is active or has activity that week. */
    suspend fun weekRows(weekStart: LocalDate): List<WorkerWeekRow> {
        val ws = weekStart.toEpochDay()
        val att = attendance.getByWeek(ws).groupBy { it.workerId }
        val adv = advances.getByWeek(ws).groupBy { it.workerId }
        val paid = payrolls.getByWeek(ws).associateBy { it.workerId }
        return workers.getAll()
            .filter { it.status == WorkerStatus.ACTIVE || att.containsKey(it.id) || adv.containsKey(it.id) || paid.containsKey(it.id) }
            .map { w ->
                val s = PayrollCalculator.summarize(weekStart, toRecords(att[w.id].orEmpty()), adv[w.id].orEmpty().map { it.amount })
                WorkerWeekRow(w, s, paid[w.id])
            }
    }

    /**
     * Confirm Saturday payment for one worker/week. Writes an immutable snapshot.
     * Refuses duplicates, future weeks, and empty weeks.
     */
    suspend fun confirmPayment(workerId: Long, weekStart: LocalDate, paidAmount: Long, paidAt: Long, notes: String): PayrollRecord {
        if (paidAmount < 0) throw BusinessException("المبلغ المدفوع لا يمكن أن يكون سالباً")
        if (weekStart.isAfter(PayrollWeek.startOf(LocalDate.now()))) throw BusinessException("لا يمكن دفع أسبوع لم يبدأ بعد")
        val rec = db.withTransaction {
            val worker = workers.get(workerId) ?: throw BusinessException("العامل غير موجود")
            if (payrolls.get(workerId, weekStart.toEpochDay()) != null) throw BusinessException("تم تأكيد دفع هذا الأسبوع مسبقاً — لا يمكن الدفع مرتين")
            val s = weekSummary(workerId, weekStart)
            if (s.gross == 0L && s.advances == 0L) throw BusinessException("لا توجد أيام عمل أو تسبقات في هذا الأسبوع")
            val r = PayrollRecord(
                workerId = workerId, weekStart = weekStart.toEpochDay(),
                fullDays = s.fullDays, halfDays = s.halfDays, absentDays = s.absentDays,
                gross = s.gross, advances = s.advances, net = s.net,
                paidAmount = paidAmount, paidAt = paidAt, notes = notes.trim(),
            )
            val id = payrolls.insert(r)
            log("payroll", id, "PAID",
                "${worker.fullName} — أسبوع ${PayrollWeek.label(weekStart)}: إجمالي ${Money.format(s.gross)}، تسبقات ${Money.format(s.advances)}، صافي ${Money.format(s.net)}، مدفوع ${Money.format(paidAmount)}")
            r.copy(id = id)
        }
        onFinancialChange()
        return rec
    }

    /** Saturday forecast for [weekStart]: real records + assumed full days for unrecorded Mon..Sat. */
    suspend fun forecast(weekStart: LocalDate): Forecast {
        val ws = weekStart.toEpochDay()
        val att = attendance.getByWeek(ws).groupBy { it.workerId }
        val adv = advances.getByWeek(ws).groupBy { it.workerId }
        val paid = payrolls.getByWeek(ws).associateBy { it.workerId }
        val per = mutableMapOf<Long, Long>()
        for (w in workers.getAll()) {
            if (paid.containsKey(w.id)) continue
            val recs = toRecords(att[w.id].orEmpty())
            val advs = adv[w.id].orEmpty().map { it.amount }
            val value = if (w.status == WorkerStatus.ACTIVE) {
                PayrollCalculator.forecast(weekStart, w.dailyWage, LocalDate.ofEpochDay(w.startDate), recs, advs)
            } else {
                if (recs.isEmpty() && advs.isEmpty()) continue
                PayrollCalculator.summarize(weekStart, recs, advs).net
            }
            per[w.id] = value
        }
        return Forecast(weekStart, per.values.sum(), per)
    }
}
