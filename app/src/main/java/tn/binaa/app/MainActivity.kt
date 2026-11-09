package tn.binaa.app

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import tn.binaa.app.ui.nav.AppRoot
import tn.binaa.app.ui.theme.BinaaTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        // Force Arabic (Tunisia) resources & RTL whatever the phone language is.
        val config = Configuration(newBase.resources.configuration)
        val locale = Locale("ar", "TN")
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as BinaaApp
        lifecycleScope.launch { app.container.backup.autoBackupIfDue() }
        setContent {
            BinaaTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    AppRoot()
                }
            }
        }
    }
}
