package tn.binaa.app.ui.attendance

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import tn.binaa.app.data.Attendance
import tn.binaa.app.data.Authorization
import tn.binaa.app.data.Project
import tn.binaa.app.data.ProjectStatus
import tn.binaa.app.data.Repository
import tn.binaa.app.data.Worker
import tn.binaa.app.data.WorkerStatus
import tn.binaa.app.domain.AttendanceStatus
import tn.binaa.app.domain.Formats
import tn.binaa.app.domain.Money
import tn.binaa.app.domain.PayrollWeek
import tn.binaa.app.ui.components.AdvanceDialog
import tn.binaa.app.ui.components.AuthorizationDialog
import tn.binaa.app.ui.components.ConfirmDialog
import tn.binaa.app.ui.components.DatePickerPopup
import tn.binaa.app.ui.components.EmptyState
import tn.binaa.app.ui.components.LocalSnackbar
import tn.binaa.app.ui.components.Loading
import tn.binaa.app.ui.components.ScreenScaffold
import tn.binaa.app.ui.components.Selector
import tn.binaa.app.ui.components.act
import tn.binaa.app.ui.components.appViewModel
import tn.binaa.app.ui.nav.Nav
import tn.binaa.app.ui.theme.Absent
import tn.binaa.app.ui.theme.Amber
import tn.binaa.app.ui.theme.Half
import tn.binaa.app.ui.theme.Muted
import tn.binaa.app.ui.theme.Present
import java.time.DayOfWeek
import java.time.LocalDate

data class AttRow(val worker: Worker, val record: Attendance?, val lastProject: Long?)
data class AttState(val date: LocalDate, val rows: List<AttRow>, val projects: List<Project>, val allProjects: Map<Long, Project>)

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceViewModel(private val repo: Repository) : ViewModel() {
    val date = MutableStateFlow(LocalDate.now())
    val defaultProject = MutableStateFlow<Long?>(null)

    val state = combine(repo.changes, date) { _, d -> d }
        .mapLatest { d -> load(d) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private suspend fun load(d: LocalDate): AttState {
        val recs = repo.attendance.getByDate(d.toEpochDay()).associateBy { it.workerId }
        val workers = repo.workers.getAll().filter { it.status == WorkerStatus.ACTIVE || recs.containsKey(it.id) }
            .filter { recs.containsKey(it.id) || !LocalDate.ofEpochDay(it.startDate).isAfter(d) }
        val projects = repo.projects.getAll()
        val active = projects.filter { it.status == ProjectStatus.ACTIVE }
        if (defaultProject.value == null && active.size == 1) defaultProject.value = active.first().id
        return AttState(d, workers.map { AttRow(it, recs[it.id], repo.attendance.lastProjectOf(it.id)) }, active, projects.associateBy { it.id })
    }
}

@Composable
fun AttendanceScreen(nav: Nav) {
    val vm = appViewModel { AttendanceViewModel(it.repo) }
    val s by vm.state.collectAsStateWithLifecycle()
    val defaultProject by vm.defaultProject.collectAsStateWithLifecycle()
    val repo = tn.binaa.app.ui.components.container().repo
    val snack = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var pickDate by remember { mutableStateOf(false) }
    var advanceFor by remember { mutableStateOf<Worker?>(null) }
    var pending by remember { mutableStateOf<Pair<String, (Authorization) -> Unit>?>(null) }
    var confirmAll by remember { mutableStateOf(false) }

    fun mark(w: Worker, status: AttendanceStatus, project: Long?, date: LocalDate) {
        scope.act(snack, onLocked = { e ->
            val retry: (Authorization) -> Unit = { a -> scope.act(snack, "تم التصحيح") { repo.setAttendance(w.id, date, status, project, a) } }
            pending = Pair(e.message ?: "", retry)
        }) {
            repo.setAttendance(w.id, date, status, project)
        }
    }

    fun clear(w: Worker, date: LocalDate) {
        scope.act(snack, onLocked = { e ->
            val retry: (Authorization) -> Unit = { a -> scope.act(snack, "تم التصحيح") { repo.clearAttendance(w.id, date, a) } }
            pending = Pair(e.message ?: "", retry)
        }) {
            repo.clearAttendance(w.id, date)
        }
    }

    ScreenScaffold("الحضور اليومي") { m ->
        val st = s ?: return@ScreenScaffold Loading()
        val d = st.date
        val payDay = PayrollWeek.payDay(PayrollWeek.startOf(d))
        LazyColumn(m, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { vm.date.value = d.minusDays(1) }) { Icon(Icons.Default.ChevronRight, "اليوم السابق") }
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(Formats.dayName(d), style = MaterialTheme.typography.titleMedium)
                                Text(Formats.date(d), style = MaterialTheme.typography.titleLarge)
                            }
                            IconButton(onClick = { pickDate = true }) { Icon(Icons.Default.CalendarMonth, "اختيار التاريخ") }
                            IconButton(onClick = { if (d.isBefore(LocalDate.now())) vm.date.value = d.plusDays(1) }, enabled = d.isBefore(LocalDate.now())) {
                                Icon(Icons.Default.ChevronLeft, "اليوم التالي")
                            }
                        }
                        val sunday = d.dayOfWeek == DayOfWeek.SUNDAY
                        Text(
                            if (sunday) "⚠ الأحد: أجر هذا اليوم يُحتسب في أسبوع الدفع القادم (السبت ${Formats.date(payDay)})"
                            else "يُدفع أجر هذا اليوم يوم السبت ${Formats.date(payDay)}",
                            color = if (sunday) Half else Muted, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (sunday) FontWeight.Bold else FontWeight.Normal,
                        )
                        Spacer(Modifier.height(8.dp))
                        Selector(
                            "المشروع الافتراضي لهذا اليوم", st.projects, st.projects.firstOrNull { it.id == defaultProject },
                            { it.name }, { vm.defaultProject.value = it?.id }, noneLabel = "— بدون —",
                        )
                        Spacer(Modifier.height(6.dp))
                        val recs = st.rows.mapNotNull { it.record }
                        Text(
                            "حاضر ${recs.count { it.status == AttendanceStatus.PRESENT }} • نصف ${recs.count { it.status == AttendanceStatus.HALF }} • غائب ${recs.count { it.status == AttendanceStatus.ABSENT }} • غير مسجل ${st.rows.count { it.record == null }}",
                            color = Muted,
                        )
                        if (st.rows.any { it.record == null }) TextButton(onClick = { confirmAll = true }) {
                            Icon(Icons.Default.DoneAll, null); Spacer(Modifier.width(6.dp)); Text("تسجيل غير المسجلين حاضرين")
                        }
                    }
                }
            }
            if (st.rows.isEmpty()) item { EmptyState("لا يوجد عمال نشطون. أضف عمالاً من تبويب «العمال».") }
            items(st.rows, key = { it.worker.id }) { row ->
                WorkerAttendanceCard(
                    row = row, projects = st.projects, allProjects = st.allProjects,
                    onMark = { status, project ->
                        val p = project ?: row.record?.projectId ?: defaultProject ?: row.lastProject
                        mark(row.worker, status, p, d)
                    },
                    onProject = { pid -> row.record?.let { mark(row.worker, it.status, pid, d) } },
                    onClear = { clear(row.worker, d) },
                    onAdvance = { advanceFor = row.worker },
                )
            }
        }
        if (pickDate) DatePickerPopup(d, false, { pickDate = false }) { vm.date.value = it }
        if (confirmAll) ConfirmDialog(
            "تسجيل الحضور", "تسجيل كل العمال غير المسجلين حاضرين ليوم ${Formats.date(d)}" + (st.projects.firstOrNull { it.id == defaultProject }?.let { " في مشروع ${it.name}" } ?: "") + "؟",
            onDismiss = { confirmAll = false },
        ) {
            st.rows.filter { it.record == null }.forEach { r -> mark(r.worker, AttendanceStatus.PRESENT, defaultProject ?: r.lastProject, d) }
        }
    }
    advanceFor?.let { AdvanceDialog(it) { advanceFor = null } }
    pending?.let { (msg, action) -> AuthorizationDialog(msg, { pending = null }) { a -> action(a) } }
}

@Composable
private fun WorkerAttendanceCard(
    row: AttRow,
    projects: List<Project>,
    allProjects: Map<Long, Project>,
    onMark: (AttendanceStatus, Long?) -> Unit,
    onProject: (Long?) -> Unit,
    onClear: () -> Unit,
    onAdvance: () -> Unit,
) {
    val rec = row.record
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(row.worker.fullName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "الأجر ${Money.format(row.worker.dailyWage)}" + (rec?.let { " • اليوم: ${Money.format(it.amount)}" } ?: " • غير مسجل"),
                        color = Muted, style = MaterialTheme.typography.bodyMedium,
                    )
                }
                IconButton(onClick = onAdvance) { Icon(Icons.Default.Payments, "تسبقة", tint = Amber) }
                if (rec != null) IconButton(onClick = onClear) { Icon(Icons.Default.Clear, "مسح التسجيل", tint = Muted) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusButton("حاضر", Present, rec?.status == AttendanceStatus.PRESENT, Modifier.weight(1f)) { onMark(AttendanceStatus.PRESENT, null) }
                StatusButton("نصف يوم", Half, rec?.status == AttendanceStatus.HALF, Modifier.weight(1f)) { onMark(AttendanceStatus.HALF, null) }
                StatusButton("غائب", Absent, rec?.status == AttendanceStatus.ABSENT, Modifier.weight(1f)) { onMark(AttendanceStatus.ABSENT, null) }
            }
            if (rec != null && rec.status != AttendanceStatus.ABSENT) {
                Spacer(Modifier.height(6.dp))
                val current = rec.projectId?.let { allProjects[it] }
                val options = if (current != null && projects.none { it.id == current.id }) projects + current else projects
                Selector("المشروع", options, current, { it.name }, { p -> onProject(p?.id) }, noneLabel = "— بدون مشروع —")
            }
        }
    }
}

@Composable
private fun StatusButton(label: String, color: Color, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick, modifier = modifier, colors = ButtonDefaults.buttonColors(containerColor = color), contentPadding = PaddingValues(4.dp)) { Text(label) }
    else OutlinedButton(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(4.dp)) { Text(label, color = color) }
}
