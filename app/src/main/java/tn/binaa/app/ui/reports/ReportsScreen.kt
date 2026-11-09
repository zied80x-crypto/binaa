package tn.binaa.app.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tn.binaa.app.data.Worker
import tn.binaa.app.domain.Formats
import tn.binaa.app.domain.PayrollWeek
import tn.binaa.app.reports.Exporters
import tn.binaa.app.reports.Report
import tn.binaa.app.reports.ReportBuilder
import tn.binaa.app.ui.components.DateField
import tn.binaa.app.ui.components.LocalSnackbar
import tn.binaa.app.ui.components.ScreenScaffold
import tn.binaa.app.ui.components.Selector
import tn.binaa.app.ui.components.act
import tn.binaa.app.ui.components.container
import tn.binaa.app.ui.components.produceLive
import tn.binaa.app.ui.nav.Nav
import tn.binaa.app.ui.theme.Muted
import java.time.LocalDate
import java.time.YearMonth

private enum class Kind(val label: String) {
    DAILY("الحضور اليومي"),
    WEEKLY("كشف الرواتب الأسبوعي"),
    MONTHLY("كشف الرواتب الشهري"),
    STATEMENT("كشف حساب عامل"),
    LABOR("تكلفة العمال حسب المشروع"),
    PAYMENTS("سجل مقبوضات المشاريع"),
    ADVANCES("التسبقات"),
    OUTSTANDING("الأرصدة المستحقة للعمال"),
}

@Composable
fun ReportsScreen(nav: Nav) {
    val ctx = LocalContext.current
    val c = container()
    val snack = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf(Kind.WEEKLY) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var from by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var worker by remember { mutableStateOf<Worker?>(null) }
    val workers by produceLive { repo.workers.getAll() }
    val months = remember { (0 until 24).map { YearMonth.now().minusMonths(it.toLong()) } }

    suspend fun build(): Report {
        val b = ReportBuilder(c.repo)
        return when (kind) {
            Kind.DAILY -> b.dailyAttendance(date)
            Kind.WEEKLY -> b.weeklyPayroll(PayrollWeek.startOf(date))
            Kind.MONTHLY -> b.monthlyPayroll(month.year, month.monthValue)
            Kind.STATEMENT -> b.workerStatement(worker?.id ?: throw tn.binaa.app.data.BusinessException("اختر العامل"))
            Kind.LABOR -> b.projectLaborCost()
            Kind.PAYMENTS -> b.projectPayments()
            Kind.ADVANCES -> b.advances(from, date)
            Kind.OUTSTANDING -> b.outstanding()
        }
    }

    ScreenScaffold("التقارير", onBack = nav::back) { m ->
        Column(m.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Selector("نوع التقرير", Kind.entries, kind, { it.label }, { if (it != null) kind = it })
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (kind) {
                        Kind.DAILY -> DateField("اليوم", date, { date = it })
                        Kind.WEEKLY -> {
                            DateField("أي يوم من الأسبوع", date, { date = it })
                            Text("الأسبوع: ${PayrollWeek.label(PayrollWeek.startOf(date))} + الأحد ${Formats.date(PayrollWeek.startOf(date))}", color = Muted)
                        }
                        Kind.MONTHLY -> {
                            Selector("الشهر", months, month, { "${Formats.monthName(it.monthValue)} ${it.year}" }, { if (it != null) month = it })
                            Text("يشمل كل الأسابيع التي يقع سبت دفعها في هذا الشهر.", color = Muted, style = MaterialTheme.typography.bodyMedium)
                        }
                        Kind.STATEMENT -> Selector("العامل", workers.orEmpty(), worker, { it.fullName }, { worker = it })
                        Kind.ADVANCES -> { DateField("من", from, { from = it }); DateField("إلى", date, { date = it }) }
                        else -> Text("تقرير شامل حتى اليوم.", color = Muted)
                    }
                }
            }
            Row {
                Button(onClick = {
                    scope.act(snack) { val f = withContext(Dispatchers.IO) { Exporters.pdf(ctx, build()) }; Exporters.share(ctx, f, "application/pdf") }
                }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.PictureAsPdf, null); Spacer(Modifier.width(6.dp)); Text("PDF") }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = {
                    scope.act(snack) {
                        val f = withContext(Dispatchers.IO) { Exporters.xlsx(ctx, build()) }
                        Exporters.share(ctx, f, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    }
                }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.TableChart, null); Spacer(Modifier.width(6.dp)); Text("Excel") }
            }
            Text("يُفتح التقرير في قائمة المشاركة: احفظه في الهاتف أو Drive أو أرسله عبر واتساب.", color = Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
        }
    }
}
