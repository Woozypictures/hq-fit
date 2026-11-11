package uk.woozy.fit.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import uk.woozy.fit.data.db.DiaryEntryEntity
import uk.woozy.fit.data.db.Diary
import uk.woozy.fit.data.db.FoodEntity
import uk.woozy.fit.data.db.HqFitDatabase
import uk.woozy.fit.data.db.RecurringFoodEntity
import uk.woozy.fit.data.db.WaterEntryEntity
import uk.woozy.fit.designsystem.HqCard
import uk.woozy.fit.designsystem.HqPillButton
import uk.woozy.fit.designsystem.HqProgressBar
import uk.woozy.fit.designsystem.HqRing
import uk.woozy.fit.designsystem.HqText
import uk.woozy.fit.designsystem.HqTheme
import uk.woozy.fit.designsystem.MacroBar
import uk.woozy.fit.designsystem.PillStyle
import uk.woozy.fit.designsystem.SectionLabel
import uk.woozy.fit.designsystem.formatWhole
import uk.woozy.fit.model.Meal
import uk.woozy.fit.model.NumberEntry
import uk.woozy.fit.model.Nutrients
import uk.woozy.fit.model.Targets
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Targets as saved in the database, falling back to the starting values. */
@Composable
fun rememberTargets(db: HqFitDatabase, key: Any? = null): Targets {
    val t by produceState(Targets(), db, key) {
        val m = db.targets().all().associate { it.key to it.value }
        val d = Targets()
        value = d.copy(
            energyKcal = m["energy"]?.toInt() ?: d.energyKcal,
            proteinG = m["protein"]?.toInt() ?: d.proteinG,
            netCarbsG = m["carbs"]?.toInt() ?: d.netCarbsG,
            fatG = m["fat"]?.toInt() ?: d.fatG,
            steps = m["steps"]?.toInt() ?: d.steps,
            waterGlasses = m["water_glasses"]?.toInt() ?: d.waterGlasses,
            waterGlassMl = m["water_glass_ml"]?.toInt() ?: d.waterGlassMl,
        )
    }
    return t
}

fun dayBounds(date: LocalDate): Pair<Long, Long> {
    val zone = ZoneId.systemDefault()
    return date.atStartOfDay(zone).toInstant().toEpochMilli() to date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
}

fun guessMeal(hour: Int): Meal = when {
    hour < 11 -> Meal.BREAKFAST
    hour < 16 -> Meal.LUNCH
    hour < 21 -> Meal.DINNER
    else -> Meal.SNACKS
}

private val longDate = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK)

/** The food diary for one day: energy, macros against target, meal groups, water. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen(db: HqFitDatabase, onAdd: (Meal, String) -> Unit, onNutrients: (String) -> Unit = {}) {
    val c = HqTheme.colors
    val scope = rememberCoroutineScope()
    var dateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val date = LocalDate.parse(dateText)
    val targets = rememberTargets(db)
    LaunchedEffect(dateText) { if (!date.isAfter(LocalDate.now())) Diary.applyRepeats(db, dateText, System.currentTimeMillis()) }
    val flow = remember(db, dateText) { db.diary().observeDay(dateText) }
    val entries by flow.collectAsState(initial = emptyList())
    val (from, to) = dayBounds(date)
    val waterFlow = remember(db, dateText) { db.water().observeTotal(from, to) }
    val waterMl by waterFlow.collectAsState(initial = 0)
    val waterContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val names by produceState(emptyMap<String, Pair<String, String>>(), db) {
        value = db.nutrients().all().associate { it.key to (it.name to it.unit) }
    }
    var editing by remember { mutableStateOf<DiaryEntryEntity?>(null) }

    val totals = Diary.totals(entries)
    val eaten = totals[Nutrients.ENERGY] ?: 0.0

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle("Diary")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous day", tint = c.ink,
                modifier = Modifier.size(48.dp).clickable(role = Role.Button) { dateText = date.minusDays(1).toString() }.padding(12.dp),
            )
            HqText(
                if (date == LocalDate.now()) "Today" else date.format(longDate),
                16.sp, FontWeight.SemiBold, c.primary, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next day", tint = c.ink,
                modifier = Modifier.size(48.dp).clickable(role = Role.Button) { dateText = date.plusDays(1).toString() }.padding(12.dp),
            )
        }

        HqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    HqRing(progress = (eaten / targets.energyKcal).toFloat().coerceIn(0f, 1f), color = c.primary, diameter = 112.dp, stroke = 12.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            HqText(formatWhole(eaten), 22.sp, FontWeight.Bold)
                            HqText("of ${formatWhole(targets.energyKcal.toDouble())} kcal", 11.sp, color = c.muted)
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val left = targets.energyKcal - eaten
                        HqText(formatWhole(kotlin.math.abs(left)), 24.sp, FontWeight.Bold, if (left < 0) c.fat else c.ink)
                        HqText(if (left < 0) "kcal over" else "kcal left", 12.sp, color = c.muted)
                    }
                }
                MacroBar("Protein", totals[Nutrients.PROTEIN] ?: 0.0, targets.proteinG.toDouble(), "g", c.protein)
                MacroBar("Net carbs", Nutrients.netCarbs(totals), targets.netCarbsG.toDouble(), "g", c.carbs)
                MacroBar("Fat", totals[Nutrients.FAT] ?: 0.0, targets.fatG.toDouble(), "g", c.fat)
                TextButton(onClick = { onNutrients(dateText) }) { HqText("All nutrients", 14.sp, FontWeight.SemiBold, c.primary) }
            }
        }

        HqCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Water")
                    Spacer(Modifier.weight(1f))
                    HqText("${formatWhole(waterMl.toDouble())} / ${formatWhole(targets.waterMl.toDouble())} ml", 14.sp, color = c.muted)
                }
                HqProgressBar((waterMl.toFloat() / targets.waterMl).coerceIn(0f, 1f), c.carbs)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HqPillButton("+ ${targets.waterGlassMl} ml", onClick = {
                        scope.launch { db.water().insert(WaterEntryEntity(epochMs = waterStamp(date), ml = targets.waterGlassMl)) }
                    }, modifier = Modifier.weight(1f))
                    HqPillButton("Undo", onClick = { scope.launch { undoWater(db, waterContext, from, to) } }, modifier = Modifier.weight(1f), style = PillStyle.Outline)
                }
            }
        }

        Meal.entries.forEach { meal ->
            val rows = entries.filter { it.meal == meal.key }
            HqCard(Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClickLabel = "Add to ${meal.title}") { onAdd(meal, dateText) }.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = c.primary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.size(12.dp))
                        HqText(meal.title, 16.sp, FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        HqText("${formatWhole(rows.sumOf { Nutrients.decode(it.nutrients)[Nutrients.ENERGY] ?: 0.0 })} kcal", 14.sp, color = c.muted)
                    }
                    rows.forEach { e ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button) { editing = e }.padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                HqText(e.name, 15.sp, FontWeight.Medium)
                                if (e.grams != null) HqText("${NumberEntry.format(e.grams!!)} g", 12.sp, color = c.muted)
                            }
                            HqText(formatWhole(Nutrients.decode(e.nutrients)[Nutrients.ENERGY] ?: 0.0), 14.sp, color = c.muted)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    editing?.let { e ->
        EntrySheet(db, e, names, onDone = { editing = null })
    }
}

private fun waterStamp(date: LocalDate): Long {
    val (from, to) = dayBounds(date)
    val now = System.currentTimeMillis()
    return if (now in from until to) now else from + (to - from) / 2
}

/** Edit the amount or meal of a logged item, see its nutrients, or delete it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntrySheet(db: HqFitDatabase, entry: DiaryEntryEntity, names: Map<String, Pair<String, String>>, onDone: () -> Unit) {
    val c = HqTheme.colors
    val scope = rememberCoroutineScope()
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    var grams by remember { mutableStateOf(entry.grams?.let { NumberEntry.format(it) }.orEmpty()) }
    var meal by remember { mutableStateOf(Meal.fromKey(entry.meal)) }
    val n = Nutrients.decode(entry.nutrients)
    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.card) {
        Column(Modifier.navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HqText(entry.name, 20.sp, FontWeight.Bold)
            if (entry.grams != null) {
                OutlinedTextField(
                    value = grams, onValueChange = { grams = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { HqText("Amount (g)", 13.sp, color = c.muted) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                )
            }
            MealChoice(meal) { meal = it }
            NutrientList(n, names)
            HqPillButton("Save", onClick = {
                scope.launch {
                    Diary.changeAmount(db, entry.id, grams.toDoubleOrNull() ?: entry.grams ?: 0.0, meal)
                    onDone()
                }
            }, modifier = Modifier.fillMaxWidth())
            HqPillButton("Delete", onClick = { scope.launch { db.diary().delete(entry.id); runCatching { uk.woozy.fit.health.HealthGateway(appContext).let { g -> if (g.available) g.deleteMeals(listOf(entry.id)) } }; onDone() } }, modifier = Modifier.fillMaxWidth(), style = PillStyle.Outline)
        }
    }
}

@Composable
private fun MealChoice(selected: Meal, onSelect: (Meal) -> Unit) {
    uk.woozy.fit.designsystem.SegmentedChoice(
        options = Meal.entries.map { it.title.take(5) },
        selectedIndex = Meal.entries.indexOf(selected),
        onSelect = { onSelect(Meal.entries[it]) },
    )
}

@Composable
private fun NutrientList(n: Map<String, Double>, names: Map<String, Pair<String, String>>) {
    val c = HqTheme.colors
    val rows = n.entries.filter { it.value > 0 && names.containsKey(it.key) }
    if (rows.isEmpty()) return
    SectionLabel("Nutrients")
    rows.sortedBy { names.keys.indexOf(it.key) }.forEach { (key, value) ->
        val (name, unit) = names.getValue(key)
        Row(Modifier.fillMaxWidth()) {
            HqText(name, 14.sp, modifier = Modifier.weight(1f))
            HqText("${NumberEntry.format(Math.round(value * 10) / 10.0)} $unit", 14.sp, color = c.muted)
        }
    }
}

// ------------------------------------------------------------------ add food

/** Search your foods, pick one, choose an amount, log it. Also creates custom foods and quick adds. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodScreen(
    db: HqFitDatabase,
    meal: Meal,
    date: String,
    startQuick: Boolean,
    off: uk.woozy.fit.data.off.OffClient,
    onBack: () -> Unit,
    onCustom: () -> Unit,
    onScan: () -> Unit,
    onRecipe: () -> Unit = {},
) {
    val c = HqTheme.colors
    val scope = rememberCoroutineScope()
    var online by remember { mutableStateOf<List<uk.woozy.fit.data.off.FoodDraft>?>(null) }
    var onlineMsg by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<FoodEntity>()) }
    var picked by remember { mutableStateOf<FoodEntity?>(null) }
    var quick by remember { mutableStateOf(startQuick) }
    var refresh by remember { mutableStateOf(0) }
    LaunchedEffect(text, refresh) { results = db.foods().search(text.trim()) }
    val names by produceState(emptyMap<String, Pair<String, String>>(), db) {
        value = db.nutrients().all().associate { it.key to (it.name to it.unit) }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { BackBar("Add to ${meal.title}", onBack) }
        OutlinedTextField(
            value = text, onValueChange = { text = it }, singleLine = true,
            label = { HqText("Search foods", 13.sp, color = c.muted) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        HqCard(Modifier.fillMaxWidth().padding(16.dp)) {
            ListRow(Icons.Rounded.QrCodeScanner, "Scan barcode", "Look a product up in Open Food Facts", onClick = onScan)
            ListRow(Icons.Rounded.Add, "Create a custom food", "Your own figures per 100 g", onClick = onCustom)
            ListRow(Icons.Rounded.Add, "Create a recipe", "Combine foods into one", onClick = onRecipe)
            ListRow(Icons.Rounded.EditNote, "Quick add", "A name and calories, no food needed", onClick = { quick = true })
        }
        if (results.isEmpty()) {
            HqText(
                if (text.isBlank()) "No foods yet. Create a custom food, or quick add. Barcode scanning and Open Food Facts search arrive in milestone 6."
                else "Nothing matches \"$text\".",
                14.sp, color = c.muted, modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (text.trim().length >= 2) {
            ListRow(
                Icons.Rounded.Search,
                if (searching) "Searching Open Food Facts…" else "Search Open Food Facts for \"${text.trim()}\"",
                if (onlineMsg.isNotEmpty()) onlineMsg else "Online. Results are saved on this phone",
                onClick = {
                    if (!searching) {
                        searching = true
                        onlineMsg = ""
                        scope.launch {
                            when (val r = off.search(text)) {
                                is uk.woozy.fit.data.off.OffResult.Found -> { online = r.value; if (r.value.isEmpty()) onlineMsg = "No matches" }
                                uk.woozy.fit.data.off.OffResult.Missing -> onlineMsg = "No matches"
                                uk.woozy.fit.data.off.OffResult.Offline -> onlineMsg = "Could not reach Open Food Facts. Nothing was changed."
                            }
                            searching = false
                        }
                    }
                },
            )
        }
        LazyColumn(Modifier.weight(1f)) {
            online?.let { drafts ->
                items(drafts.size) { i ->
                    val d = drafts[i]
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) {
                            scope.launch { picked = uk.woozy.fit.data.db.Foods.saveDraft(db, d, System.currentTimeMillis()) }
                        }.padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        HqText(d.name, 16.sp, FontWeight.Medium)
                        HqText(
                            listOf(d.brand, d.per100[Nutrients.ENERGY]?.let { "${formatWhole(it)} kcal per 100 g" } ?: "Incomplete data").filter { it.isNotBlank() }.joinToString(" · "),
                            13.sp, color = c.muted,
                        )
                    }
                }
            }
            items(results, key = { it.id }) { f ->
                Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { picked = f }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    HqText(f.name, 16.sp, FontWeight.Medium)
                    val kcal = Nutrients.decode(f.per100)[Nutrients.ENERGY]
                    HqText(
                        listOf(f.brand, kcal?.let { "${formatWhole(it)} kcal per 100 g" } ?: "", if (f.incomplete) "Incomplete data" else "").filter { it.isNotBlank() }.joinToString(" · "),
                        13.sp, color = c.muted,
                    )
                }
            }
        }
    }

    picked?.let { f ->
        AmountSheet(db, f, meal, date, names, onDone = { picked = null; refresh++ }, onLogged = { picked = null; onBack() })
    }
    if (quick) QuickAddSheet(db, meal, date, onDismiss = { quick = false }, onLogged = { quick = false; onBack() })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AmountSheet(
    db: HqFitDatabase,
    food: FoodEntity,
    meal: Meal,
    date: String,
    names: Map<String, Pair<String, String>>,
    onDone: () -> Unit,
    onLogged: () -> Unit,
) {
    val c = HqTheme.colors
    val scope = rememberCoroutineScope()
    var grams by remember { mutableStateOf(NumberEntry.format(food.servingG ?: 100.0)) }
    val per100 = Nutrients.decode(food.per100)
    val g = grams.toDoubleOrNull() ?: 0.0
    val scaled = Nutrients.scale(per100, g)
    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.card) {
        Column(Modifier.navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HqText(food.name, 20.sp, FontWeight.Bold)
            if (food.brand.isNotBlank()) HqText(food.brand, 14.sp, color = c.muted)
            if (food.incomplete) HqText("Incomplete data: the source left out some figures, so totals may be low.", 13.sp, color = c.muted)
            OutlinedTextField(
                value = grams, onValueChange = { grams = it.filter { ch -> ch.isDigit() || ch == '.' } },
                label = { HqText("Amount (g)", 13.sp, color = c.muted) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
            )
            if (food.servingG != null) HqText("One serving is ${NumberEntry.format(food.servingG!!)} g${if (food.servingLabel.isNotBlank()) " (${food.servingLabel})" else ""}", 13.sp, color = c.muted)
            NutrientList(scaled, names)
            HqPillButton("Log to ${meal.title}", onClick = {
                if (g > 0) scope.launch { Diary.log(db, food, g, date, meal, System.currentTimeMillis()); onLogged() }
            }, modifier = Modifier.fillMaxWidth())
            HqPillButton("Log this every day", onClick = {
                if (g > 0) scope.launch {
                    db.recurring().insert(RecurringFoodEntity(foodId = food.id, grams = g, meal = meal.key, fromDate = date))
                    Diary.applyRepeats(db, date, System.currentTimeMillis())
                    onLogged()
                }
            }, modifier = Modifier.fillMaxWidth(), style = PillStyle.Outline)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAddSheet(db: HqFitDatabase, meal: Meal, date: String, onDismiss: () -> Unit, onLogged: () -> Unit) {
    val c = HqTheme.colors
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var p by remember { mutableStateOf("") }
    var cb by remember { mutableStateOf("") }
    var f by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.card) {
        Column(Modifier.navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HqText("Quick add to ${meal.title}", 20.sp, FontWeight.Bold)
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { HqText("Name", 13.sp, color = c.muted) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            NumberField("Calories (kcal)", kcal) { kcal = it }
            NumberField("Protein (g)", p) { p = it }
            NumberField("Carbs (g)", cb) { cb = it }
            NumberField("Fat (g)", f) { f = it }
            HqText("Leave calories empty to work them out from protein, carbs and fat.", 12.sp, color = c.muted)
            HqPillButton("Add", onClick = {
                val map = buildMap {
                    kcal.toDoubleOrNull()?.let { put(Nutrients.ENERGY, it) }
                    p.toDoubleOrNull()?.let { put(Nutrients.PROTEIN, it) }
                    cb.toDoubleOrNull()?.let { put(Nutrients.CARBS, it) }
                    f.toDoubleOrNull()?.let { put(Nutrients.FAT, it) }
                }
                if (map.isNotEmpty()) scope.launch {
                    Diary.quickAdd(db, name.ifBlank { "Quick add" }, map, date, meal, System.currentTimeMillis())
                    onLogged()
                }
            }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun NumberField(label: String, value: String, modifier: Modifier = Modifier.fillMaxWidth(), onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { ch -> ch.isDigit() || ch == '.' }) },
        label = { HqText(label, 13.sp, color = HqTheme.colors.muted) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

// ------------------------------------------------------------------ custom food

/** Your own food, figures per 100 g. The extra nutrients are optional. */
@Composable
fun CustomFoodScreen(db: HqFitDatabase, barcode: String?, onBack: () -> Unit) {
    val c = HqTheme.colors
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    val fields = remember {
        listOf(
            "Calories (kcal)" to Nutrients.ENERGY, "Protein (g)" to Nutrients.PROTEIN, "Carbohydrate (g)" to Nutrients.CARBS,
            "Fibre (g)" to Nutrients.FIBRE, "Sugars (g)" to Nutrients.SUGARS, "Fat (g)" to Nutrients.FAT,
            "Saturated fat (g)" to Nutrients.SAT_FAT, "Sodium (mg)" to Nutrients.SODIUM,
        )
    }
    val values = remember { mutableStateOf(mapOf<String, String>()) }
    var serving by remember { mutableStateOf("") }
    var servingLabel by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { BackBar(if (barcode != null) "New food from $barcode" else "Custom food", onBack) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { HqText("Name", 13.sp, color = c.muted) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = brand, onValueChange = { brand = it }, label = { HqText("Brand (optional)", 13.sp, color = c.muted) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            SectionLabel("Per 100 g")
            fields.forEach { (label, key) ->
                NumberField(label, values.value[key].orEmpty()) { values.value = values.value + (key to it) }
            }
            SectionLabel("Usual serving (optional)")
            NumberField("Serving size (g)", serving) { serving = it }
            OutlinedTextField(value = servingLabel, onValueChange = { servingLabel = it }, label = { HqText("Serving description, e.g. 1 slice", 13.sp, color = c.muted) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
        }
        Column(Modifier.padding(16.dp)) {
            HqPillButton("Save food", onClick = {
                if (name.isNotBlank()) {
                    val per = values.value.mapNotNull { (k, v) -> v.toDoubleOrNull()?.let { k to it } }.toMap()
                    val withEnergy = if ((per[Nutrients.ENERGY] ?: 0.0) > 0) per else per + (Nutrients.ENERGY to Nutrients.energyFromMacros(per))
                    scope.launch {
                        db.foods().insert(
                            FoodEntity(
                                name = name.trim(), brand = brand.trim(), barcode = barcode, source = "custom", servingG = serving.toDoubleOrNull(),
                                servingLabel = servingLabel.trim(), per100 = Nutrients.encode(withEnergy), createdMs = System.currentTimeMillis(),
                            ),
                        )
                        onBack()
                    }
                }
            }, modifier = Modifier.fillMaxWidth())
        }
    }
}


/** Takes back the latest glass in a window, here and in Health Connect. */
internal suspend fun undoWater(db: uk.woozy.fit.data.db.HqFitDatabase, context: android.content.Context, from: Long, to: Long) {
    val row = db.water().latestBetween(from, to) ?: return
    db.water().delete(row.id)
    runCatching { uk.woozy.fit.health.HealthGateway(context.applicationContext).let { g -> if (g.available) g.deleteWater(row.id) } }
}
