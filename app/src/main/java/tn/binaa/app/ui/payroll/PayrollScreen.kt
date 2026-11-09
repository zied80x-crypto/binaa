package tn.binaa.app.ui.payroll

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import tn.binaa.app.data.Repository
import tn.binaa.app.data.WorkerWeekRow
import tn.binaa.app.domain.Formats
import tn.binaa.app.domain.Money
import tn.binaa.app.domain.PayrollWeek
import tn.binaa.app.reports.Exporters
import tn.binaa.app.reports.ReportBuilder
import tn.binaa.app.ui.components.EmptyState
import tn.binaa.app.ui.components.LabelValue
import tn.binaa.app.ui.components.LocalSnackbar
import tn.binaa.app.ui.components.Loading
import tn.binaa.app.ui.components.PaymentDialog
import tn.binaa.app.ui.components.ScreenScaffold
import tn.binaa.app.ui.components.act
import tn.binaa.app.ui.components.appViewModel
import tn.binaa.app.ui.components.container
import tn.binaa.app.ui.nav.Nav
import tn.binaa.app.ui.theme.Absent
import tn.binaa.app.ui.theme.Muted
import tn.binaa.app.ui.theme.Navy
import tn.binaa.app.ui.workers.StatusTag
import java.time.LocalDate

data class PayrollState(val week: LocalDate, val rows: List<WorkerWeekRow>, val forecast: Long)

@OptIn(ExperimentalCoroutinesApi::class)
class PayrollViewModel(private val repo: Repository) : ViewModel() {
    val week = MutableStateFlow(PayrollWeek.startOf(LocalDate.now()))
    val state = combine(repo.changes, week) { _, w -> w }
        .mapLatest { w ->
            val rows = repo.weekRows(w).filter { it.summary.gross != 0L || it.summary.advances != 0L || it.payroll != null }
            PayrollState(w, rows, repo.forecast(w).total)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

@Composable
fun PayrollScreen(nav: Nav) {
    val vm = appViewModel { PayrollViewModel(it.repo) }
    val s by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val c = container()
    val snack = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var paying by remember { mutableStateOf<WorkerWeekRow?>(null) }

    ScreenScaffold(
        "رواتب السبت",
        actions = {
            IconButton(onClick = {
                val w = s?.week ?: return@IconButton
                scope.act(snack) { val f = withContext(Dispatchers.IO) { Exporters.pdf(ctx, ReportBuilder(c.repo).weeklyPayroll(w)) }; Exporters.share(ctx, f, "application/pdf") }
            }) { Icon(Icons.Default.PictureAsPdf, "PDF") }
            IconButton(onClick = {
                val w = s?.week ?: return@IconButton
                scope.act(snack) {
                    val f = withContext(Dispatchers.IO) { Exporters.xlsx(ctx, ReportBuilder(c.repo).weeklyPayroll(w)) }
                    Exporters.share(ctx, f, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                }
            }) { Icon(Icons.Default.TableChart, "Excel") }
        },
    ) { m ->
        val st = s ?: return@ScreenScaffold Loading()
        val currentWeek = PayrollWeek.startOf(LocalDate.now())
        LazyColumn(m, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { vm.week.value = PayrollWeek.previous(st.week) }) { Icon(Icons.Default.ChevronRight, "السابق") }
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("السبت ${Formats.date(PayrollWeek.payDay(st.week))}", style = MaterialTheme.typography.titleLarge, color = Navy)
                                Text("الأحد ${Formats.date(st.week)} + ${PayrollWeek.label(st.week)}", color = Muted, style = MaterialTheme.typography.bodyMedium)
                            }
                            val canNext = st.week.isBefore(currentWeek)
                            IconButton(onClick = { if (canNext) vm.week.value = PayrollWeek.next(st.week) }, enabled = canNext) { Icon(Icons.Default.ChevronLeft, "التالي") }
                        }
                        val unpaid = st.rows.filter { it.payroll == null }
                        LabelValue("مجموع الإجمالي", Money.format(st.rows.sumOf { it.summary.gross }))
                        LabelValue("مجموع التسبقات", Money.format(st.rows.sumOf { it.summary.advances }))
                        LabelValue("الباقي للدفع (غير المدفوعين)", Money.format(unpaid.sumOf { it.summary.net }), bold = true, valueColor = Absent)
                        if (st.week == currentWeek) LabelValue("التوقع حتى السبت", Money.format(st.forecast))
                        LabelValue("مدفوع / المجموع", "${st.rows.count { it.payroll != null }} / ${st.rows.size}")
                    }
                }
            }
            if (st.rows.isEmpty()) item { EmptyState("لا توجد أيام عمل أو تسبقات مسجلة في هذا الأسبوع") }
            items(st.rows, key = { it.worker.id }) { r ->
                Card(onClick = { nav.week(r.worker.id, st.week) }, colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.worker.fullName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            StatusTag(r.payroll)
                        }
                        Text(
                            "كامل ${r.summary.fullDays} • نصف ${r.summary.halfDays} • غياب ${r.summary.absentDays}" + if (r.summary.sundayWorked) " • يشمل الأحد" else "",
                            color = Muted, style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("إجمالي ${Money.format(r.summary.gross)}")
                            Text("تسبقات ${Money.format(r.summary.advances)}")
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("الباقي: ${Money.format(r.payroll?.net ?: r.summary.net)}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            if (r.payroll == null) FilledTonalButton(onClick = { paying = r }) { Text("تأكيد الدفع") }
                        }
                    }
                }
            }
        }
    }
    paying?.let { PaymentDialog(it.worker, it.summary) { paying = null } }
}
