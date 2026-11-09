package tn.binaa.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import tn.binaa.app.AppContainer
import tn.binaa.app.BinaaApp
import tn.binaa.app.data.Authorization
import tn.binaa.app.data.BusinessException
import tn.binaa.app.data.PaidWeekLockedException
import tn.binaa.app.domain.Formats
import tn.binaa.app.domain.Money
import tn.binaa.app.ui.theme.Muted
import tn.binaa.app.ui.theme.Navy
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

val LocalSnackbar = staticCompositionLocalOf<SnackbarHostState> { error("no snackbar") }

@Composable
fun container(): AppContainer = (LocalContext.current.applicationContext as BinaaApp).container

@Suppress("UNCHECKED_CAST")
@Composable
inline fun <reified T : ViewModel> appViewModel(key: String? = null, crossinline factory: (AppContainer) -> T): T {
    val c = container()
    return viewModel(key = key, factory = object : ViewModelProvider.Factory {
        override fun <V : ViewModel> create(modelClass: Class<V>): V = factory(c) as V
    })
}

/** Re-runs [load] every time any financial table changes. */
@Composable
fun <T> produceLive(vararg keys: Any?, load: suspend AppContainer.() -> T): State<T?> {
    val c = container()
    return produceState<T?>(initialValue = null, *keys) {
        c.repo.changes.collectLatest {
            value = try { c.load() } catch (e: Exception) { value }
        }
    }
}

/**
 * Runs a repository action, shows business errors in the snackbar and routes
 * paid-week locks to [onLocked] so the UI can ask for authorization.
 */
fun CoroutineScope.act(
    snack: SnackbarHostState,
    success: String? = null,
    onLocked: ((PaidWeekLockedException) -> Unit)? = null,
    block: suspend () -> Unit,
) = launch {
    try {
        block()
        if (success != null) snack.showSnackbar(success)
    } catch (e: PaidWeekLockedException) {
        if (onLocked != null) onLocked(e) else snack.showSnackbar(e.message ?: "")
    } catch (e: BusinessException) {
        snack.showSnackbar(e.message ?: "خطأ")
    } catch (e: Exception) {
        snack.showSnackbar("خطأ: ${e.message}")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    fab: @Composable () -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
                },
                actions = { actions() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy, titleContentColor = Color.White, navigationIconContentColor = Color.White, actionIconContentColor = Color.White),
            )
        },
        floatingActionButton = fab,
        containerColor = MaterialTheme.colorScheme.background,
    ) { p -> content(Modifier.padding(p)) }
}

@Composable
fun StatCard(title: String, value: String, icon: ImageVector, tint: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val colors = CardDefaults.cardColors(containerColor = Color.White)
    val inner: @Composable () -> Unit = {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, color = tint)
        }
    }
    if (onClick != null) Card(onClick = onClick, modifier = modifier, colors = colors, elevation = CardDefaults.cardElevation(1.dp)) { inner() }
    else Card(modifier = modifier, colors = colors, elevation = CardDefaults.cardElevation(1.dp)) { inner() }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = Navy, modifier = modifier.padding(vertical = 8.dp))
}

@Composable
fun LabelValue(label: String, value: String, valueColor: Color = Color.Unspecified, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Muted)
        Text(value, color = valueColor, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Muted, textAlign = TextAlign.Center)
    }
}

@Composable
fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("...", color = Muted) }
}

@Composable
fun AppTextField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, keyboard: KeyboardType = KeyboardType.Text, singleLine: Boolean = true, error: String? = null) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        modifier = modifier.fillMaxWidth(), singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
    )
}

@Composable
fun MoneyField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    val err = if (value.isNotBlank() && Money.parse(value) == null) "مبلغ غير صالح (حتى 3 أرقام بعد الفاصل)" else null
    AppTextField(value, onChange, "$label (د.ت)", modifier, KeyboardType.Decimal, error = err)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, date: LocalDate, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier, allowFuture: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = Formats.date(date), onValueChange = {}, readOnly = true, label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        trailingIcon = { IconButton(onClick = { open = true }) { Icon(Icons.Default.CalendarMonth, "اختيار التاريخ") } },
    )
    if (open) DatePickerPopup(date, allowFuture, { open = false }, onChange)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerPopup(date: LocalDate, allowFuture: Boolean, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        selectableDates = object : androidx.compose.material3.SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                allowFuture || !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isAfter(today)
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("موافق") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    ) { DatePicker(state = state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Selector(
    label: String,
    options: List<T>,
    selected: T?,
    text: (T) -> String,
    onSelect: (T?) -> Unit,
    modifier: Modifier = Modifier,
    noneLabel: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected?.let(text) ?: (noneLabel ?: ""), onValueChange = {}, readOnly = true,
            label = { Text(label) }, singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (noneLabel != null) DropdownMenuItem(text = { Text(noneLabel) }, onClick = { onSelect(null); expanded = false })
            options.forEach { o -> DropdownMenuItem(text = { Text(text(o)) }, onClick = { onSelect(o); expanded = false }) }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String = "تأكيد", onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

/**
 * Explicit authorization for modifying a PAID week: a written reason is always
 * required, plus the correction PIN when one is set in Settings.
 */
@Composable
fun AuthorizationDialog(message: String, onDismiss: () -> Unit, onAuthorized: (Authorization) -> Unit) {
    val auth = container().auth
    var reason by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تصحيح أسبوع مدفوع") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(message)
                Text("سيُسجَّل هذا التعديل في سجل المراجعة، ويبقى مبلغ الدفع الأصلي محفوظاً. الفرق يظهر في رصيد العامل.", color = Muted, style = MaterialTheme.typography.bodyMedium)
                AppTextField(reason, { reason = it }, "سبب التصحيح (إجباري)", singleLine = false)
                if (auth.hasPin) OutlinedTextField(
                    value = pin, onValueChange = { pin = it }, label = { Text("رمز التصحيح") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    reason.isBlank() -> error = "اكتب سبب التصحيح"
                    auth.hasPin && !auth.verify(pin) -> error = "رمز غير صحيح"
                    else -> { onAuthorized(Authorization(reason.trim())); onDismiss() }
                }
            }) { Text("تصريح بالتعديل") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
fun MoneyBig(millimes: Long, color: Color) {
    Text(Money.format(millimes), style = MaterialTheme.typography.headlineMedium, color = color)
}
