package tn.binaa.app.ui.workers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import tn.binaa.app.data.Advance
import tn.binaa.app.data.Authorization
import tn.binaa.app.data.Project
import tn.binaa.app.data.ProjectStatus
import tn.binaa.app.domain.AttendanceStatus
import tn.binaa.app.domain.Formats
import tn.binaa.app.domain.Money
import tn.binaa.app.domain.PayrollWeek
import tn.binaa.app.ui.components.AppTextField
import tn.binaa.app.ui.components.AuthorizationDialog
import tn.binaa.app.ui.components.LabelValue
import tn.binaa.app.ui.components.LocalSnackbar
import tn.binaa.app.ui.components.Loading
import tn.binaa.app.ui.components.PaymentDialog
import tn.binaa.app.ui.components.ScreenScaffold
import tn.binaa.app.ui.components.SectionTitle
import tn.binaa.app.ui.components.Selector
import tn.binaa.app.ui.components.act
import tn.binaa.app.ui.components.container
import tn.binaa.app.ui.components.produceLive
import tn.binaa.app.ui.nav.Nav
import tn.binaa.app.ui.theme.Absent
import tn.binaa.app.ui.theme.Half
import tn.binaa.app.ui.theme.Muted
import tn.binaa.app.ui.theme.Present
import java.time.DayOfWeek
import java.time.LocalDate

@Composable
fun WeekDetailScreen(nav: Nav, workerId: Long, initialWeek: LocalDate) {
    val c = container()
    val snack = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var week by remember { mutableStateOf(initialWeek) }
    var editDay by remember { mutableStateOf<LocalDate?>(null) }
    var voidAdv by remember { mutableStateOf<Advance?>(null) }
    var pay by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pair<String, (Authorization) -> Unit>?>(null) }
    val data by produceLive(week) { repo.weekDetail(workerId, week) to repo.projects.getAll() }

    fun locked(msg: String, retry: (Authorization) -> Unit) { pending = Pair(msg, retry) }

    ScreenScaffold("أسبوع الدفع", onBack = nav::back) { m ->
        val (d, projects) = data ?: return@ScreenScaffold Loading()
        val pmap = projects.associateBy { it.id }
        val byDate = d.attendance.associateBy { LocalDate.ofEpochDay(it.date) }
        LazyColumn(m, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { week = PayrollWeek.previous(week) }) { Icon(Icons.Default.ChevronRight, "الأسبوع السابق") }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(d.worker.fullName, style = MaterialTheme.typography.titleMedium)
                        Text(PayrollWeek.label(week), fontWeight = FontWeight.Bold)
                        Text("يوم الدفع: السبت ${Formats.date(PayrollWeek.payDay(week))}", color = Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                    val canNext = week.isBefore(PayrollWeek.startOf(LocalDate.now()))
                    IconButton(onClick = { if (canNext) week = PayrollWeek.next(week) }, enabled = canNext) { Icon(Icons.Default.ChevronLeft, "الأسبوع التالي") }
                }
            }
            item { SectionTitle("الحضور اليومي") }
            // Sunday first (it opens this payroll week), then Monday..Saturday
            items(PayrollWeek.allDays(week)) { day ->
                val r = byDate[day]
                val future = day.isAfter(LocalDate.now())
                Card(
                    onClick = { if (!future) editDay = day },
                    colors = CardDefaults.cardColors(containerColor = if (day.dayOfWeek == DayOfWeek.SUNDAY) Half.copy(alpha = .08f) else Color.White),
                ) {
                    Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${Formats.dayName(day)} ${Formats.date(day)}", fontWeight = FontWeight.SemiBold)
                            if (day.dayOfWeek == DayOfWeek.SUNDAY) Text("الأحد يُحتسب في هذا الأسبوع (الأسبوع التالي للسبت الماضي)", color = Half, style = MaterialTheme.typography.bodyMedium)
                            Text(r?.projectId?.let { pmap[it]?.name } ?: if (r == null) (if (future) "—" else "غير مسجل") else "", color = Muted, style = MaterialTheme.typography.bodyMedium)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(r?.status?.label ?: "", color = when (r?.status) { AttendanceStatus.PRESENT -> Present; AttendanceStatus.HALF -> Half; AttendanceStatus.ABSENT -> Absent; null -> Muted }, fontWeight = FontWeight.Bold)
                            if (r != null) Text(Money.format(r.amount))
                        }
                    }
                }
            }
            item { SectionTitle("التسبقات") }
            if (d.advances.isEmpty()) item { Text("لا توجد تسبقات في هذا الأسبوع", color = Muted) }
            items(d.advances, key = { it.id }) { a ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Formats.dateTime(a.timestamp))
                        if (a.notes.isNotBlank()) Text(a.notes, color = Muted, style = MaterialTheme.typography.bodyMedium)
                        if (a.voided) Text("ملغاة: ${a.voidReason}", color = Absent, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("- " + Money.format(a.amount), textDecoration = if (a.voided) TextDecoration.LineThrough else null, fontWeight = FontWeight.Bold)
                    if (!a.voided) IconButton(onClick = { voidAdv = a }) { Icon(Icons.Default.Block, "إلغاء التسبقة", tint = Muted) }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(14.dp)) {
                        val s = d.summary
                        LabelValue("أيام كاملة", "${s.fullDays}")
                        LabelValue("أنصاف أيام", "${s.halfDays}")
                        LabelValue("أيام غياب", "${s.absentDays}")
                        LabelValue("الإجمالي", Money.format(s.gross))
                        LabelValue("مجموع التسبقات", "- " + Money.format(s.advances))
                        LabelValue("الباقي المستحق", Money.format(s.net), bold = true, valueColor = if (s.net >= 0) Present else Absent)
                    }
                }
            }
            item {
                val p = d.payroll
                if (p != null) {
                    Card(colors = CardDefaults.cardColors(containerColor = Present.copy(alpha = .1f)), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lock, null, tint = Present)
                                Text("  مدفوع — الأسبوع مقفل", color = Present, fontWeight = FontWeight.Bold)
                            }
                            LabelValue("تاريخ الدفع", Formats.dateTime(p.paidAt))
                            LabelValue("المبلغ المدفوع", Money.format(p.paidAmount), bold = true)
                            LabelValue("الصافي عند الدفع", Money.format(p.net))
                            if (p.paidAmount != p.net) LabelValue("فرق مرحَّل للرصيد", Money.format(p.net - p.paidAmount), valueColor = Absent)
                            if (p.notes.isNotBlank()) LabelValue("ملاحظات", p.notes)
                            if (d.correctionDelta != 0L) Text("⚠ تصحيح بعد الدفع: الفرق ${Money.format(d.correctionDelta)} أُضيف إلى رصيد العامل.", color = Half, fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else if (!week.isAfter(PayrollWeek.startOf(LocalDate.now()))) {
                    Button(onClick = { pay = true }, modifier = Modifier.fillMaxWidth(), enabled = d.summary.gross != 0L || d.summary.advances != 0L) {
                        Text("تأكيد دفع هذا الأسبوع")
                    }
                }
            }
        }

        editDay?.let { day ->
            DayEditDialog(
                day = day, current = byDate[day]?.status, currentProject = byDate[day]?.projectId,
                projects = projects.filter { it.status == ProjectStatus.ACTIVE || it.id == byDate[day]?.projectId },
                onDismiss = { editDay = null },
                onSave = { status, project ->
                    scope.act(snack, "تم الحفظ", onLocked = { e -> locked(e.message ?: "") { a -> scope.act(snack, "تم التصحيح") { c.repo.setAttendance(workerId, day, status, project, a) } } }) {
                        c.repo.setAttendance(workerId, day, status, project)
                    }
                },
                onClear = {
                    scope.act(snack, "تم المسح", onLocked = { e -> locked(e.message ?: "") { a -> scope.act(snack, "تم التصحيح") { c.repo.clearAttendance(workerId, day, a) } } }) {
                        c.repo.clearAttendance(workerId, day)
                    }
                },
            )
        }
        if (pay) PaymentDialog(d.worker, d.summary) { pay = false }
    }

    voidAdv?.let { a ->
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { voidAdv = null },
            title = { Text("إلغاء تسبقة ${Money.format(a.amount)}") },
            text = { Column { Text("التسبقة لا تُحذف بل تُلغى مع ذكر السبب وتبقى في السجل."); AppTextField(reason, { reason = it }, "السبب") } },
            confirmButton = {
                TextButton(onClick = {
                    val r = reason
                    voidAdv = null
                    scope.act(snack, "تم الإلغاء", onLocked = { e -> locked(e.message ?: "") { au -> scope.act(snack, "تم التصحيح") { c.repo.voidAdvance(a.id, r, au) } } }) {
                        c.repo.voidAdvance(a.id, r)
                    }
                }) { Text("إلغاء التسبقة") }
            },
            dismissButton = { TextButton(onClick = { voidAdv = null }) { Text("رجوع") } },
        )
    }
    pending?.let { (msg, action) -> AuthorizationDialog(msg, { pending = null }) { a -> action(a) } }
}

@Composable
private fun DayEditDialog(
    day: LocalDate,
    current: AttendanceStatus?,
    currentProject: Long?,
    projects: List<Project>,
    onDismiss: () -> Unit,
    onSave: (AttendanceStatus, Long?) -> Unit,
    onClear: () -> Unit,
) {
    var status by remember { mutableStateOf(current ?: AttendanceStatus.PRESENT) }
    var project by remember { mutableStateOf(projects.firstOrNull { it.id == currentProject }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${Formats.dayName(day)} ${Formats.date(day)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AttendanceStatus.entries.forEach { s ->
                        if (s == status) Button(onClick = { status = s }, Modifier.weight(1f), contentPadding = PaddingValues(2.dp)) { Text(s.label) }
                        else OutlinedButton(onClick = { status = s }, Modifier.weight(1f), contentPadding = PaddingValues(2.dp)) { Text(s.label) }
                    }
                }
                if (status != AttendanceStatus.ABSENT) Selector("المشروع", projects, project, { it.name }, { project = it }, noneLabel = "— بدون مشروع —")
            }
        },
        confirmButton = { TextButton(onClick = { onSave(status, project?.id); onDismiss() }) { Text("حفظ") } },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { onClear(); onDismiss() }) { Text("مسح التسجيل", color = Absent) }
                TextButton(onClick = onDismiss) { Text("إلغاء") }
            }
        },
    )
}
