package uk.woozy.fit.ui

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        modifier = modifier.size(size).clip(RoundedCornerShape(10.dp)).background(c.track),
    )
}

/** Both stills (start and end position), the muscle and equipment, and the written steps. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailSheet(db: HqFitDatabase, name: String, onDismiss: () -> Unit) {
    val c = HqTheme.colors
    val id = rememberImageId(name)
    val entity by produceState<uk.woozy.fit.data.db.ExerciseEntity?>(null, name) { value = db.exercises().findByName(name) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.card,
    ) {
        Column(
            Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HqText(name, 20.sp, FontWeight.SemiBold)
            entity?.let { e ->
                val line = listOf(e.muscle, e.equipment).filter { it.isNotBlank() }.joinToString(" · ")
                if (line.isNotEmpty()) HqText(line, 14.sp, color = c.muted)
            }
            if (id != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExerciseMedia.urls(id).forEach { url ->
                        Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(c.track)) {
                            AsyncImage(model = url, contentDescription = "$name, start and end position", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                        }
                    }
                }
                HqText("Pictures: free-exercise-db (Unlicense). Needs a connection the first time.", 11.sp, color = c.muted)
            }
            val steps = entity?.instructions.orEmpty()
            if (steps.isNotBlank()) HqText(steps, 15.sp)
            else if (id == null) HqText("No picture or notes for this exercise yet.", 14.sp, color = c.muted)
        }
    }
}
