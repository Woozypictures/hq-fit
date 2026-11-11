package uk.woozy.fit.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.produceState
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
    const val EDIT = "edit"
    const val RECIPE = "recipe"
    const val NUTRIENTS = "nutrients"
    const val TARGETS = "targets"
    const val PAST = "past"
    const val NEW_EXERCISE = "newexercise"
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
    frosted: Boolean,
    onFrosted: (Boolean) -> Unit,
    scheme: String,
    onScheme: (String) -> Unit,
) {
    val nav = rememberNavController()
    val syncContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    androidx.compose.runtime.LaunchedEffect(db) {
        // Send anything new to Health Connect each time the app opens. Failures are shown in Settings, never as crashes.
        runCatching { uk.woozy.fit.health.HealthSync.run(syncContext, db, uk.woozy.fit.health.HealthGateway(syncContext)) }
        uk.woozy.fit.CheckInReminder.schedule(syncContext)
    }
    var refreshTick by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    val health by androidx.compose.runtime.produceState<uk.woozy.fit.health.HealthSnapshot?>(null, db, refreshTick) {
        value = runCatching {
            val g = uk.woozy.fit.health.HealthGateway(syncContext)
            if (!g.available) null else g.snapshot(
                LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant(), java.time.Instant.now(),
            )
        }.getOrNull()
    }
    // One line on Today when Health Connect needs attention; nothing at all when it is fine (as in HQ).
    val problem by androidx.compose.runtime.produceState<String?>(null, db, refreshTick) {
        value = runCatching {
            val g = uk.woozy.fit.health.HealthGateway(syncContext)
            val status = uk.woozy.fit.health.HealthSync.lastStatus(syncContext)
            when {
                !g.available -> null
                g.granted().intersect(uk.woozy.fit.health.HealthGateway.allPermissions).isEmpty() -> "Health Connect is not set up, so steps, calories burned and syncing are off"
                status.startsWith("Sync failed") || status.startsWith("Grant") -> "Health Connect: $status"
                else -> null
            }
        }.getOrNull()
    }
    val today = rememberToday()
    val sundayDate = remember(today) { today.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)).toString() }
    val ticksFlow = remember(db, sundayDate) { db.checkIns().observe(sundayDate) }
    val ticks by ticksFlow.collectAsState(initial = emptyList())
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    var quickAdd by remember { mutableStateOf(false) }
    val calendarFlow = remember(db, today) {
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
    // Reload targets whenever the screen changes, so an edit in Targets shows straight away.
    val targets = rememberTargets(db, route)
    val todayText = today.toString()
    val dayFlow = remember(db, todayText) { db.diary().observeDay(todayText) }
    val dayEntries by dayFlow.collectAsState(initial = emptyList())
    val dayTotals = Diary.totals(dayEntries)
    val eatenToday = dayTotals[Nutrients.ENERGY] ?: 0.0
    val nutrientTargets by produceState(emptyMap<String, Pair<Double, String>>(), route) {
        value = db.targets().all().associate { it.key to (it.value to it.kind) }
    }
    val (dayFrom, dayTo) = dayBounds(today)
    val waterFlow = remember(db, dayFrom) { db.water().observeTotal(dayFrom, dayTo) }
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
        // Tab pages may go wide (two columns of cards); every other page is capped so it reads well on a tablet.
        androidx.compose.foundation.layout.Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
        NavHost(navController = nav, startDestination = Routes.TODAY, modifier = Modifier.widthIn(max = if (route in tabRoutes) CARDS_MAX else FORM_MAX).fillMaxSize()) {
            composable(Routes.TODAY) {
                TodayScreen(
                    name = remember { ProfilePrefs.name(syncContext) },
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
                    onUndoWater = { scope.launch { undoWater(db, syncContext, dayFrom, dayTo) } },
                    refreshing = refreshing,
                    onRefresh = {
                        scope.launch {
                            refreshing = true
                            runCatching { uk.woozy.fit.health.HealthSync.run(syncContext, db, uk.woozy.fit.health.HealthGateway(syncContext)) }
                            refreshTick++
                            refreshing = false
                        }
                    },
                    steps = health?.steps,
                    burnedKcal = health?.activeKcal,
                    proteinG = dayTotals[Nutrients.PROTEIN] ?: 0.0,
                    netCarbsG = Nutrients.netCarbs(dayTotals),
                    fatG = dayTotals[Nutrients.FAT] ?: 0.0,
                    problem = problem,
                    onProblem = { nav.navigate(Routes.HEALTH) },
                    onSteps = { if (!openSamsungHealth(syncContext)) nav.navigate(Routes.HEALTH) },
                    onDiary = { nav.navigateTab(Routes.DIARY) },
                    today = today,
                    dayTotals = dayTotals,
                    nutrientTargets = nutrientTargets,
                    onNutrients = { nav.navigate("${Routes.NUTRIENTS}/$todayText") },
                    checkIn = if (today.dayOfWeek == DayOfWeek.SUNDAY) listOf(
                        "mood" to "Log your mood", "metrics" to "Take body metrics", "photo" to "Progress photo",
                    ).map { (k, l) -> CheckInRow(k, l, ticks.any { it.item == k && it.done }) } else null,
                    onToggleCheckIn = { k, v -> scope.launch { db.checkIns().put(uk.woozy.fit.data.db.CheckInEntity(sundayDate, k, v)) } },
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
                    onNewWorkout = { scope.launch { nav.navigate("${Routes.EDIT}/${createWorkout(db, "New workout")}") } },
                    onNewExercise = { nav.navigate(Routes.NEW_EXERCISE) },
                    onPast = { nav.navigate(Routes.PAST) },
                )
            }
            composable(Routes.DIARY) {
                DiaryScreen(db = db, onAdd = { meal, date -> nav.navigate("${Routes.ADD}/${meal.key}/$date") }, onNutrients = { nav.navigate("${Routes.NUTRIENTS}/$it") })
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
                    onRecipe = { nav.navigate(Routes.RECIPE) },
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
            composable(Routes.PROGRESS) { ProgressScreen(db = db, onRecords = { nav.navigate(Routes.RECORDS) }, onScale = { nav.navigate(Routes.SCALE) }) }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    themeMode = themeMode,
                    onThemeMode = onThemeMode,
                    scheme = scheme,
                    onScheme = onScheme,
                    frosted = frosted,
                    onFrosted = onFrosted,
                    onBack = { nav.popBackStack() },
                    onDesign = { nav.navigate(Routes.DESIGN) },
                    onScale = { nav.navigate(Routes.SCALE) },
                    onHealth = { nav.navigate(Routes.HEALTH) },
                    onBackup = { nav.navigate(Routes.BACKUP) },
                    onProfile = { nav.navigate("profile") },
                    onWorkoutSettings = { nav.navigate("workoutsettings") },
                    onHqConnection = { nav.navigate("hqconnection") },
                    onTargets = { nav.navigate(Routes.TARGETS) },
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
                    onEdit = { nav.navigate("${Routes.EDIT}/$it") },
                )
            }
            composable("${Routes.EDIT}/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { backStack ->
                TemplateEditorScreen(db = db, templateId = backStack.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
            }
            composable(Routes.PAST) { PastWorkoutsScreen(db = db, onBack = { nav.popBackStack() }, onOpen = { nav.navigate("${Routes.SUMMARY}/$it") }) }
            composable(Routes.RECIPE) { RecipeScreen(db = db, onBack = { nav.popBackStack() }) }
            composable("profile") { ProfileScreen(onBack = { nav.popBackStack() }) }
            composable("workoutsettings") { WorkoutSettingsScreen(onBack = { nav.popBackStack() }) }
            composable("hqconnection") { HqConnectionScreen(db = db, onBack = { nav.popBackStack() }) }
            composable(Routes.TARGETS) { TargetsEditorScreen(db = db, onBack = { nav.popBackStack() }) }
            composable("${Routes.NUTRIENTS}/{date}", arguments = listOf(navArgument("date") { type = NavType.StringType })) { backStack ->
                NutrientSummaryScreen(db = db, date = backStack.arguments?.getString("date") ?: LocalDate.now().toString(), onBack = { nav.popBackStack() })
            }
            composable(Routes.NEW_EXERCISE) { NewExerciseScreen(db = db, onBack = { nav.popBackStack() }) }
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
                    onHistory = { nav.navigate("${Routes.HISTORY}/$it") },
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
                    onEdit = { nav.navigate("${Routes.WORKOUT}/${backStack.arguments?.getLong("id") ?: 0L}") },
                )
            }
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

/** Samsung Health owns steps; open it there. False when it is not installed. */
private fun openSamsungHealth(c: android.content.Context): Boolean {
    val i = c.packageManager.getLaunchIntentForPackage("com.sec.android.app.shealth") ?: return false
    return runCatching { c.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
}
