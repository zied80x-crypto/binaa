package tn.binaa.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import tn.binaa.app.data.ProjectStatus
import tn.binaa.app.data.Repository
import tn.binaa.app.data.WorkerStatus
import tn.binaa.app.domain.AttendanceStatus
import tn.binaa.app.domain.Formats
import tn.binaa.app.domain.Money
import tn.binaa.app.domain.PayrollWeek
import tn.binaa.app.ui.components.Loading
import tn.binaa.app.ui.components.ScreenScaffold
import tn.binaa.app.ui.components.SectionTitle
import tn.binaa.app.ui.components.StatCard
import tn.binaa.app.ui.components.appViewModel
import tn.binaa.app.ui.nav.Nav
import tn.binaa.app.ui.theme.Absent
import tn.binaa.app.ui.theme.Amber
import tn.binaa.app.ui.theme.Half
import tn.binaa.app.ui.theme.Muted
import tn.binaa.app.ui.theme.Navy
import tn.binaa.app.ui.theme.NavyLight
import tn.binaa.app.ui.theme.Present
import java.time.LocalDate

data class Txn(val time: Long, val title: String, val amount: Long, val incoming: Boolean)

data class DashboardState(
    val activeProjects: Int,
    val workers: Int,
    val activeWorkers: Int,
    val present: Int,
    val absent: Int,
    val half: Int,
    val notRecorded: Int,
    val payDay: LocalDate,
    val forecast: Long,
    val outstanding: Long,
    val received: Long,
    val recent: List<Txn>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(private val repo: Repository) : ViewModel() {
    val state = repo.changes.mapLatest { load() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private suspend fun load(): DashboardState {
        val today = LocalDate.now()
        val week = PayrollWeek.startOf(today)
        val workers = repo.workers.getAll()
        val active = workers.filter { it.status == WorkerStatus.ACTIVE }
        val todays = repo.attendance.getByDate(today.toEpochDay())
        val activeIds = active.map { it.id }.toSet()
        val recordedIds = todays.map { it.workerId }.toSet()
        val payments = repo.payments.getAllWithProject().filter { !it.payment.voided }
        val names = workers.associate { it.id to it.fullName }
        val recent = buildList {
            payments.takeLast(10).forEach { add(Txn(it.payment.createdAt, "مقبوض: ${it.projectName}", it.payment.amount, true)) }
            repo.advances.getRange(0, Long.MAX_VALUE).takeLast(10).forEach { add(Txn(it.timestamp, "تسبقة: ${names[it.workerId]}", it.amount, false)) }
            repo.payrolls.getRange(0, Long.MAX_VALUE).sortedBy { it.paidAt }.takeLast(10).forEach { add(Txn(it.paidAt, "دفع أجرة: ${names[it.workerId]}", it.paidAmount, false)) }
        }.sortedByDescending { it.time }.take(10)
        return DashboardState(
            activeProjects = repo.projects.getAll().count { it.status == ProjectStatus.ACTIVE },
            workers = workers.size,
            activeWorkers = active.size,
            present = todays.count { it.status == AttendanceStatus.PRESENT },
            absent = todays.count { it.status == AttendanceStatus.ABSENT },
            half = todays.count { it.status == AttendanceStatus.HALF },
            notRecorded = (activeIds - recordedIds).size,
            payDay = PayrollWeek.payDay(week),
            forecast = repo.forecast(week).total,
            outstanding = repo.balances().values.sumOf { maxOf(0L, it.outstanding) },
            received = payments.sumOf { it.payment.amount },
            recent = recent,
        )
    }
}

@Composable
fun DashboardScreen(nav: Nav) {
    val vm = appViewModel { DashboardViewModel(it.repo) }
    val s by vm.state.collectAsStateWithLifecycle()
    ScreenScaffold(
        title = "مقاول بناء",
        actions = {
            IconButton(onClick = nav::reports) { Icon(Icons.Default.Assessment, "التقارير") }
            IconButton(onClick = nav::settings) { Icon(Icons.Default.Settings, "الإعدادات") }
        },
    ) { m ->
        val st = s ?: return@ScreenScaffold Loading()
        LazyColumn(m.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Spacer(Modifier.height(4.dp))
                Card(colors = CardDefaults.cardColors(containerColor = Navy), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("الرواتب المتوقعة ليوم السبت ${Formats.date(st.payDay)}", color = Color.White.copy(alpha = .85f))
                        Spacer(Modifier.height(6.dp))
                        Text(Money.format(st.forecast), style = MaterialTheme.typography.headlineMedium, color = Amber)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "تقدير ديناميكي: الأيام المسجلة بقيمتها الحقيقية، والأيام غير المسجلة (الاثنين–السبت) تُحتسب حضوراً كاملاً، ناقص التسبقات.",
                            color = Color.White.copy(alpha = .7f), style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("حاضرون اليوم", "${st.present}", Icons.Default.CheckCircle, Present, Modifier.weight(1f)) { nav.tab("attendance") }
                    StatCard("نصف يوم", "${st.half}", Icons.Default.Timelapse, Half, Modifier.weight(1f)) { nav.tab("attendance") }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("غائبون", "${st.absent}", Icons.Default.RemoveCircle, Absent, Modifier.weight(1f)) { nav.tab("attendance") }
                    StatCard("لم يُسجَّل بعد", "${st.notRecorded}", Icons.Default.Schedule, Muted, Modifier.weight(1f)) { nav.tab("attendance") }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("المشاريع النشطة", "${st.activeProjects}", Icons.Default.HomeWork, NavyLight, Modifier.weight(1f)) { nav.tab("projects") }
                    StatCard("العمال", "${st.activeWorkers} / ${st.workers}", Icons.Default.Groups, NavyLight, Modifier.weight(1f)) { nav.tab("workers") }
                }
            }
            item {
                StatCard("إجمالي المستحقات للعمال", Money.format(st.outstanding), Icons.Default.AccountBalanceWallet, Absent, Modifier.fillMaxWidth()) { nav.tab("payroll") }
            }
            item {
                StatCard("إجمالي المقبوض من المالكين", Money.format(st.received), Icons.Default.Payments, Present, Modifier.fillMaxWidth()) { nav.tab("projects") }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(onClick = { nav.tab("attendance") }, label = { Text("تسجيل الحضور") }, leadingIcon = { Icon(Icons.Default.EventAvailable, null) })
                    AssistChip(onClick = { nav.editWorker() }, label = { Text("عامل جديد") }, leadingIcon = { Icon(Icons.Default.Groups, null) })
                    AssistChip(onClick = { nav.editProject() }, label = { Text("مشروع جديد") }, leadingIcon = { Icon(Icons.Default.HomeWork, null) })
                }
            }
            item { SectionTitle("آخر العمليات المالية") }
            if (st.recent.isEmpty()) item { Text("لا توجد عمليات بعد", color = Muted) }
            items(st.recent) { t ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(t.title, fontWeight = FontWeight.SemiBold)
                        Text(Formats.dateTime(t.time), color = Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text((if (t.incoming) "+ " else "- ") + Money.format(t.amount), color = if (t.incoming) Present else Absent, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider()
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
