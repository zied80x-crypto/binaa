package tn.binaa.app.ui.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tn.binaa.app.data.Project
import tn.binaa.app.data.ProjectPayment
import tn.binaa.app.data.ProjectStatus
import tn.binaa.app.domain.Formats
import tn.binaa.app.domain.Money
import tn.binaa.app.ui.components.AppTextField
import tn.binaa.app.ui.components.DateField
import tn.binaa.app.ui.components.EmptyState
import tn.binaa.app.ui.components.LabelValue
import tn.binaa.app.ui.components.LocalSnackbar
import tn.binaa.app.ui.components.Loading
import tn.binaa.app.ui.components.MoneyField
import tn.binaa.app.ui.components.ScreenScaffold
import tn.binaa.app.ui.components.SectionTitle
import tn.binaa.app.ui.components.act
import tn.binaa.app.ui.components.container
import tn.binaa.app.ui.components.produceLive
import tn.binaa.app.ui.nav.Nav
import tn.binaa.app.ui.theme.Absent
import tn.binaa.app.ui.theme.Half
import tn.binaa.app.ui.theme.Muted
import tn.binaa.app.ui.theme.Navy
import tn.binaa.app.ui.theme.Present
import java.time.LocalDate

private fun statusColor(s: ProjectStatus) = when (s) { ProjectStatus.ACTIVE -> Present; ProjectStatus.COMPLETED -> Navy; ProjectStatus.SUSPENDED -> Half }

@Composable
fun ProjectsScreen(nav: Nav) {
    val data by produceLive {
        Triple(repo.projects.getAll().sortedWith(compareBy({ it.status.ordinal }, { -it.startDate })),
            repo.payments.getAllWithProject().filter { !it.payment.voided }.groupBy { it.payment.projectId }.mapValues { e -> e.value.sumOf { it.payment.amount } },
            repo.attendance.getLaborCost().associateBy { it.projectId })
    }
    ScreenScaffold(
        "المشاريع",
        fab = { ExtendedFloatingActionButton(onClick = { nav.editProject() }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("مشروع جديد") }) },
    ) { m ->
        val (projects, received, labor) = data ?: return@ScreenScaffold Loading()
        LazyColumn(m, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(14.dp)) {
                        LabelValue("إجمالي المقبوض من كل المشاريع", Money.format(received.values.sum()), bold = true, valueColor = Present)
                        LabelValue("إجمالي تكلفة العمال", Money.format(labor.values.sumOf { it.total }))
                    }
                }
            }
            if (projects.isEmpty()) item { EmptyState("لا توجد مشاريع. اضغط «مشروع جديد».") }
            items(projects, key = { it.id }) { p ->
                Card(onClick = { nav.project(p.id) }, colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text(p.status.label, color = statusColor(p.status), fontWeight = FontWeight.Bold)
                        }
                        if (p.address.isNotBlank()) Text(p.address, color = Muted, style = MaterialTheme.typography.bodyMedium)
                        LabelValue("المقبوض", Money.format(received[p.id] ?: 0), valueColor = Present)
                        LabelValue("تكلفة العمال", Money.format(labor[p.id]?.total ?: 0))
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@Composable
fun ProjectEditScreen(nav: Nav, id: Long) {
    val c = container()
    val snack = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var original by remember { mutableStateOf<Project?>(null) }
    var loaded by remember { mutableStateOf(id == 0L) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var start by remember { mutableStateOf(LocalDate.now()) }
    var status by remember { mutableStateOf(ProjectStatus.ACTIVE) }
    var notes by remember { mutableStateOf("") }
    LaunchedEffect(id) {
        if (id != 0L) c.repo.projects.get(id)?.let {
            original = it; name = it.name; phone = it.ownerPhone; address = it.address
            start = LocalDate.ofEpochDay(it.startDate); status = it.status; notes = it.notes
        }
        loaded = true
    }
    ScreenScaffold(if (id == 0L) "مشروع جديد" else "تعديل المشروع", onBack = nav::back) { m ->
        if (!loaded) return@ScreenScaffold Loading()
        Column(m.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AppTextField(name, { name = it }, "اسم المشروع أو الاسم الكامل للمالك *")
            AppTextField(phone, { phone = it }, "هاتف المالك", keyboard = KeyboardType.Phone)
            AppTextField(address, { address = it }, "عنوان المشروع")
            DateField("تاريخ البداية", start, { start = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProjectStatus.entries.forEach { s -> FilterChip(selected = status == s, onClick = { status = s }, label = { Text(s.label) }) }
            }
            AppTextField(notes, { notes = it }, "ملاحظات", singleLine = false)
            Button(onClick = {
                val base = original ?: Project(name = "", startDate = 0)
                scope.act(snack, "تم الحفظ") {
                    c.repo.saveProject(base.copy(name = name, ownerPhone = phone, address = address, startDate = start.toEpochDay(), status = status, notes = notes))
                    nav.back()
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("حفظ") }
        }
    }
}

@Composable
fun ProjectDetailScreen(nav: Nav, id: Long) {
    val c = container()
    val snack = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var addPayment by remember { mutableStateOf(false) }
    var voidPay by remember { mutableStateOf<ProjectPayment?>(null) }
    val data by produceLive(id) {
        val p = repo.projects.get(id) ?: return@produceLive null
        val pays = repo.payments.getAllWithProject().map { it.payment }.filter { it.projectId == id }.sortedWith(compareByDescending<ProjectPayment> { it.date }.thenByDescending { it.id })
        val labor = repo.attendance.getLaborCost().firstOrNull { it.projectId == id }
        Triple(p, pays, labor)
    }
    ScreenScaffold(
        data?.first?.name ?: "المشروع", onBack = nav::back,
        actions = { IconButton(onClick = { nav.editProject(id) }) { Icon(Icons.Default.Edit, "تعديل") } },
        fab = { ExtendedFloatingActionButton(onClick = { addPayment = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("دفعة من المالك") }) },
    ) { m ->
        val (p, pays, labor) = data ?: return@ScreenScaffold Loading()
        val received = pays.filter { !it.voided }.sumOf { it.amount }
        val cost = labor?.total ?: 0
        LazyColumn(m, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(14.dp)) {
                        LabelValue("الحالة", p.status.label, valueColor = statusColor(p.status), bold = true)
                        LabelValue("هاتف المالك", p.ownerPhone.ifBlank { "-" })
                        LabelValue("العنوان", p.address.ifBlank { "-" })
                        LabelValue("تاريخ البداية", Formats.date(p.startDate))
                        if (p.notes.isNotBlank()) LabelValue("ملاحظات", p.notes)
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        LabelValue("مجموع المقبوض", Money.format(received), valueColor = Present, bold = true)
                        LabelValue("تكلفة العمال (${labor?.fullDays ?: 0} يوم + ${labor?.halfDays ?: 0} نصف)", Money.format(cost))
                        LabelValue("الفرق", Money.format(received - cost), valueColor = if (received >= cost) Present else Absent, bold = true)
                    }
                }
            }
            item { SectionTitle("سجل الدفعات المقبوضة") }
            if (pays.isEmpty()) item { Text("لا توجد دفعات بعد", color = Muted) }
            items(pays, key = { it.id }) { pay ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Formats.date(pay.date), fontWeight = FontWeight.SemiBold)
                        val info = listOf(pay.method, pay.notes).filter { it.isNotBlank() }.joinToString(" • ")
                        if (info.isNotBlank()) Text(info, color = Muted, style = MaterialTheme.typography.bodyMedium)
                        if (pay.voided) Text("ملغاة: ${pay.voidReason}", color = Absent, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(Money.format(pay.amount), color = Present, fontWeight = FontWeight.Bold, textDecoration = if (pay.voided) TextDecoration.LineThrough else null)
                    if (!pay.voided) IconButton(onClick = { voidPay = pay }) { Icon(Icons.Default.Block, "إلغاء", tint = Muted) }
                }
                HorizontalDivider()
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (addPayment) {
        var amount by remember { mutableStateOf("") }
        var date by remember { mutableStateOf(LocalDate.now()) }
        var method by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addPayment = false },
            title = { Text("استلام دفعة من المالك") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MoneyField(amount, { amount = it }, "المبلغ")
                    DateField("تاريخ الاستلام", date, { date = it })
                    AppTextField(method, { method = it }, "طريقة الدفع (نقداً، صك، تحويل...)")
                    AppTextField(notes, { notes = it }, "البيان / ملاحظات", singleLine = false)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val a = Money.parse(amount)
                    if (a == null || a <= 0) { scope.launch { snack.showSnackbar("أدخل مبلغاً صحيحاً") }; return@TextButton }
                    scope.act(snack, "تم تسجيل الدفعة") { c.repo.addProjectPayment(id, a, date, method, notes); addPayment = false }
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { addPayment = false }) { Text("إلغاء") } },
        )
    }
    voidPay?.let { pay ->
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { voidPay = null },
            title = { Text("إلغاء دفعة ${Money.format(pay.amount)}") },
            text = { Column { Text("لا تُحذف الدفعة؛ تُعلَّم كملغاة وتبقى في السجل."); AppTextField(reason, { reason = it }, "سبب الإلغاء") } },
            confirmButton = { TextButton(onClick = { val r = reason; voidPay = null; scope.act(snack, "تم الإلغاء") { c.repo.voidProjectPayment(pay.id, r) } }) { Text("إلغاء الدفعة") } },
            dismissButton = { TextButton(onClick = { voidPay = null }) { Text("رجوع") } },
        )
    }
}
