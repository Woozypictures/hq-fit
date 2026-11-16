package uk.woozy.fit.ui

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.debounce
import androidx.lifecycle.repeatOnLifecycle
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.rounded.Add
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import uk.woozy.fit.designsystem.GlassItem
import uk.woozy.fit.designsystem.GlassNavHeight
import uk.woozy.fit.designsystem.GlassNavLift
import uk.woozy.fit.designsystem.GlassPillNav
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import uk.woozy.fit.designsystem.glass
import androidx.compose.ui.text.font.FontWeight
import uk.woozy.fit.designsystem.HqText
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import uk.woozy.fit.designsystem.GlassMenuItem
import uk.woozy.fit.designsystem.frostedContent
import uk.woozy.fit.designsystem.GlassRoundButton
import uk.woozy.fit.designsystem.LocalGlassInset
import uk.woozy.fit.designsystem.glassSource
import uk.woozy.fit.designsystem.glassStyle
import uk.woozy.fit.designsystem.rememberGlassBackdrop
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
    const val TRAINING = "training"
    const val REARRANGE = "rearrange"
    const val PROGRAMMES = "programmes"
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
    const val TIMELINE = "timeline"
}

/** Today, Train, (+), Diary, Progress. The + sits between the second and third tab. */
private val tabRoutes = listOf(Routes.TODAY, Routes.TRAIN, Routes.DIARY, Routes.PROGRESS)

/**
 * A destination with its own width cap (wide for tabs, narrower for forms), so the cap never changes while two pages
 * cross-fade. Capping the whole NavHost by the current route squeezed the outgoing page into the new width mid-fade.
 */
private fun androidx.navigation.NavGraphBuilder.capped(
    route: String,
    arguments: List<androidx.navigation.NamedNavArgument> = emptyList(),
    content: @androidx.compose.runtime.Composable androidx.compose.animation.AnimatedContentScope.(androidx.navigation.NavBackStackEntry) -> Unit,
) = androidx.navigation.compose.composable(route, arguments) { entry ->
    val scope = this
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
        androidx.compose.foundation.layout.Box(Modifier.widthIn(max = if (route in tabRoutes) CARDS_MAX else FORM_MAX).fillMaxSize()) { scope.content(entry) }
    }
}

private val barItems = listOf(
    BarItem("Home", Icons.Rounded.Home),
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
    // Live pipeline. (1) Back in the app: re-read Health Connect at once, and sync if the last one is over 2 min old.
    // (2) While open: re-read every 2 min, so the watch's steps and calories tick along. (3) After anything is logged
    // (food, water, a weight, a workout): the diary updates instantly from the local database, then a quick
    // Health Connect sync follows 6s after the last change, so HQ and Samsung Health catch up without a refresh.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val syncScope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshTick++
                if (System.currentTimeMillis() - uk.woozy.fit.health.HealthSync.lastSyncMs(syncContext) > 120_000) syncScope.launch {
                    refreshing = true
                    runCatching { uk.woozy.fit.health.HealthSync.run(syncContext, db, uk.woozy.fit.health.HealthGateway(syncContext), quick = true) }
                    refreshing = false
                    refreshTick++
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    androidx.compose.runtime.LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            while (true) { kotlinx.coroutines.delay(120_000); refreshTick++ }
        }
    }
    androidx.compose.runtime.LaunchedEffect(db) {
        val changes = kotlinx.coroutines.flow.callbackFlow {
            val o = object : androidx.room.InvalidationTracker.Observer(arrayOf("diary_entry", "water_entry", "weight_entry", "workout_session")) {
                override fun onInvalidated(tables: Set<String>) { trySend(Unit) }
            }
            db.invalidationTracker.addObserver(o)
            awaitClose { db.invalidationTracker.removeObserver(o) }
        }
        @OptIn(kotlinx.coroutines.FlowPreview::class)
        changes.debounce(6_000).collect {
            runCatching { uk.woozy.fit.health.HealthSync.run(syncContext, db, uk.woozy.fit.health.HealthGateway(syncContext), quick = true) }
            refreshTick++
        }
    }
    val today = rememberToday()
    val health by androidx.compose.runtime.produceState<uk.woozy.fit.health.HealthSnapshot?>(null, db, refreshTick, today) {
        value = runCatching {
            val g = uk.woozy.fit.health.HealthGateway(syncContext)
            if (!g.available) null else g.snapshot(
                today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant(), java.time.Instant.now(),
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
    val sundayDate = remember(today) { today.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)).toString() }
    val ticksFlow = remember(db, sundayDate) { db.checkIns().observe(sundayDate) }
    val ticks by ticksFlow.collectAsState(initial = emptyList())
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    var quickAdd by remember { mutableStateOf(false) }
    var refreshToast by remember { mutableStateOf(0L) }
    var toastText by remember { mutableStateOf("") }
    var waterFocus by remember { mutableStateOf(0L) }
    val calendarFlow = remember(db, today) {
        db.schedule().observeBetween(today.with(DayOfWeek.MONDAY).toString(), today.plusDays(70).toString())
    }
    val calendar by calendarFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val activeFlow = remember(db) { db.sessions().observeActive() }
    val active by activeFlow.collectAsState(initial = null)
    fun startOrResume(s: ScheduledWorkoutEntity) {
        scope.launch {
            val id = Sessions.start(db, s, System.currentTimeMillis(), WorkoutPrefs.weightStep(syncContext), WorkoutPrefs.barKg(syncContext))
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
    // Keep my order (Settings > Workout): Today offers the next workout in the plan's order on any day.
    val keepOrder by produceState(false, route) { value = uk.woozy.fit.data.db.Rotation.on(db) }
    val upNext by produceState<ScheduledWorkoutEntity?>(null, route, calendar, keepOrder) {
        value = if (keepOrder) uk.woozy.fit.data.db.Rotation.upNext(db, today) else null
    }
    val todays = (if (keepOrder) upNext else null) ?: calendar.firstOrNull { it.date == today.toString() && it.status == "planned" }
    // A workout finished today keeps its card (marked complete) instead of turning the day into a rest day.
    val doneToday by produceState<uk.woozy.fit.data.db.WorkoutSessionEntity?>(null, route, dayFrom, calendar) {
        value = db.sessions().doneSince(dayFrom).lastOrNull { it.startedMs < dayTo }
    }
    val missed = if (keepOrder) 0 else calendar.count { it.status == "planned" && it.date < today.toString() && it.date >= today.minusDays(7).toString() }
    val weighedToday by produceState(false, route, dayFrom) {
        value = db.weights().observeAllOnce().any { it.epochMs in dayFrom until dayTo }
    }
    val next = calendar.firstOrNull { it.date > today.toString() && it.status == "planned" }

    Scaffold(
        containerColor = HqTheme.colors.page,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        val backdrop = rememberGlassBackdrop()
        val gc = HqTheme.colors
        val gStyle = remember(gc) { glassStyle(gc.dark, gc.ink, gc.muted) }
        val navInset = androidx.compose.foundation.layout.WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + GlassNavHeight + GlassNavLift
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        // Tab pages may go wide (two columns of cards); every other page is capped so it reads well on a tablet.
        androidx.compose.foundation.layout.Box(Modifier.padding(padding).fillMaxSize().frostedContent(quickAdd).glassSource(backdrop), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
        val titleCollapse = remember { TitleCollapse() }
        androidx.compose.runtime.LaunchedEffect(route) { titleCollapse.reset() }
        androidx.compose.runtime.CompositionLocalProvider(LocalGlassInset provides (if (route in tabRoutes) navInset + (if (active != null) 68.dp else 0.dp) else 0.dp), LocalTitleCollapse provides titleCollapse) {
        NavHost(navController = nav, startDestination = Routes.TODAY, modifier = Modifier.fillMaxSize().nestedScroll(titleCollapse.connection),
            // Snappy with a slight transition: a short crossfade.
            enterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(150)) },
            exitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(90)) },
            popEnterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(150)) },
            popExitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(90)) },
        ) {
            capped(Routes.TODAY) {
                HomeScaleListener(db) { kg -> toastText = "\u2696\uFE0F Weighed ${"%.1f".format(kg)} kg, saved"; refreshToast = System.currentTimeMillis() }
                TodayScreen(
                    name = remember { ProfilePrefs.name(syncContext) },
                    onPlace = { key ->
                        when (key) {
                            "programmes" -> nav.navigate(Routes.PROGRAMMES)
                            "history" -> nav.navigate(Routes.PAST)
                            "records" -> nav.navigate(Routes.RECORDS)
                            "library" -> nav.navigate(Routes.LIBRARY)
                            "scale" -> nav.navigate(Routes.SCALE)
                        }
                    },
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                    onStartWorkout = {
                        val a = active
                        if (a != null) nav.navigate("${Routes.WORKOUT}/${a.id}")
                        else if (todays != null) {
                            val t = todays
                            if (t.date != today.toString()) scope.launch { uk.woozy.fit.data.db.Rotation.bringToToday(db, t, today); nav.navigate("${Routes.PREVIEW}/${t.id}") }
                            else nav.navigate("${Routes.PREVIEW}/${t.id}")
                        }
                        else doneToday?.let { nav.navigate("${Routes.SUMMARY}/${it.id}") }
                    },
                    workoutTitle = active?.name ?: todays?.templateName ?: doneToday?.name,
                    workoutDone = active == null && todays == null && doneToday != null,
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
                            toastText = "Last refreshed " + java.time.format.DateTimeFormatter.ofPattern("HH:mm").format(java.time.LocalTime.now())
                            refreshToast = System.currentTimeMillis()
                        }
                    },
                    steps = if (problem == null) health?.let { it.steps ?: 0L } else health?.steps,
                    burnedKcal = if (problem == null) health?.let { it.activeKcal ?: 0.0 } else health?.activeKcal,
                    watchLastMs = health?.watchLastMs,
                    proteinG = dayTotals[Nutrients.PROTEIN] ?: 0.0,
                    netCarbsG = Nutrients.netCarbs(dayTotals),
                    fatG = dayTotals[Nutrients.FAT] ?: 0.0,
                    problem = problem,
                    onProblem = { nav.navigate(Routes.HEALTH) },
                    missedCount = missed,
                    onMissed = { nav.navigateTab(Routes.TRAIN) },
                    onRestDay = { nav.navigate(Routes.TRAINING) },
                    weighedToday = weighedToday,
                    onScale = { nav.navigate(Routes.SCALE) },
                    onSteps = { if (!openSamsungHealth(syncContext)) nav.navigate(Routes.HEALTH) },
                    onDiary = { nav.navigateTab(Routes.DIARY) },
                    today = today,
                    waterFocus = waterFocus,
                    onWaterShown = { waterFocus = 0L },
                    dayTotals = dayTotals,
                    nutrientTargets = nutrientTargets,
                    onNutrients = { nav.navigate("${Routes.NUTRIENTS}/$todayText") },
                    checkIn = if (today.dayOfWeek == DayOfWeek.SUNDAY) listOf(
                        "mood" to "Log your mood", "metrics" to "Take body metrics", "photo" to "Progress photo",
                    ).map { (k, l) -> CheckInRow(k, l, ticks.any { it.item == k && it.done }) } else null,
                    onToggleCheckIn = { k, v -> scope.launch { db.checkIns().put(uk.woozy.fit.data.db.CheckInEntity(sundayDate, k, v)) } },
                    targets = targets,
                    buttonLabel = if (active != null) "Resume workout" else if (todays == null && doneToday != null) "View summary" else "View workout",
                    workoutCaption = when {
                        active != null -> "In progress"
                        todays == null && doneToday != null -> "Completed today" + (next?.let { n -> ". Next: ${n.templateName}, " + LocalDate.parse(n.date).format(DateTimeFormatter.ofPattern("EEEE d MMM", Locale.UK)) } ?: "")
                        todays != null && todays.date != today.toString() -> "Next in your order" + if (todays.date < today.toString()) ", from " + LocalDate.parse(todays.date).format(DateTimeFormatter.ofPattern("EEEE", Locale.UK)) else ""
                        todays != null -> if (keepOrder) "Next in your order" else "Scheduled for today"
                        next != null -> "Next: ${next.templateName}, " +
                            LocalDate.parse(next.date).format(DateTimeFormatter.ofPattern("EEEE d MMM", Locale.UK))
                        else -> "Nothing scheduled"
                    },
                )
            }
            capped(Routes.TRAIN) {
                val doneDates by produceState(emptySet<String>(), db, calendar) {
                    val zone = java.time.ZoneId.systemDefault()
                    val from = java.time.LocalDate.now().with(java.time.DayOfWeek.MONDAY).atStartOfDay(zone).toInstant().toEpochMilli()
                    value = db.sessions().doneSince(from).map { java.time.Instant.ofEpochMilli(it.startedMs).atZone(zone).toLocalDate().toString() }.toSet()
                }
                TrainScreen(
                    calendar = calendar,
                    doneDates = doneDates,
                    onOpen = { nav.navigate("${Routes.PREVIEW}/$it") },
                    onLibrary = { nav.navigate(Routes.LIBRARY) },
                    onRecords = { nav.navigate(Routes.RECORDS) },
                    onNewWorkout = { scope.launch { nav.navigate("${Routes.EDIT}/${createWorkout(db, "New workout")}") } },
                    onNewExercise = { nav.navigate(Routes.NEW_EXERCISE) },
                    onPast = { nav.navigate(Routes.PAST) },
                    onProgrammes = { nav.navigate(Routes.PROGRAMMES) },
                    onTraining = { nav.navigate(Routes.TRAINING) },
                )
            }
            capped(Routes.TRAINING) {
                val sessions by produceState(emptyList<uk.woozy.fit.data.db.WorkoutSessionEntity>(), db, calendar) {
                    val zone = java.time.ZoneId.systemDefault()
                    val from = java.time.LocalDate.now().with(java.time.DayOfWeek.MONDAY).atStartOfDay(zone).toInstant().toEpochMilli()
                    value = db.sessions().doneSince(from)
                }
                val counts by produceState(emptyMap<Long, Int>(), db, calendar) {
                    value = calendar.map { it.templateId }.distinct().associateWith { t -> db.templates().items(t).size }
                }
                TrainingScreen(
                    calendar = calendar, sessions = sessions, exerciseCounts = counts,
                    onBack = { nav.popBackStack() },
                    onOpen = { nav.navigate("${Routes.PREVIEW}/$it") },
                    onOpenSession = { nav.navigate("${Routes.SUMMARY}/$it") },
                    onRearrange = { nav.navigate(Routes.REARRANGE) },
                    onHistory = { nav.navigate(Routes.PAST) },
                )
            }
            capped(Routes.REARRANGE) {
                RearrangeScreen(calendar = calendar, onBack = { nav.popBackStack() }, onMove = { id, date -> db.schedule().move(id, date) })
            }
            capped(Routes.DIARY) {
                DiaryScreen(db = db, onAdd = { meal, date -> nav.navigate("${Routes.ADD}/${meal.key}/$date") }, onNutrients = { nav.navigate("${Routes.NUTRIENTS}/$it") })
            }
            capped(
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
            capped(
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
            capped(
                "${Routes.CUSTOM_FOOD}?barcode={barcode}",
                arguments = listOf(navArgument("barcode") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { backStack ->
                CustomFoodScreen(db = db, barcode = backStack.arguments?.getString("barcode"), onBack = { nav.popBackStack() })
            }
            capped(Routes.PROGRESS) { ProgressScreen(db = db, onRecords = { nav.navigate(Routes.RECORDS) }, onScale = { nav.navigate(Routes.SCALE) }, onPast = { nav.navigate(Routes.PAST) }, onLibrary = { nav.navigate(Routes.LIBRARY) }) }
            capped(Routes.SETTINGS) {
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
            capped(Routes.BACKUP) { BackupScreen(db = db, container = container, onBack = { nav.popBackStack() }) }
            capped(Routes.HEALTH) { HealthScreen(db = db, onBack = { nav.popBackStack() }) }
            capped(Routes.SCALE) { ScaleScreen(db = db, onBack = { nav.popBackStack() }) }
            capped(Routes.DESIGN) { DesignScreen(onBack = { nav.popBackStack() }) }
            capped(
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
            capped("${Routes.EDIT}/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { backStack ->
                TemplateEditorScreen(db = db, templateId = backStack.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
            }
            capped(Routes.PROGRAMMES) {
                ProgrammesScreen(
                    db = db, onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate("${Routes.EDIT}/$it") },
                    onNewWorkout = { scope.launch { nav.navigate("${Routes.EDIT}/${createWorkout(db, "New workout")}") } },
                )
            }
            capped(Routes.PAST) {
                PastWorkoutsScreen(
                    db = db, onBack = { nav.popBackStack() }, onOpen = { nav.navigate("${Routes.SUMMARY}/$it") }, onTimeline = { nav.navigate(Routes.TIMELINE) },
                    onLogPast = if (active != null) null else { date, templateId ->
                        scope.launch {
                            Sessions.startPast(db, templateId, date, WorkoutPrefs.weightStep(syncContext), WorkoutPrefs.barKg(syncContext))?.let { nav.navigate("${Routes.WORKOUT}/$it") }
                        }
                    },
                )
            }
            capped(Routes.TIMELINE) { HistoryScreen(db = db, onBack = { nav.popBackStack() }, onOpen = { nav.navigate("${Routes.SUMMARY}/$it") }) }
            capped(Routes.RECIPE) { RecipeScreen(db = db, onBack = { nav.popBackStack() }) }
            capped("profile") { ProfileScreen(onBack = { nav.popBackStack() }) }
            capped("workoutsettings") { WorkoutSettingsScreen(db, onBack = { nav.popBackStack() }) }
            capped("hqconnection") { HqConnectionScreen(db = db, onBack = { nav.popBackStack() }) }
            capped(Routes.TARGETS) { TargetsEditorScreen(db = db, onBack = { nav.popBackStack() }) }
            capped("${Routes.NUTRIENTS}/{date}", arguments = listOf(navArgument("date") { type = NavType.StringType })) { backStack ->
                NutrientSummaryScreen(db = db, date = backStack.arguments?.getString("date") ?: LocalDate.now().toString(), onBack = { nav.popBackStack() })
            }
            capped(Routes.NEW_EXERCISE) { NewExerciseScreen(db = db, onBack = { nav.popBackStack() }) }
            capped(Routes.RECORDS) {
                RecordsScreen(db = db, onBack = { nav.popBackStack() }, onOpen = { nav.navigate("${Routes.HISTORY}/$it") })
            }
            capped(
                "${Routes.HISTORY}/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { backStack ->
                ExerciseHistoryScreen(db = db, exerciseId = backStack.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
            }
            capped(Routes.LIBRARY) { LibraryScreen(db = db, onBack = { nav.popBackStack() }) }
            capped(
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
            capped(
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
        var showToast by remember { mutableStateOf(false) }
        LaunchedEffect(refreshToast) { if (refreshToast > 0) { showToast = true; kotlinx.coroutines.delay(2200); showToast = false } }
        if (route in tabRoutes) {
            uk.woozy.fit.designsystem.GlassTopFade(backdrop, gc.page, Modifier.align(androidx.compose.ui.Alignment.TopCenter))
            uk.woozy.fit.designsystem.GlassBottomFade(gc.page, Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
            GlassPillNav(
                items = barItems.map { GlassItem(it.label, it.icon) },
                selected = tabRoutes.indexOf(route),
                onSelect = { nav.navigateTab(tabRoutes[it]) },
                backdrop = backdrop, style = gStyle,
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
                actionIcon = Icons.Rounded.Add, actionLabel = "Add", onAction = { quickAdd = !quickAdd },
            )
        }
        // Rest timer on every tab while it runs (Everfit keeps it in reach): time left, +15s and Skip.
        var restLeft by remember { mutableStateOf(0L) }
        LaunchedEffect(route, refreshTick) {
            while (true) {
                val end = uk.woozy.fit.RestTimer.endMs(syncContext)
                restLeft = (end - System.currentTimeMillis()).coerceAtLeast(0)
                kotlinx.coroutines.delay(if (restLeft > 0) 250 else 1500)
            }
        }
        if (route in tabRoutes && restLeft > 0) uk.woozy.fit.designsystem.GlassRestPill(
            leftMs = restLeft,
            totalMs = (uk.woozy.fit.RestTimer.endMs(syncContext) - uk.woozy.fit.RestTimer.startMs(syncContext)).coerceAtLeast(1),
            backdrop = backdrop, style = gStyle, accent = uk.woozy.fit.designsystem.HqTheme.colors.primary,
            onAdd = { uk.woozy.fit.RestTimer.start(syncContext, uk.woozy.fit.RestTimer.endMs(syncContext) + 15_000, "Rest", extend = true) },
            onSkip = { uk.woozy.fit.RestTimer.cancel(syncContext); restLeft = 0 },
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(bottom = navInset + 10.dp),
        )
        // Mini player (GymMane style): a workout left running shows above the nav on every tab; tap to go back in.
        active?.let { a ->
            if (route in tabRoutes) {
                var tickNow by remember { mutableStateOf(System.currentTimeMillis()) }
                androidx.compose.runtime.LaunchedEffect(a.id) { while (true) { tickNow = System.currentTimeMillis(); kotlinx.coroutines.delay(1000) } }
                val mc = uk.woozy.fit.designsystem.HqTheme.colors
                val elapsed = ((tickNow - a.startedMs) / 1000).coerceAtLeast(0)
                val past = elapsed > 12 * 3600
                androidx.compose.foundation.layout.Row(
                    Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
                        .padding(bottom = navInset + 10.dp + (if (restLeft > 0) 66.dp else 0.dp), start = 20.dp, end = 20.dp)
                        .widthIn(max = 520.dp).fillMaxWidth().height(56.dp)
                        .glass(backdrop, gStyle, androidx.compose.foundation.shape.CircleShape)
                        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "Back to workout") { nav.navigate("${Routes.WORKOUT}/${a.id}") }
                        .padding(start = 22.dp, end = 8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                        HqText(if (past) "LOGGING" else "IN PROGRESS", 11.sp, FontWeight.SemiBold, mc.muted, letterSpacing = 1.sp)
                        HqText(a.name, 15.sp, FontWeight.SemiBold, maxLines = 1)
                    }
                    if (!past) HqText("%d:%02d".format(elapsed / 60, elapsed % 60), 15.sp, FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp))
                    androidx.compose.foundation.layout.Box(
                        Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).background(mc.primary),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) { androidx.compose.material3.Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = mc.onPrimary, modifier = Modifier.size(24.dp)) }
                }
            }
        }
        if (route in tabRoutes) uk.woozy.fit.designsystem.GlassToast(
            toastText, showToast, backdrop, gStyle,
            Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(bottom = navInset + 12.dp),
        )
        if (route in tabRoutes) uk.woozy.fit.designsystem.GlassPopupMenu(
            visible = quickAdd, onDismiss = { quickAdd = false }, backdrop = backdrop, style = gStyle, bottomInset = navInset,
            items = listOf(
                GlassMenuItem("Add food", Icons.Rounded.Search) {
                    quickAdd = false
                    nav.navigate("${Routes.ADD}/${guessMeal(java.time.LocalTime.now().hour).key}/$todayText")
                },
                GlassMenuItem("Scan barcode", Icons.Rounded.QrCodeScanner) {
                    quickAdd = false
                    nav.navigate("${Routes.SCAN}/${guessMeal(java.time.LocalTime.now().hour).key}/$todayText")
                },
                GlassMenuItem("Add water", Icons.Rounded.WaterDrop) {
                    quickAdd = false
                    scope.launch { db.water().insert(WaterEntryEntity(epochMs = System.currentTimeMillis(), ml = targets.waterGlassMl)) }
                    // Show it: go to Home, scroll to Water and flash it, and say what was added.
                    if (route != Routes.TODAY) nav.navigateTab(Routes.TODAY)
                    waterFocus = System.currentTimeMillis()
                    toastText = "\uD83D\uDCA7 ${targets.waterGlassMl} ml of water added"
                    refreshToast = System.currentTimeMillis()
                },
                GlassMenuItem("Quick add", Icons.Rounded.EditNote) {
                    quickAdd = false
                    nav.navigate("${Routes.ADD}/${guessMeal(java.time.LocalTime.now().hour).key}/$todayText?quick=true")
                },
                GlassMenuItem("Start workout", Icons.Rounded.PlayArrow) {
                    quickAdd = false
                    val a = active
                    if (a != null) nav.navigate("${Routes.WORKOUT}/${a.id}")
                    else if (todays != null) startOrResume(todays)
                    else nav.navigateTab(Routes.TRAIN)
                },
            ),
        )
        }
    }

}

/** Samsung Health owns steps; open it there. False when it is not installed. */
private fun openSamsungHealth(c: android.content.Context): Boolean {
    val i = c.packageManager.getLaunchIntentForPackage("com.sec.android.app.shealth") ?: return false
    return runCatching { c.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
}
