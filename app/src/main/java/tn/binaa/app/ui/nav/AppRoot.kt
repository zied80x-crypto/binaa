package tn.binaa.app.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import tn.binaa.app.ui.attendance.AttendanceScreen
import tn.binaa.app.ui.components.LocalSnackbar
import tn.binaa.app.ui.dashboard.DashboardScreen
import tn.binaa.app.ui.payroll.PayrollScreen
import tn.binaa.app.ui.projects.ProjectDetailScreen
import tn.binaa.app.ui.projects.ProjectEditScreen
import tn.binaa.app.ui.projects.ProjectsScreen
import tn.binaa.app.ui.reports.ReportsScreen
import tn.binaa.app.ui.settings.AuditLogScreen
import tn.binaa.app.ui.settings.SettingsScreen
import tn.binaa.app.ui.workers.WeekDetailScreen
import tn.binaa.app.ui.workers.WorkerEditScreen
import tn.binaa.app.ui.workers.WorkerProfileScreen
import tn.binaa.app.ui.workers.WorkersScreen
import java.time.LocalDate

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("dashboard", "الرئيسية", Icons.Default.Dashboard),
    Tab("attendance", "الحضور", Icons.Default.EventAvailable),
    Tab("workers", "العمال", Icons.Default.Groups),
    Tab("projects", "المشاريع", Icons.Default.HomeWork),
    Tab("payroll", "الرواتب", Icons.Default.Payments),
)

/** Navigation helpers passed to screens. */
class Nav(private val c: NavHostController) {
    fun back() { c.popBackStack() }
    fun tab(route: String) = c.navigate(route) {
        popUpTo(c.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true; restoreState = true
    }
    fun worker(id: Long) = c.navigate("worker/$id")
    fun editWorker(id: Long = 0) = c.navigate("worker_edit/$id")
    fun week(workerId: Long, weekStart: LocalDate) = c.navigate("week/$workerId/${weekStart.toEpochDay()}")
    fun project(id: Long) = c.navigate("project/$id")
    fun editProject(id: Long = 0) = c.navigate("project_edit/$id")
    fun reports() = c.navigate("reports")
    fun settings() = c.navigate("settings")
    fun audit() = c.navigate("audit")
}

@Composable
fun AppRoot() {
    val controller = rememberNavController()
    val nav = remember(controller) { Nav(controller) }
    val snack = remember { SnackbarHostState() }
    val entry by controller.currentBackStackEntryAsState()
    val current = entry?.destination?.route

    CompositionLocalProvider(LocalSnackbar provides snack) {
        Scaffold(
            snackbarHost = { SnackbarHost(snack) },
            bottomBar = {
                if (tabs.any { it.route == current }) NavigationBar {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = current == t.route, onClick = { nav.tab(t.route) },
                            icon = { Icon(t.icon, null) }, label = { Text(t.label) },
                        )
                    }
                }
            },
        ) { pad ->
            NavHost(controller, startDestination = "dashboard", modifier = Modifier.padding(pad)) {
                composable("dashboard") { DashboardScreen(nav) }
                composable("attendance") { AttendanceScreen(nav) }
                composable("workers") { WorkersScreen(nav) }
                composable("projects") { ProjectsScreen(nav) }
                composable("payroll") { PayrollScreen(nav) }
                composable("reports") { ReportsScreen(nav) }
                composable("settings") { SettingsScreen(nav) }
                composable("audit") { AuditLogScreen(nav) }
                composable("worker/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                    WorkerProfileScreen(nav, it.arguments!!.getLong("id"))
                }
                composable("worker_edit/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                    WorkerEditScreen(nav, it.arguments!!.getLong("id"))
                }
                composable(
                    "week/{wid}/{ws}",
                    listOf(navArgument("wid") { type = NavType.LongType }, navArgument("ws") { type = NavType.LongType })
                ) {
                    WeekDetailScreen(nav, it.arguments!!.getLong("wid"), LocalDate.ofEpochDay(it.arguments!!.getLong("ws")))
                }
                composable("project/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                    ProjectDetailScreen(nav, it.arguments!!.getLong("id"))
                }
                composable("project_edit/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                    ProjectEditScreen(nav, it.arguments!!.getLong("id"))
                }
            }
        }
    }
}
