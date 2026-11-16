package uk.woozy.fit.ui

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.woozy.fit.data.db.HqFitDatabase
import uk.woozy.fit.data.imports.EverfitImport
import uk.woozy.fit.designsystem.HqPillButton
import uk.woozy.fit.designsystem.HqText
import uk.woozy.fit.designsystem.HqTheme
import uk.woozy.fit.designsystem.SegmentedChoice
import uk.woozy.fit.designsystem.formatWhole
import uk.woozy.fit.model.NumberEntry
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A training report for a period (GymMane's Report): totals, weight change and every workout, saved as a PDF. */
object TrainingReport {
    data class Row(val date: LocalDate, val name: String, val sets: Int, val volumeKg: Double, val minutes: Long?)
    data class Data(
        val from: LocalDate, val to: LocalDate, val rows: List<Row>,
        val weightStart: Double?, val weightEnd: Double?, val sleep: Double?, val energy: Double?,
    ) {
        val sets get() = rows.sumOf { it.sets }
        val volume get() = rows.sumOf { it.volumeKg }
        val minutes get() = rows.sumOf { it.minutes ?: 0 }
    }

    suspend fun build(db: HqFitDatabase, days: Int): Data {
        val zone = ZoneId.systemDefault()
        val to = LocalDate.now()
        val from = to.minusDays(days.toLong() - 1)
        val fromMs = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val sessions = db.sessions().doneSince(fromMs)
        val rows = sessions.map { s ->
            val sets = db.sessions().sets(s.id).filter { it.done && it.setType != "warmup" }
            Row(
                Instant.ofEpochMilli(s.startedMs).atZone(zone).toLocalDate(), s.name, sets.size,
                sets.filter { it.durationSec == null }.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) },
                if (s.scheduledId == EverfitImport.MARKER) null else ((s.endedMs ?: s.startedMs) - s.startedMs) / 60_000,
            )
        }.sortedBy { it.date }
        val weights = db.weights().since(fromMs).sortedBy { it.epochMs }
        val rated = sessions.filter { it.sleep != null || it.energy != null }
        return Data(
            from, to, rows, weights.firstOrNull()?.kg, weights.lastOrNull()?.kg,
            rated.mapNotNull { it.sleep }.takeIf { it.isNotEmpty() }?.average(),
            rated.mapNotNull { it.energy }.takeIf { it.isNotEmpty() }?.average(),
        )
    }

    /** Draws the report onto A4 pages. */
    fun write(d: Data, out: OutputStream) {
        val doc = PdfDocument()
        val w = 595; val h = 842; val m = 48f
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 24f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val head = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = 0xFF6B675F.toInt() }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f }
        val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 20f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val line = Paint().apply { color = 0xFFDDDAD2.toInt(); strokeWidth = 0.6f }
        val fmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)
        val day = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)
        var pageNo = 0
        var page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, ++pageNo).create())
        var cv = page.canvas
        var y = m + 10
        cv.drawText("Woozy Fit training report", m, y, title); y += 22
        cv.drawText("${d.from.format(fmt)} to ${d.to.format(fmt)}", m, y, body); y += 34
        val tiles = listOf(
            "WORKOUTS" to "${d.rows.size}", "SETS" to "${d.sets}",
            "VOLUME" to if (d.volume >= 1000) "${NumberEntry.format(Math.round(d.volume / 100.0) / 10.0)} t" else "${formatWhole(d.volume)} kg",
            "TIME" to "${d.minutes / 60}h ${d.minutes % 60}m",
        )
        val col = (w - 2 * m) / tiles.size
        tiles.forEachIndexed { i, (k, v) -> cv.drawText(k, m + i * col, y, head); cv.drawText(v, m + i * col, y + 24, big) }
        y += 52
        val extras = listOfNotNull(
            if (d.weightStart != null && d.weightEnd != null) "Weight ${NumberEntry.format(d.weightStart)} kg to ${NumberEntry.format(d.weightEnd)} kg (" +
                (if (d.weightEnd - d.weightStart >= 0) "+" else "") + NumberEntry.format(Math.round((d.weightEnd - d.weightStart) * 10) / 10.0) + " kg)" else null,
            d.sleep?.let { "Average sleep rating ${NumberEntry.format(Math.round(it * 10) / 10.0)} of 5" },
            d.energy?.let { "Average energy rating ${NumberEntry.format(Math.round(it * 10) / 10.0)} of 5" },
        )
        extras.forEach { cv.drawText(it, m, y, body); y += 16 }
        y += 16
        fun header() {
            cv.drawText("DATE", m, y, head); cv.drawText("WORKOUT", m + 100, y, head)
            cv.drawText("SETS", m + 340, y, head); cv.drawText("VOLUME", m + 390, y, head); cv.drawText("MIN", m + 470, y, head)
            y += 8; cv.drawLine(m, y, w - m, y, line); y += 16
        }
        header()
        d.rows.forEach { r ->
            if (y > h - m) {
                doc.finishPage(page)
                page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, ++pageNo).create()); cv = page.canvas; y = m + 10
                header()
            }
            cv.drawText(r.date.format(day), m, y, body)
            cv.drawText(r.name.take(40), m + 100, y, body)
            cv.drawText("${r.sets}", m + 340, y, body)
            cv.drawText(if (r.volumeKg > 0) "${formatWhole(r.volumeKg)} kg" else "-", m + 390, y, body)
            cv.drawText(r.minutes?.toString() ?: "-", m + 470, y, body)
            y += 18
        }
        if (d.rows.isEmpty()) cv.drawText("No workouts in this period.", m, y, body)
        doc.finishPage(page)
        doc.writeTo(out)
        doc.close()
    }
}

/** Pick a period and save the report as a PDF wherever you like (Drive, Downloads, ...). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportSheet(db: HqFitDatabase, onDismiss: () -> Unit) {
    val c = HqTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ranges = listOf(30, 90, 365)
    var pick by remember { mutableStateOf(0) }
    val data by produceState<TrainingReport.Data?>(null, pick) { value = TrainingReport.build(db, ranges[pick]) }
    var saved by remember { mutableStateOf<String?>(null) }
    val save = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val d = data
        if (uri != null && d != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openOutputStream(uri)!!.use { TrainingReport.write(d, it) } }.isSuccess }
            saved = if (ok) "Saved" else "Could not save the report"
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.card) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HqText("Report", 20.sp, FontWeight.SemiBold)
            HqText("Your training for a period as a PDF: totals, weight change and every workout.", 13.sp, color = c.muted)
            SegmentedChoice(listOf("30 days", "90 days", "1 year"), pick, onSelect = { pick = it; saved = null })
            data?.let { d ->
                Row(Modifier.fillMaxWidth()) {
                    listOf("Workouts" to "${d.rows.size}", "Sets" to "${d.sets}", "Volume" to "${formatWhole(d.volume)} kg").forEach { (k, v) ->
                        Column(Modifier.weight(1f)) { HqText(v, 18.sp, FontWeight.Bold); HqText(k, 12.sp, color = c.muted) }
                    }
                }
            }
            HqPillButton("Save PDF", onClick = {
                save.launch("Woozy Fit report ${LocalDate.now()}.pdf")
            }, enabled = data != null, modifier = Modifier.fillMaxWidth())
            saved?.let { HqText(it, 13.sp, color = c.muted) }
        }
    }
}
