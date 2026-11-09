package tn.binaa.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import tn.binaa.app.domain.Formats
import tn.binaa.app.reports.Exporters
import tn.binaa.app.ui.components.ConfirmDialog
import tn.binaa.app.ui.components.LabelValue
import tn.binaa.app.ui.components.LocalSnackbar
import tn.binaa.app.ui.components.Loading
import tn.binaa.app.ui.components.ScreenScaffold
import tn.binaa.app.ui.components.SectionTitle
import tn.binaa.app.ui.components.act
import tn.binaa.app.ui.components.container
import tn.binaa.app.ui.components.produceLive
import tn.binaa.app.ui.nav.Nav
import tn.binaa.app.ui.theme.Absent
import tn.binaa.app.ui.theme.Muted
import kotlinx.coroutines.flow.first
import java.io.File

@Composable
fun SettingsScreen(nav: Nav) {
    val ctx = LocalContext.current
    val c = container()
    val snack = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    val backups = remember(refresh) { c.backup.list() }
    var restoreFile by remember { mutableStateOf<File?>(null) }
    var restoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pinDialog by remember { mutableStateOf(false) }
    var hasPin by remember { mutableStateOf(c.auth.hasPin) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) scope.act(snack, "تم حفظ النسخة الاحتياطية") { c.backup.exportTo(uri); refresh++ }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) restoreUri = uri }

    ScreenScaffold("الإعدادات والنسخ الاحتياطي", onBack = nav::back) { m ->
        LazyColumn(m, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { SectionTitle("النسخ الاحتياطي") }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LabelValue("آخر نسخة تلقائية", if (c.backup.lastBackupAt == 0L) "لا توجد بعد" else Formats.dateTime(c.backup.lastBackupAt))
                        Text("نسخة تلقائية كل 6 ساعات على الأقل عند فتح التطبيق وبعد كل عملية مالية (آخر 20 نسخة). كما تُحفظ البيانات في نسخ Google الاحتياطي للهاتف إن كان مفعّلاً.", color = Muted, style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = {
                            scope.act(snack) { val f = c.backup.createBackup("manual"); refresh++; Exporters.share(ctx, f, "application/octet-stream") }
                        }, Modifier.fillMaxWidth()) { Text("نسخة الآن ومشاركتها (واتساب / Drive)") }
                        OutlinedButton(onClick = { exportLauncher.launch("binaa_backup_${Formats.fileStamp()}.db") }, Modifier.fillMaxWidth()) { Text("حفظ نسخة في ملف...") }
                        OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth()) { Text("استعادة من ملف...") }
                        Text("⚠ احتفظ بنسخة خارج الهاتف بانتظام: حذف التطبيق يحذف النسخ الداخلية.", color = Absent, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item { SectionTitle("النسخ المحفوظة في التطبيق") }
            items(backups, key = { it.name }) { f ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Formats.dateTime(f.lastModified()), fontWeight = FontWeight.SemiBold)
                        Text(when { f.name.contains("_auto_") -> "تلقائية"; f.name.contains("pre_restore") -> "قبل الاستعادة"; else -> "يدوية" } + " • ${f.length() / 1024} KB", color = Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                    IconButton(onClick = { restoreFile = f }) { Icon(Icons.Default.Restore, "استعادة") }
                }
                HorizontalDivider()
            }
            item { SectionTitle("الأمان") }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("رمز التصحيح: يُطلب عند تعديل أي أسبوع مدفوع (إضافة إلى السبب الإجباري).", color = Muted)
                        LabelValue("الحالة", if (hasPin) "مفعّل" else "غير مفعّل")
                        OutlinedButton(onClick = { pinDialog = true }, Modifier.fillMaxWidth()) { Text(if (hasPin) "تغيير / إزالة الرمز" else "تعيين رمز") }
                        OutlinedButton(onClick = nav::audit, Modifier.fillMaxWidth()) { Text("سجل المراجعة (كل التعديلات)") }
                    }
                }
            }
            item {
                Text("مقاول بناء — الإصدار 1.0.0 • كل البيانات محفوظة محلياً على الهاتف ولا تحتوي على أي بيانات تجريبية.", color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    restoreFile?.let { f ->
        ConfirmDialog("استعادة نسخة", "استبدال كل البيانات الحالية بنسخة ${Formats.dateTime(f.lastModified())}؟ ستُحفظ نسخة من البيانات الحالية أولاً، ثم يُعاد تشغيل التطبيق.", "استعادة", { restoreFile = null }) {
            scope.act(snack) { c.backup.restoreFromFile(f) }
        }
    }
    restoreUri?.let { uri ->
        ConfirmDialog("استعادة من ملف", "استبدال كل البيانات الحالية بمحتوى الملف المختار؟ ستُحفظ نسخة من البيانات الحالية أولاً، ثم يُعاد تشغيل التطبيق.", "استعادة", { restoreUri = null }) {
            scope.act(snack) { c.backup.restoreFromUri(uri) }
        }
    }
    if (pinDialog) {
        var oldPin by remember { mutableStateOf("") }
        var newPin by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { pinDialog = false },
            title = { Text("رمز التصحيح") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (hasPin) PinField(oldPin, { oldPin = it }, "الرمز الحالي")
                    PinField(newPin, { newPin = it }, if (hasPin) "الرمز الجديد (اتركه فارغاً للإزالة)" else "الرمز الجديد (4 أرقام أو أكثر)")
                    err?.let { Text(it, color = Absent) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (hasPin && !c.auth.verify(oldPin)) { err = "الرمز الحالي غير صحيح"; return@TextButton }
                    if (newPin.isEmpty() && hasPin) { c.auth.clearPin(); hasPin = false; pinDialog = false; return@TextButton }
                    if (newPin.length < 4 || !newPin.all { it.isDigit() }) { err = "4 أرقام على الأقل"; return@TextButton }
                    c.auth.setPin(newPin); hasPin = true; pinDialog = false
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { pinDialog = false }) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun PinField(v: String, on: (String) -> Unit, label: String) {
    OutlinedTextField(v, on, label = { Text(label) }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
}

@Composable
fun AuditLogScreen(nav: Nav) {
    val entries by produceLive { repo.audit.observeRecent().first() }
    ScreenScaffold("سجل المراجعة", onBack = nav::back) { m ->
        val list = entries ?: return@ScreenScaffold Loading()
        LazyColumn(m, contentPadding = PaddingValues(12.dp)) {
            items(list, key = { it.id }) { e ->
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text("${Formats.dateTime(e.timestamp)} • ${actionLabel(e.action)}", color = if (e.action == "CORRECTION") Absent else Muted, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(e.details)
                }
                HorizontalDivider()
            }
        }
    }
}

private fun actionLabel(a: String) = when (a) {
    "CREATE" -> "إضافة"; "EDIT" -> "تعديل"; "DELETE" -> "حذف تسجيل"; "VOID" -> "إلغاء"
    "PAID" -> "تأكيد دفع"; "CORRECTION" -> "تصحيح أسبوع مدفوع"; "WAGE_CHANGE" -> "تغيير الأجر"; "STATUS" -> "تغيير الحالة"
    else -> a
}
