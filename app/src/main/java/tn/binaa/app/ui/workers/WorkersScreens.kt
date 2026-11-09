package tn.binaa.app.ui.workers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tn.binaa.app.data.PayrollRecord
import tn.binaa.app.data.Worker
import tn.binaa.app.data.WorkerStatus
import tn.binaa.app.domain.Formats
import tn.binaa.app.domain.Money
import tn.binaa.app.domain.PayrollWeek
import tn.binaa.app.domain.WeekSummary
import tn.binaa.app.reports.Exporters
import tn.binaa.app.reports.ReportBuilder
import tn.binaa.app.ui.components.AdvanceDialog
import tn.binaa.app.ui.components.AppTextField
import tn.binaa.app.ui.components.DateField
import tn.binaa.app.ui.components.EmptyState
import tn.binaa.app.ui.components.LabelValue
import tn.binaa.app.ui.components.LocalSnackbar
import tn.binaa.app.ui.components.Loading
import tn.binaa.app.ui.components.MoneyBig
import tn.binaa.app.ui.components.MoneyField
import tn.binaa.app.ui.components.ScreenScaffold
import tn.binaa.app.ui.components.SectionTitle
import tn.binaa.app.ui.components.act
import tn.binaa.app.ui.components.container
import tn.binaa.app.ui.components.produceLive
import tn.binaa.app.ui.nav.Nav
import tn.binaa.app.ui.theme.Absent
import tn.binaa.app.ui.theme.Amber
import tn.binaa.app.ui.theme.Half
import tn.binaa.app.ui.theme.Muted
import tn.binaa.app.ui.theme.Present
import java.time.LocalDate

// ============================================================ Workers list

@Composable
fun WorkersScreen(nav: Nav) {
    var query by remember { mutableStateOf("") }
    var showInactive by remember { mutableStateOf(false) }
    val data by produceLive { repo.workers.getAll() to repo.balances() }
    ScreenScaffold(
        "العمال",
        fab = { ExtendedFloatingActionButton(onClick = { nav.editWorker() }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("عامل جديد") }) },
    ) { m ->
        val (workers, balances) = data ?: return@ScreenScaffold Loading()
        val list = workers.filter { showInactive || it.status == WorkerStatus.ACTIVE }
            .filter { query.isBlank() || it.fullName.contains(query.trim(), ignoreCase = true) || it.phone.contains(query.trim()) }
        LazyColumn(m, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("بحث بالاسم أو الهاتف") }, singleLine = true)
                FilterChip(selected = showInactive, onClick = { showInactive = !showInactive }, label = { Text("إظهار غير النشطين") })
            }
            if (list.isEmpty()) item { EmptyState("لا يوجد عمال. اضغط «عامل جديد» للإضافة.") }
            items(list, key = { it.id }) { w ->
                val out = balances[w.id]?.outstanding ?: 0
                Card(onClick = { nav.worker(w.id) }, colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(w.fullName, style = MaterialTheme.typography.titleMedium)
                            Text("${Money.format(w.dailyWage)} / يوم" + (if (w.phone.isNotBlank()) " • ${w.phone}" else "") + (if (w.status != WorkerStatus.ACTIVE) " • ${w.status.label}" else ""), color = Muted, style = MaterialTheme.typography.bodyMedium)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("المستحق", color = Muted, style = MaterialTheme.typography.bodyMedium)
                            Text(Money.format(out), fontWeight = FontWeight.Bold, color = if (out > 0) Absent else if (out < 0) Half else Present)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

// ============================================================ Add / edit worker

@Composable
fun WorkerEditScreen(nav: Nav, id: Long) {
    val c = container()
    val snack = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(id == 0L) }
    var original by remember { mutableStateOf<Worker?>(null) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var wage by remember { mutableStateOf("") }
    var start by remember { mutableStateOf(LocalDate.now()) }
    var active by remember { mutableStateOf(true) }
    var notes by remember { mutableStateOf("") }
    LaunchedEffect(id) {
        if (id != 0L) c.repo.workers.get(id)?.let {
            original = it; name = it.fullName; phone = it.phone; wage = Money.toInput(it.dailyWage)
            start = LocalDate.ofEpochDay(it.startDate); active = it.status == WorkerStatus.ACTIVE; notes = it.notes
        }
        loaded = true
    }
    ScreenScaffold(if (id == 0L) "عامل جديد" else "تعديل العامل", onBack = nav::back) { m ->
        if (!loaded) return@ScreenScaffold Loading()
        Column(m.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AppTextField(name, { name = it }, "الاسم الكامل *")
            AppTextField(phone, { phone = it }, "رقم الهاتف", keyboard = KeyboardType.Phone)
            MoneyField(wage, { wage = it }, "الأجر اليومي *")
            if (original != null && Money.parse(wage) != null && Money.parse(wage) != original!!.dailyWage)
                Text("تغيير الأجر يُطبَّق على الأيام الجديدة فقط؛ الأيام المسجلة تحتفظ بأجرها الأصلي.", color = Half, style = MaterialTheme.typography.bodyMedium)
            DateField("تاريخ البداية", start, { start = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = active, onClick = { active = true }, label = { Text("نشط") })
                FilterChip(selected = !active, onClick = { active = false }, label = { Text("غير نشط") })
            }
            AppTextField(notes, { notes = it }, "ملاحظات", singleLine = false)
            Button(onClick = {
                val w = Money.parse(wage)
                if (w == null) { scope.launch { snack.showSnackbar("أدخل أجراً يومياً صحيحاً") }; return@Button }
                val base = original ?: Worker(fullName = "", dailyWage = 0, startDate = 0)
                scope.act(snack, "تم الحفظ") {
                    c.repo.saveWorker(base.copy(fullName = name, phone = phone, dailyWage = w, startDate = start.toEpochDay(), status = if (active) WorkerStatus.ACTIVE else WorkerStatus.INACTIVE, notes = notes))
                    nav.back()
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("حفظ") }
        }
    }
}

// ============================================================ Worker profile

data class ProfileData(
    val worker: Worker,
    val earned: Long, val advances: Long, val paid: Long, val outstanding: Long,
    val currentWeek: WeekSummary,
    val weeks: List<Triple<LocalDate, WeekSummary, PayrollRecord?>>,
)

@Composable
fun WorkerProfileScreen(nav: Nav, id: Long) {
    val ctx = LocalContext.current
    val c = container()
    val snack = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var advance by remember { mutableStateOf(false) }
    val data by produceLive(id) {
        val w = repo.workers.get(id) ?: return@produceLive null
        val b = repo.balances()[id]
        val weeks = repo.workerWeeks(id).map { ws -> Triple(ws, repo.weekSummary(id, ws), repo.payrolls.get(id, ws.toEpochDay())) }
        ProfileData(w, b?.earned ?: 0, b?.advances ?: 0, b?.paid ?: 0, b?.outstanding ?: 0,
            repo.weekSummary(id, PayrollWeek.startOf(LocalDate.now())), weeks)
    }
    ScreenScaffold(
        data?.worker?.fullName ?: "العامل", onBack = nav::back,
        actions = {
            IconButton(onClick = {
                scope.act(snack) {
                    val f = withContext(Dispatchers.IO) { Exporters.pdf(ctx, ReportBuilder(c.repo).workerStatement(id)) }
                    Exporters.share(ctx, f, "application/pdf")
                }
            }) { Icon(Icons.Default.PictureAsPdf, "كشف حساب PDF") }
            IconButton(onClick = { nav.editWorker(id) }) { Icon(Icons.Default.Edit, "تعديل") }
        },
    ) { m ->
        val d = data ?: return@ScreenScaffold Loading()
        LazyColumn(m, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                val color = if (d.outstanding > 0) Absent else if (d.outstanding < 0) Half else Present
                Card(colors = CardDefaults.cardColors(containerColor = color.copy(alpha = .1f)), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (d.outstanding >= 0) "الرصيد المستحق للعامل" else "على العامل (تسبقات زائدة)", color = color, fontWeight = FontWeight.Bold)
                        MoneyBig(kotlin.math.abs(d.outstanding), color)
                        Text("يشمل كل الأسابيع غير المدفوعة والفوارق السابقة", color = Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(14.dp)) {
                        LabelValue("الهاتف", d.worker.phone.ifBlank { "-" })
                        LabelValue("الأجر اليومي", Money.format(d.worker.dailyWage), bold = true)
                        LabelValue("تاريخ البداية", Formats.date(d.worker.startDate))
                        LabelValue("الحالة", d.worker.status.label)
                        if (d.worker.notes.isNotBlank()) LabelValue("ملاحظات", d.worker.notes)
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        LabelValue("مجموع المكتسب", Money.format(d.earned))
                        LabelValue("مجموع التسبقات", Money.format(d.advances))
                        LabelValue("مجموع المدفوع", Money.format(d.paid))
                    }
                }
            }
            item {
                val cw = d.currentWeek
                Card(onClick = { nav.week(id, cw.weekStart) }, colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("الأسبوع الحالي: ${PayrollWeek.label(cw.weekStart)}", fontWeight = FontWeight.Bold)
                        Text("كامل ${cw.fullDays} • نصف ${cw.halfDays} • غياب ${cw.absentDays}" + if (cw.sundayWorked) " • يشمل الأحد" else "", color = Muted)
                        LabelValue("الإجمالي حتى الآن", Money.format(cw.gross))
                        LabelValue("التسبقات", Money.format(cw.advances))
                        LabelValue("الباقي", Money.format(cw.net), bold = true)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { advance = true }, label = { Text("تسبقة جديدة") }, leadingIcon = { Icon(Icons.Default.Payments, null, tint = Amber) })
                }
            }
            item { SectionTitle("أرشيف الأسابيع") }
            items(d.weeks, key = { it.first.toEpochDay() }) { (ws, s, p) ->
                Card(onClick = { nav.week(id, ws) }, colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(PayrollWeek.label(ws), fontWeight = FontWeight.SemiBold)
                            Text("إجمالي ${Money.format(s.gross)} • تسبقات ${Money.format(s.advances)}", color = Muted, style = MaterialTheme.typography.bodyMedium)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Money.format(p?.net ?: s.net), fontWeight = FontWeight.Bold)
                            StatusTag(p)
                        }
                    }
                }
            }
        }
    }
    if (advance) data?.worker?.let { AdvanceDialog(it) { advance = false } }
}

@Composable
fun StatusTag(p: PayrollRecord?) {
    val (t, col) = when {
        p == null -> "غير مدفوع" to Absent
        p.corrected -> "مدفوع • مُصحَّح" to Half
        else -> "مدفوع ${Formats.date(java.time.Instant.ofEpochMilli(p.paidAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate())}" to Present
    }
    Text(t, color = col, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
}
