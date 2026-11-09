package tn.binaa.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tn.binaa.app.data.Worker
import tn.binaa.app.domain.Money
import tn.binaa.app.domain.PayrollWeek
import tn.binaa.app.domain.WeekSummary
import tn.binaa.app.ui.theme.Absent
import tn.binaa.app.ui.theme.Muted
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** "Confirm payment" for one worker / one week. Writes an immutable PAID snapshot. */
@Composable
fun PaymentDialog(worker: Worker, summary: WeekSummary, onDismiss: () -> Unit) {
    val c = container()
    val snack = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var amount by remember { mutableStateOf(Money.toInput(maxOf(0L, summary.net))) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var notes by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val parsed = Money.parse(amount)

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("تأكيد الدفع — ${worker.fullName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("الأسبوع: ${PayrollWeek.label(summary.weekStart)}", color = Muted)
                LabelValue("الإجمالي", Money.format(summary.gross))
                LabelValue("التسبقات", "- " + Money.format(summary.advances))
                LabelValue("الباقي المستحق", Money.format(summary.net), bold = true)
                MoneyField(amount, { amount = it }, "المبلغ المدفوع فعلياً")
                if (parsed != null && parsed != maxOf(0L, summary.net)) Text(
                    if (parsed < summary.net) "سيبقى ${Money.format(summary.net - parsed)} ديناً للعامل ويظهر في رصيده."
                    else "دفعت أكثر من المستحق بـ ${Money.format(parsed - summary.net)}؛ سيُسجَّل على العامل.",
                    color = Absent, style = MaterialTheme.typography.bodyMedium,
                )
                if (summary.net < 0) Text("التسبقات أكبر من المكتسب: يبقى ${Money.format(-summary.net)} على العامل للأسبوع القادم.", color = Absent)
                DateField("تاريخ الدفع", date, { date = it })
                AppTextField(notes, { notes = it }, "ملاحظات (اختياري)")
                Text("بعد التأكيد يُقفل الأسبوع. أي تعديل لاحق يحتاج تصريحاً ويُسجَّل.", color = Muted, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && parsed != null, onClick = {
                val m = parsed ?: return@TextButton
                busy = true
                val ts = LocalDateTime.of(date, if (date == LocalDate.now()) LocalTime.now() else LocalTime.NOON)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                scope.act(snack, "تم تأكيد الدفع ✓") {
                    try { c.repo.confirmPayment(worker.id, summary.weekStart, m, ts, notes); onDismiss() } finally { busy = false }
                }
            }) { Text("تأكيد الدفع") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("إلغاء") } },
    )
}
