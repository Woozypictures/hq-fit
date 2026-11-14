package uk.woozy.fit.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import uk.woozy.fit.data.db.FoodEntity
import uk.woozy.fit.data.db.HqFitDatabase
import uk.woozy.fit.data.db.NutrientEntity
import uk.woozy.fit.data.db.TargetEntity
import uk.woozy.fit.data.db.Diary
import uk.woozy.fit.designsystem.HqCard
import uk.woozy.fit.designsystem.HqPillButton
import uk.woozy.fit.designsystem.HqProgressBar
import uk.woozy.fit.designsystem.HqText
import uk.woozy.fit.designsystem.HqTheme
import uk.woozy.fit.designsystem.PillStyle
import uk.woozy.fit.designsystem.SectionLabel
import uk.woozy.fit.designsystem.SegmentedChoice
import uk.woozy.fit.model.NumberEntry
import uk.woozy.fit.model.Nutrients

private fun round1(d: Double) = Math.round(d * 10) / 10.0

/** Build a food from other foods: ingredients and grams, the cooked weight, how many servings. Saved like any food. */
@Composable
fun RecipeScreen(db: HqFitDatabase, onBack: () -> Unit) {
    val c = HqTheme.colors
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var servings by remember { mutableStateOf("1") }
    var yieldText by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<FoodEntity>()) }
    var picked by remember { mutableStateOf<FoodEntity?>(null) }
    var grams by remember { mutableStateOf("") }
    var ingredients by remember { mutableStateOf(listOf<Pair<FoodEntity, Double>>()) }
    var saved by remember { mutableStateOf(false) }
    LaunchedEffect(search) { results = if (search.isBlank()) emptyList() else db.foods().search(search.trim()).take(8) }

    val totals = Nutrients.sum(ingredients.map { (f, g) -> Nutrients.scale(Nutrients.decode(f.per100), g) })
    val rawGrams = ingredients.sumOf { it.second }
    val yieldG = yieldText.toDoubleOrNull() ?: rawGrams

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { BackBar("New recipe", onBack) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { HqText("Recipe name", 13.sp, color = c.muted) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            SectionLabel("Ingredients")
            ingredients.forEachIndexed { i, (f, g) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        HqText(f.name, 15.sp, FontWeight.Medium)
                        HqText("${NumberEntry.format(g)} g", 13.sp, color = c.muted)
                    }
                    TextButton(onClick = { ingredients = ingredients.filterIndexed { j, _ -> j != i } }) { HqText("Remove", 13.sp, color = c.muted) }
                }
            }
            val p = picked
            if (p == null) {
                OutlinedTextField(value = search, onValueChange = { search = it }, label = { HqText("Search your foods to add", 13.sp, color = c.muted) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                results.forEach { f ->
                    Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { picked = f; grams = f.servingG?.let { NumberEntry.format(it) } ?: "100" }.padding(vertical = 6.dp)) {
                        HqText(f.name, 15.sp)
                        if (f.brand.isNotBlank()) HqText(f.brand, 12.sp, color = c.muted)
                    }
                }
            } else {
                HqText(p.name, 15.sp, FontWeight.SemiBold)
                NumberField("Grams", grams) { grams = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HqPillButton("Add", modifier = Modifier.weight(1f), enabled = (grams.toDoubleOrNull() ?: 0.0) > 0, onClick = {
                        ingredients = ingredients + (p to grams.toDouble()); picked = null; search = ""
                    })
                    HqPillButton("Cancel", style = PillStyle.Outline, modifier = Modifier.weight(1f), onClick = { picked = null })
                }
            }
            SectionLabel("Cooked recipe")
            NumberField("Cooked weight in grams (empty = ingredients total ${NumberEntry.format(rawGrams)} g)", yieldText) { yieldText = it }
            NumberField("Servings it makes", servings) { servings = it.filter { ch -> ch.isDigit() } }
            if (ingredients.isNotEmpty() && yieldG > 0) {
                val perServing = (servings.toIntOrNull() ?: 1).coerceAtLeast(1)
                HqText(
                    "Per serving: ${NumberEntry.format(round1((totals[Nutrients.ENERGY] ?: Nutrients.energyFromMacros(totals)) / perServing))} kcal, " +
                        "${NumberEntry.format(round1((totals[Nutrients.PROTEIN] ?: 0.0) / perServing))} g protein",
                    14.sp, color = c.muted,
                )
            }
            Spacer(Modifier.height(4.dp))
        }
        Column(Modifier.padding(16.dp)) {
            HqPillButton(if (saved) "Saved" else "Save recipe", enabled = name.isNotBlank() && ingredients.isNotEmpty() && yieldG > 0 && !saved, modifier = Modifier.fillMaxWidth(), onClick = {
                val n = servings.toIntOrNull()?.coerceAtLeast(1) ?: 1
                val withEnergy = if ((totals[Nutrients.ENERGY] ?: 0.0) > 0) totals else totals + (Nutrients.ENERGY to Nutrients.energyFromMacros(totals))
                val per100 = withEnergy.mapValues { it.value * 100.0 / yieldG }
                scope.launch {
                    db.foods().insert(
                        FoodEntity(
                            name = name.trim(), source = "recipe", servingG = yieldG / n, servingLabel = if (n > 1) "1 of $n servings" else "whole recipe",
                            per100 = Nutrients.encode(per100), createdMs = System.currentTimeMillis(),
                        ),
                    )
                    saved = true
                }
            })
        }
    }
}

/** Every nutrient for the day, grouped, with your targets where you have set them. */
@Composable
fun NutrientSummaryScreen(db: HqFitDatabase, date: String, onBack: () -> Unit) {
    val c = HqTheme.colors
    val data by produceState<Triple<List<NutrientEntity>, Map<String, Double>, Map<String, TargetEntity>>?>(null, date) {
        val totals = Diary.totals(db.diary().day(date))
        value = Triple(db.nutrients().all(), totals, db.targets().all().associateBy { it.key })
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { BackBar("Nutrients, $date", onBack) }
        val d = data
        if (d == null) { uk.woozy.fit.designsystem.SkeletonBlock(c.track, lines = 8); return@Column }
        val (nutrients, totals, targets) = d
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (totals.isEmpty()) HqText("Nothing logged for this day.", 14.sp, color = c.muted)
            nutrients.groupBy { it.grp }.forEach { (group, rows) ->
                val visible = rows.filter { (totals[it.key] ?: 0.0) > 0 || targets.containsKey(it.key) }
                if (visible.isNotEmpty()) {
                    SectionLabel(group)
                    HqCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            visible.forEach { n ->
                                val v = if (n.key == "carbs") Nutrients.netCarbs(totals) else totals[n.key] ?: 0.0
                                val t = targets[n.key]
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(Modifier.fillMaxWidth()) {
                                        HqText(if (n.key == "carbs") "Net carbs" else n.name, 14.sp, modifier = Modifier.weight(1f))
                                        HqText(
                                            NumberEntry.format(round1(v)) + (t?.let { " / ${NumberEntry.format(round1(it.value))}" } ?: "") + " ${n.unit}" + (if (t?.kind == "max") " max" else ""),
                                            14.sp, color = c.muted,
                                        )
                                    }
                                    if (t != null && t.value > 0) HqProgressBar((v / t.value).toFloat().coerceIn(0f, 1f), if (t.kind == "max" && v > t.value) c.fat else c.primary)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Edit the targets the rest of the app reads. Leave a nutrient empty for no target. */
@Composable
fun TargetsEditorScreen(db: HqFitDatabase, onBack: () -> Unit) {
    val c = HqTheme.colors
    val scope = rememberCoroutineScope()
    val loaded by produceState<Pair<List<NutrientEntity>, Map<String, TargetEntity>>?>(null) {
        value = db.nutrients().all() to db.targets().all().associateBy { it.key }
    }
    val base = loaded
    if (base == null) { uk.woozy.fit.designsystem.SkeletonBlock(c.track, Modifier.statusBarsPadding(), lines = 8); return }
    val (nutrients, existing) = base
    val values = remember { mutableStateOf(existing.mapValues { NumberEntry.format(it.value.value) }) }
    val kinds = remember { mutableStateOf(existing.mapValues { it.value.kind }) }
    var saved by remember { mutableStateOf(false) }
    val core = listOf("energy" to "Energy (kcal)", "protein" to "Protein (g)", "carbs" to "Net carbs (g)", "fat" to "Fat (g)", "steps" to "Steps", "water_glasses" to "Water glasses a day", "water_glass_ml" to "Millilitres per glass")
    val coreKeys = core.map { it.first }.toSet()

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { BackBar("Targets", onBack) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Daily")
            core.forEach { (k, label) -> NumberField(label, values.value[k].orEmpty()) { values.value = values.value + (k to it); saved = false } }
            SectionLabel("Other nutrients (optional)")
            nutrients.filter { it.key !in coreKeys && it.key != "water" }.forEach { n ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    NumberField("${n.name} (${n.unit})", values.value[n.key].orEmpty()) { values.value = values.value + (n.key to it); saved = false }
                    if (!values.value[n.key].isNullOrBlank()) {
                        val max = kinds.value[n.key] == "max"
                        SegmentedChoice(listOf("Aim for", "Stay under"), if (max) 1 else 0, onSelect = { kinds.value = kinds.value + (n.key to if (it == 1) "max" else "goal"); saved = false })
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Column(Modifier.padding(16.dp)) {
            HqPillButton(if (saved) "Saved" else "Save targets", modifier = Modifier.fillMaxWidth(), onClick = {
                scope.launch {
                    (core.map { it.first } + nutrients.map { it.key }).distinct().forEach { k ->
                        val v = values.value[k]?.toDoubleOrNull()
                        if (v != null && v > 0) db.targets().upsert(TargetEntity(k, v, kinds.value[k] ?: "goal")) else if (k !in coreKeys) db.targets().delete(k)
                    }
                    saved = true
                }
            })
        }
    }
}
