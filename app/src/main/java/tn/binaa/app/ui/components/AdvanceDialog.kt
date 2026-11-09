package tn.binaa.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import tn.binaa.app.data.Worker
import tn.binaa.app.domain.Money
import tn.binaa.app.domain.PayrollWeek
import tn.binaa.app.ui.theme.Muted
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Records a salary advance. The advance is linked to exactly ONE payroll week
 * (default: the first unpaid week containing the advance date) and will be
 * deducted from that week's gross — never added as an expense.
 */
@Composable
fun AdvanceDialog(worker: Worker, onDismiss: () -> Unit) {
    val c = container()
    val snack = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var amount by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var week by remember { mutableStateOf<LocalDate?>(null) }
    var weekOptions by remember { mutableStateOf(emptyList<LocalDate>()) }

    LaunchedEffect(date) {
        val first = c.repo.suggestedAdvanceWeek(worker.id, date)
        weekOptions = listOf(first, PayrollWeek.next(first))
        week = first
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تسبقة — ${worker.fullName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyField(amount, { amount = it }, "المبلغ")
                DateField("تاريخ التسبقة", date, { date = it })
                Selector("أسبوع الخصم", weekOptions, week, { "يُخصم يوم السبت ${tn.binaa.app.domain.Formats.date(PayrollWeek.payDay(it))}" }, { week = it })
                AppTextField(notes, { notes = it }, "ملاحظات (اختياري)", singleLine = false)
                Text("التسبقة مال سُلِّم مسبقاً، تُطرح من أجرة الأسبوع المختار ولا تُضاف كمصروف.", color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val m = Money.parse(amount)
                val w = week
                if (m == null || m <= 0 || w == null) return@TextButton
                val now = LocalTime.now()
                val ts = LocalDateTime.of(date, now).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                scope.act(snack, "تم تسجيل تسبقة ${Money.format(m)}") {
                    c.repo.addAdvance(worker.id, m, ts, w, notes)
                    onDismiss()
                }
            }) { Text("حفظ التسبقة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
