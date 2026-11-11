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
import androidx.compose.runtime.collectAsState
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
import androidx.navigation.NavType
import androidx.navigation.navArgument
import uk.woozy.fit.data.db.HqFitDatabase
import uk.woozy.fit.data.db.ScheduledWorkoutEntity
import uk.woozy.fit.data.db.Sessions
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import uk.woozy.fit.data.db.WaterEntryEntity
import uk.woozy.fit.model.Meal
import uk.woozy.fit.model.Nutrients
import uk.woozy.fit.data.db.Diary
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
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
    const val SCALE = "scale"
    const val HEALTH = "health"
    const val BACKUP = "backup"
    const val WORKOUT = "workout"
    const val SUMMARY = "summary"
    const val RECORDS = "records"
    const val ADD = "add"
    const val CUSTOM_FOOD = "customfood"
    const val SCAN = "scan"
    const val HISTORY = "history"
    const val PREVIEW = "preview"
    const val LIBRARY = "library"
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
fun HqFitRoot(
    db: HqFitDatabase,
    off: uk.woozy.fit.data.off.OffClient,
    container: uk.woozy.fit.AppContainer,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    scheme: String,
    onScheme: (String) -> Unit,
) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    var quickAdd by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now() }
    val calendarFlow = remember(db) {
        db.schedule().observeBetween(today.with(DayOfWeek.MONDAY).toString(), today.plusDays(70).toString())
    }
    val calendar by calendarFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val activeFlow = remember(db) { db.sessions().observeActive() }
    val active by activeFlow.collectAsState(initial = null)
    fun startOrResume(s: ScheduledWorkoutEntity) {
        scope.launch {
            val id = Sessions.start(db, s, System.currentTimeMillis())
            nav.navigate("${Routes.WORKOUT}/$id")
        }
    }
    val targets = rememberTargets(db)
    val todayText = today.toString()
    val dayFlow = remember(db) { db.diary().observeDay(todayText) }
    val dayEntries by dayFlow.collectAsState(initial = emptyList())
    val eatenToday = Diary.totals(dayEntries)[Nutrients.ENERGY] ?: 0.0
    val (dayFrom, dayTo) = dayBounds(today)
    val waterFlow = remember(db) { db.water().observeTotal(dayFrom, dayTo) }
    val waterToday by waterFlow.collectAsState(initial = 0)
    val todays = calendar.firstOrNull { it.date == today.toString() && it.status == "planned" }
    val next = calendar.firstOrNull { it.date > today.toString() && it.status == "planned" }

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
                    onStartWorkout = {
                        val a = active
                        if (a != null) nav.navigate("${Routes.WORKOUT}/${a.id}")
                        else todays?.let { nav.navigate("${Routes.PREVIEW}/${it.id}") }
                    },
                    workoutTitle = active?.name ?: todays?.templateName,
                    eaten = eatenToday,
                    waterMl = waterToday,
                    onAddWater = { scope.launch { db.water().insert(WaterEntryEntity(epochMs = System.currentTimeMillis(), ml = targets.waterGlassMl)) } },
                    targets = targets,
                    buttonLabel = if (active != null) "Resume workout" else "View workout",
                    workoutCaption = when {
                        active != null -> "In progress"
                        todays != null -> "Scheduled for today"
                        next != null -> "Next: ${next.templateName}, " +
                            LocalDate.parse(next.date).format(DateTimeFormatter.ofPattern("EEEE d MMM", Locale.UK))
                        else -> "Nothing scheduled"
                    },
                )
            }
            composable(Routes.TRAIN) {
                TrainScreen(
                    calendar = calendar,
                    onOpen = { nav.navigate("${Routes.PREVIEW}/$it") },
                    onLibrary = { nav.navigate(Routes.LIBRARY) },
                    onRecords = { nav.navigate(Routes.RECORDS) },
                )
            }
            composable(Routes.DIARY) {
                DiaryScreen(db = db, onAdd = { meal, date -> nav.navigate("${Routes.ADD}/${meal.key}/$date") })
            }
            composable(
                "${Routes.ADD}/{meal}/{date}?quick={quick}",
                arguments = listOf(
                    navArgument("meal") { type = NavType.StringType },
                    navArgument("date") { type = NavType.StringType },
                    navArgument("quick") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { backStack ->
                AddFoodScreen(
                    db = db,
                    meal = Meal.fromKey(backStack.arguments?.getString("meal").orEmpty()),
                    date = backStack.arguments?.getString("date") ?: todayText,
                    startQuick = backStack.arguments?.getBoolean("quick") ?: false,
                    off = off,
                    onBack = { nav.popBackStack() },
                    onCustom = { nav.navigate(Routes.CUSTOM_FOOD) },
                    onScan = {
                        val m = backStack.arguments?.getString("meal").orEmpty()
                        val d = backStack.arguments?.getString("date") ?: todayText
                        nav.navigate("${Routes.SCAN}/$m/$d")
                    },
                )
            }
            composable(
                "${Routes.SCAN}/{meal}/{date}",
                arguments = listOf(navArgument("meal") { type = NavType.StringType }, navArgument("date") { type = NavType.StringType }),
            ) { backStack ->
                ScanScreen(
                    db = db,
                    off = off,
                    meal = Meal.fromKey(backStack.arguments?.getString("meal").orEmpty()),
                    date = backStack.arguments?.getString("date") ?: todayText,
                    onBack = { nav.popBackStack() },
                    onCreate = { nav.navigate("${Routes.CUSTOM_FOOD}?barcode=$it") },
                    onLogged = { nav.popBackStack(Routes.DIARY, inclusive = false) },
                )
            }
            composable(
                "${Routes.CUSTOM_FOOD}?barcode={barcode}",
                arguments = listOf(navArgument("barcode") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { backStack ->
                CustomFoodScreen(db = db, barcode = backStack.arguments?.getString("barcode"), onBack = { nav.popBackStack() })
            }
            composable(Routes.PROGRESS) { ProgressScreen() }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    themeMode = themeMode,
                    onThemeMode = onThemeMode,
                    scheme = scheme,
                    onScheme = onScheme,
                    onBack = { nav.popBackStack() },
                    onDesign = { nav.navigate(Routes.DESIGN) },
                    onScale = { nav.navigate(Routes.SCALE) },
                    onHealth = { nav.navigate(Routes.HEALTH) },
                    onBackup = { nav.navigate(Routes.BACKUP) },
                )
            }
            composable(Routes.BACKUP) { BackupScreen(db = db, container = container, onBack = { nav.popBackStack() }) }
            composable(Routes.HEALTH) { HealthScreen(db = db, onBack = { nav.popBackStack() }) }
            composable(Routes.SCALE) { ScaleScreen(db = db, onBack = { nav.popBackStack() }) }
            composable(Routes.DESIGN) { DesignScreen(onBack = { nav.popBackStack() }) }
            composable(
                "${Routes.PREVIEW}/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { backStack ->
                WorkoutPreviewScreen(
                    db = db,
                    scheduledId = backStack.arguments?.getLong("id") ?: 0L,
                    onBack = { nav.popBackStack() },
                    onStart = { startOrResume(it) },
                )
            }
            composable(Routes.RECORDS) {
                RecordsScreen(db = db, onBack = { nav.popBackStack() }, onOpen = { nav.navigate("${Routes.HISTORY}/$it") })
            }
            composable(
                "${Routes.HISTORY}/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { backStack ->
                ExerciseHistoryScreen(db = db, exerciseId = backStack.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
            }
            composable(Routes.LIBRARY) { LibraryScreen(db = db, onBack = { nav.popBackStack() }) }
            composable(
                "${Routes.WORKOUT}/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { backStack ->
                val id = backStack.arguments?.getLong("id") ?: 0L
                ActiveWorkoutScreen(
                    db = db,
                    sessionId = id,
                    onLeave = { nav.popBackStack() },
                    onFinished = {
                        nav.navigate("${Routes.SUMMARY}/$id") { popUpTo(Routes.TODAY) }
                    },
                    onDiscarded = { nav.popBackStack(Routes.TODAY, inclusive = false) },
                )
            }
            composable(
                "${Routes.SUMMARY}/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { backStack ->
                WorkoutSummaryScreen(
                    db = db,
                    sessionId = backStack.arguments?.getLong("id") ?: 0L,
                    onDone = { nav.popBackStack(Routes.TODAY, inclusive = false) },
                )
            }
        }
    }

    if (quickAdd) {
        QuickAddSheet(
            onSearchFood = {
                quickAdd = false
                nav.navigate("${Routes.ADD}/${guessMeal(java.time.LocalTime.now().hour).key}/$todayText")
            },
            onQuickAdd = {
                quickAdd = false
                nav.navigate("${Routes.ADD}/${guessMeal(java.time.LocalTime.now().hour).key}/$todayText?quick=true")
            },
            onScanBarcode = {
                quickAdd = false
                nav.navigate("${Routes.SCAN}/${guessMeal(java.time.LocalTime.now().hour).key}/$todayText")
            },
            onWater = {
                quickAdd = false
                scope.launch { db.water().insert(WaterEntryEntity(epochMs = System.currentTimeMillis(), ml = targets.waterGlassMl)) }
            },
            onDismiss = { quickAdd = false },
            onStartWorkout = {
                quickAdd = false
                val a = active
                if (a != null) nav.navigate("${Routes.WORKOUT}/${a.id}")
                else if (todays != null) startOrResume(todays)
                else nav.navigateTab(Routes.TRAIN)
            },
        )
    }
}

/** The + menu. Rows light up as the milestone that powers them lands. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAddSheet(
    onSearchFood: () -> Unit,
    onQuickAdd: () -> Unit,
    onScanBarcode: () -> Unit,
    onWater: () -> Unit,
    onDismiss: () -> Unit,
    onStartWorkout: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = HqTheme.colors.card,
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { SectionLabel("Quick add") }
            ListRow(Icons.Rounded.Search, "Add food", "Search your foods or create one", onClick = onSearchFood)
            ListRow(Icons.Rounded.QrCodeScanner, "Scan barcode", "Look a product up", onClick = onScanBarcode)
            ListRow(Icons.Rounded.WaterDrop, "Add water", "One glass", onClick = onWater)
            ListRow(Icons.Rounded.EditNote, "Quick add", "A name and calories", onClick = onQuickAdd)
            ListRow(Icons.Rounded.PlayArrow, "Start workout", "Today's workout, or resume the one running", onClick = onStartWorkout)
        }
    }
}
