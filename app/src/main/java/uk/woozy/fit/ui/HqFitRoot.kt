package uk.woozy.fit.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import uk.woozy.fit.designsystem.BarItem
import uk.woozy.fit.designsystem.HqBottomBar
import uk.woozy.fit.designsystem.HqTheme
import uk.woozy.fit.designsystem.SectionLabel
import uk.woozy.fit.model.ThemeMode

private object Routes {
    const val TODAY = "today"
    const val TRAIN = "train"
    const val DIARY = "diary"
    const val PROGRESS = "progress"
    const val SETTINGS = "settings"
    const val DESIGN = "design"
    const val WORKOUT = "workout"
}

/** Today, Train, (+), Diary, Progress. The + sits between the second and third tab. */
private val tabRoutes = listOf(Routes.TODAY, Routes.TRAIN, Routes.DIARY, Routes.PROGRESS)

private val barItems = listOf(
    BarItem("Today", Icons.Rounded.Home),
    BarItem("Train", Icons.Rounded.FitnessCenter),
    BarItem("Diary", Icons.Rounded.Restaurant),
    BarItem("Progress", Icons.Rounded.BarChart),
)

private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun HqFitRoot(themeMode: ThemeMode, onThemeMode: (ThemeMode) -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    var quickAdd by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = HqTheme.colors.page,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (route in tabRoutes) {
                HqBottomBar(
                    items = barItems,
                    selected = tabRoutes.indexOf(route),
                    onSelect = { nav.navigateTab(tabRoutes[it]) },
                    onCentre = { quickAdd = true },
                )
            }
        },
    ) { padding ->
        NavHost(navController = nav, startDestination = Routes.TODAY, modifier = Modifier.padding(padding)) {
            composable(Routes.TODAY) {
                TodayScreen(
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                    onStartWorkout = { nav.navigate(Routes.WORKOUT) },
                )
            }
            composable(Routes.TRAIN) { TrainScreen() }
            composable(Routes.DIARY) { DiaryScreen() }
            composable(Routes.PROGRESS) { ProgressScreen() }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    themeMode = themeMode,
                    onThemeMode = onThemeMode,
                    onBack = { nav.popBackStack() },
                    onDesign = { nav.navigate(Routes.DESIGN) },
                )
            }
            composable(Routes.DESIGN) { DesignScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.WORKOUT) { WorkoutPlaceholderScreen(onClose = { nav.popBackStack() }) }
        }
    }

    if (quickAdd) {
        QuickAddSheet(
            onDismiss = { quickAdd = false },
            onStartWorkout = {
                quickAdd = false
                nav.navigate(Routes.WORKOUT)
            },
        )
    }
}

/** The + menu. Rows light up as the milestone that powers them lands. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAddSheet(onDismiss: () -> Unit, onStartWorkout: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = HqTheme.colors.card,
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { SectionLabel("Quick add") }
            ListRow(Icons.Rounded.Search, "Search food", "Arrives in milestone 5", enabled = false)
            ListRow(Icons.Rounded.QrCodeScanner, "Scan barcode", "Arrives in milestone 6", enabled = false)
            ListRow(Icons.Rounded.WaterDrop, "Add water", "Arrives in milestone 5", enabled = false)
            ListRow(Icons.Rounded.EditNote, "Quick add or note", "Arrives in milestone 5", enabled = false)
            ListRow(Icons.Rounded.PlayArrow, "Start workout", "Opens the workout screen (logging arrives in milestone 4)", onClick = onStartWorkout)
        }
    }
}
