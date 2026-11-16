package uk.woozy.fit.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.woozy.fit.data.db.HqFitDatabase
import uk.woozy.fit.designsystem.HqText
import uk.woozy.fit.designsystem.HqTheme

/**
 * Exercise stills from free-exercise-db (Unlicense), loaded from GitHub the first time and then cached on the
 * phone by Coil. The mapping from our exercise names to its folders is assets/exercise_images.tsv
 * (tools/build_exercise_images.py). Exercises without a match simply show no picture.
 */
object ExerciseMedia {
    private const val BASE = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/"
    @Volatile private var map: Map<String, String>? = null

    fun norm(name: String): String =
        name.lowercase().replace('-', ' ').replace(Regex("[^a-z0-9 ]"), "").split(' ').filter { it.isNotEmpty() }
            .joinToString(" ") { if (it.length > 3 && it.endsWith("s") && !it.endsWith("ss")) it.dropLast(1) else it }

    fun idFor(context: Context, name: String): String? {
        val m = map ?: context.applicationContext.assets.open("exercise_images.tsv").bufferedReader().useLines { lines ->
            lines.mapNotNull { l -> l.split('\t').takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
        }.also { map = it }
        return m[norm(name)]
    }

    fun urls(id: String): List<String> = listOf("${BASE}$id/0.jpg", "${BASE}$id/1.jpg")
}

@Composable
private fun rememberImageId(name: String): String? {
    val context = LocalContext.current
    val id by produceState<String?>(null, name) { value = withContext(Dispatchers.IO) { ExerciseMedia.idFor(context, name) } }
    return id
}

/** A small square still, or nothing when the exercise has no picture. */
@Composable
fun ExerciseThumb(name: String, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    val id = rememberImageId(name) ?: return
    val c = HqTheme.colors
    AsyncImage(
        model = ExerciseMedia.urls(id)[0],
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.size(size).clip(RoundedCornerShape(12.dp)).background(c.track),
    )
}

/** The start-position still across the top of an exercise page; tap for both pictures and the steps. */
@Composable
fun ExerciseHero(name: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val id = rememberImageId(name) ?: return
    val c = HqTheme.colors
    AsyncImage(
        model = ExerciseMedia.urls(id)[0],
        contentDescription = "$name. Tap for how to do it",
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(14.dp)).background(c.track)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
    )
}

/**
 * The exercise page, all in one sheet (GymMane style): pictures, main muscle and equipment, your note, the next
 * target with its settings, how to do it as numbered steps, what it works and why, your recent history and
 * similar exercises (tap one to look at it here). Opens from a workout's picture or ⋮ menu and from the library.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailSheet(
    db: HqFitDatabase,
    name: String,
    onDismiss: () -> Unit,
    note: String? = null,
    onEditNote: (() -> Unit)? = null,
    repsMin: Int? = null,
    repsMax: Int? = null,
    /** Timed sets (cardio, stretches) have no weight target, so NEXT is left out. */
    timed: Boolean = false,
) {
    val c = HqTheme.colors
    val context = LocalContext.current
    var current by androidx.compose.runtime.saveable.rememberSaveable(name) { androidx.compose.runtime.mutableStateOf(name) }
    val isOwn = current == name
    val id = rememberImageId(current)
    val entity by produceState<uk.woozy.fit.data.db.ExerciseEntity?>(null, current) { value = db.exercises().findByName(current) }
    var settingsOpen by remember { androidx.compose.runtime.mutableStateOf(false) }
    var tick by remember { androidx.compose.runtime.mutableStateOf(0) }
    val next by produceState<uk.woozy.fit.model.Progression.Target?>(null, entity, tick) {
        val e = entity ?: return@produceState
        value = uk.woozy.fit.data.db.Coach.next(db, e.id, if (isOwn) repsMin else null, if (isOwn) repsMax else null, WorkoutPrefs.weightStep(context))
    }
    val history by produceState(emptyList<List<uk.woozy.fit.data.db.HistoryRow>>(), entity) {
        val e = entity ?: return@produceState
        value = db.sessions().exerciseHistory(e.id).groupBy { it.sessionId }.values.take(5).toList()
    }
    val similar by produceState(emptyList<uk.woozy.fit.data.db.ExerciseEntity>(), entity) {
        val e = entity ?: return@produceState
        value = if (e.muscle.isBlank()) emptyList() else db.exercises().similar(e.muscle, e.equipment, e.id, 5)
    }
    @Composable fun Label(t: String) = HqText(t, 11.sp, FontWeight.SemiBold, c.muted, letterSpacing = 0.06.em)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.card,
    ) {
        Column(
            Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!isOwn) HqText("\u2190 Back to $name", 14.sp, FontWeight.SemiBold, c.primary, Modifier.clip(RoundedCornerShape(12.dp)).clickable { current = name }.padding(vertical = 4.dp))
            HqText(current, 22.sp, FontWeight.Bold, maxLines = 3)
            if (id != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExerciseMedia.urls(id).forEach { url ->
                        Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(c.track)) {
                            AsyncImage(model = url, contentDescription = "$current, start and end position", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                        }
                    }
                }
            }
            val e = entity
            if (e != null && (e.muscle.isNotBlank() || e.equipment.isNotBlank())) Row(Modifier.fillMaxWidth()) {
                if (e.muscle.isNotBlank()) Column(Modifier.weight(1f)) { Label("MAIN MUSCLE"); HqText(e.muscle.replaceFirstChar { it.uppercase() }, 15.sp, FontWeight.Medium) }
                if (e.equipment.isNotBlank()) Column(Modifier.weight(1f)) { Label("EQUIPMENT"); HqText(e.equipment.replaceFirstChar { it.uppercase() }, 15.sp, FontWeight.Medium) }
            }
            if (isOwn && (onEditNote != null || !note.isNullOrBlank())) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.track.copy(alpha = 0.5f))
                        .then(if (onEditNote != null) Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "Edit note", onClick = onEditNote) else Modifier)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Label("YOUR NOTE")
                    HqText(note?.takeIf { it.isNotBlank() } ?: "Add a note: seat height, grip, how it felt", 15.sp, color = if (note.isNullOrBlank()) c.muted else c.ink)
                }
            }
            if (e != null && !(timed && isOwn) && !e.muscle.contains("cardio", ignoreCase = true)) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.track.copy(alpha = 0.5f))
                        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "Settings for this exercise") { settingsOpen = true }
                        .padding(14.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Label("NEXT")
                        HqText(next?.let { uk.woozy.fit.model.Progression.label(it) } ?: "Log it once to get a target", 17.sp, FontWeight.Bold)
                        next?.let { HqText(it.why, 13.sp, color = c.primary) }
                    }
                    HqText("Settings", 14.sp, FontWeight.SemiBold, c.primary)
                }
            }
            val info = ExerciseInfo.of(current, e?.instructions.orEmpty(), e?.muscle.orEmpty())
            val steps = e?.instructions.orEmpty().split(Regex("\\n+|(?<=[.!?])\\s+(?=[A-Z])")).map { it.trim() }.filter { it.length > 2 }
            if (info != null || steps.size >= 2) {
                Label("HOW TO")
                if (info != null && info.what.isNotBlank() && (steps.size < 2 || info.what != e?.instructions)) HqText(info.what, 15.sp)
                if (steps.size >= 2) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    steps.forEachIndexed { i, step ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(24.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.primary.copy(alpha = 0.14f)), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                HqText("${i + 1}", 12.sp, FontWeight.SemiBold, c.primary)
                            }
                            HqText(step, 15.sp, modifier = Modifier.weight(1f))
                        }
                    }
                }
                if (info != null && info.works.isNotBlank()) Column { Label("WORKS"); HqText(info.works, 15.sp, FontWeight.Medium) }
                if (info != null && info.why.isNotBlank()) Column { Label("WHY"); HqText(info.why, 15.sp) }
            } else if (id == null) HqText("No picture or notes for this exercise yet.", 14.sp, color = c.muted)
            if (history.isNotEmpty()) {
                Label("HISTORY")
                val fmt = java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.UK)
                history.forEach { rows ->
                    val top = rows.maxByOrNull { it.weightKg }
                    Row(Modifier.fillMaxWidth()) {
                        HqText(java.time.Instant.ofEpochMilli(rows.first().startedMs).atZone(java.time.ZoneId.systemDefault()).format(fmt), 14.sp, modifier = Modifier.weight(1f))
                        HqText("${rows.size} sets" + (top?.let { " \u00b7 ${uk.woozy.fit.model.NumberEntry.format(it.weightKg)} \u00d7 ${it.reps}" } ?: ""), 14.sp, FontWeight.Medium)
                    }
                }
            }
            if (similar.isNotEmpty()) {
                Label("SIMILAR")
                similar.forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = androidx.compose.ui.semantics.Role.Button) { current = s.name }.padding(vertical = 8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        HqText(s.name, 15.sp, FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1)
                        HqText(s.equipment.replaceFirstChar { it.uppercase() }, 13.sp, color = c.muted)
                    }
                }
            }
        }
    }
    val e = entity
    if (settingsOpen && e != null) ExerciseSettingsSheet(db, e.id, e.name, if (isOwn) repsMin else null, if (isOwn) repsMax else null) { settingsOpen = false; tick++ }
}
