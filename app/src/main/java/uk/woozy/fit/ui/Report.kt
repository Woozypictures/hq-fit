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

/**
 * A training report for a period (GymMane's Report, Woozy-styled): totals, weight change, weekly volume, sets per
 * muscle, best lifts and every workout, drawn onto A4 pages and saved as a PDF.
 */
object TrainingReport {
    data class Row(val date: LocalDate, val name: String, val sets: Int, val volumeKg: Double, val minutes: Long?)
    data class Lift(val name: String, val weightKg: Double, val reps: Int, val e1rm: Double)
    data class Data(
        val from: LocalDate, val to: LocalDate, val rows: List<Row>,
        val weightStart: Double?, val weightEnd: Double?, val sleep: Double?, val energy: Double?,
        val muscles: Map<String, Double> = emptyMap(), val lifts: List<Lift> = emptyList(),
    ) {
        val sets get() = rows.sumOf { it.sets }
        val volume get() = rows.sumOf { it.volumeKg }
        val minutes get() = rows.sumOf { it.minutes ?: 0 }
        /** Volume per week (Monday start) across the period, oldest first. */
        val weeks: List<Pair<LocalDate, Double>> get() {
            val start = from.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            val n = (java.time.temporal.ChronoUnit.WEEKS.between(start, to) + 1).toInt().coerceIn(1, 53)
            return (0 until n).map { i -> val w = start.plusWeeks(i.toLong()); w to rows.filter { !it.date.isBefore(w) && it.date.isBefore(w.plusWeeks(1)) }.sumOf { it.volumeKg } }
        }
    }

    suspend fun build(db: HqFitDatabase, days: Int, secondary: Map<String, List<String>> = emptyMap()): Data {
        val zone = ZoneId.systemDefault()
        val to = LocalDate.now()
        val from = to.minusDays(days.toLong() - 1)
        val fromMs = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val sessions = db.sessions().doneSince(fromMs)
        val best = HashMap<String, Lift>()
        val rows = sessions.map { s ->
            val sets = db.sessions().sets(s.id).filter { it.done && it.setType != "warmup" }
            sets.filter { it.durationSec == null && (it.weightKg ?: 0.0) > 0 && (it.reps ?: 0) in 1..20 }.forEach { l ->
                val e = uk.woozy.fit.model.Strength.e1rm(uk.woozy.fit.model.SetPoint(l.weightKg!!, l.reps!!))
                if (e > (best[l.exerciseName]?.e1rm ?: 0.0)) best[l.exerciseName] = Lift(l.exerciseName, l.weightKg!!, l.reps!!, e)
            }
            Row(
                Instant.ofEpochMilli(s.startedMs).atZone(zone).toLocalDate(), s.name, sets.size,
                sets.filter { it.durationSec == null }.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) },
                if (s.scheduledId == EverfitImport.MARKER) null else ((s.endedMs ?: s.startedMs) - s.startedMs) / 60_000,
            )
        }.sortedBy { it.date }
        val weights = db.weights().since(fromMs).sortedBy { it.epochMs }
        val rated = sessions.filter { it.sleep != null || it.energy != null }
        val muscles = uk.woozy.fit.data.Muscles.load(db, fromMs, secondary).sets
        return Data(
            from, to, rows, weights.firstOrNull()?.kg, weights.lastOrNull()?.kg,
            rated.mapNotNull { it.sleep }.takeIf { it.isNotEmpty() }?.average(),
            rated.mapNotNull { it.energy }.takeIf { it.isNotEmpty() }?.average(),
            muscles, best.values.sortedByDescending { it.e1rm }.take(10),
        )
    }

    /** Draws the report onto A4 pages in the Woozy look: green header band, soft tiles, charts, then the tables. */
    fun write(d: Data, out: OutputStream, fonts: Pair<Typeface, Typeface> = Typeface.DEFAULT to Typeface.DEFAULT_BOLD) {
        val (reg, bold) = fonts
        val doc = PdfDocument()
        val w = 595f; val h = 842f; val m = 40f
        val ink = 0xFF141414.toInt(); val mute = 0xFF6B675F.toInt(); val green = 0xFF2D5A34.toInt()
        val panel = 0xFFF4F1EA.toInt(); val track = 0xFFE7E3DA.toInt(); val hair = 0xFFDDDAD2.toInt()
        fun p(size: Float, color: Int = ink, face: Typeface = reg, spacing: Float = 0f) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = face; letterSpacing = spacing }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fun rr(cv: android.graphics.Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, color: Int) { fill.color = color; cv.drawRoundRect(l, t, r, b, rad, rad, fill) }
        val fmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)
        val day = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)
        var pageNo = 0
        lateinit var page: PdfDocument.Page
        lateinit var cv: android.graphics.Canvas
        var y = 0f
        fun footer() {
            cv.drawText("Woozy Fit · training report", m, h - 22, p(8f, mute))
            val t = "Page $pageNo"; val fp = p(8f, mute); cv.drawText(t, w - m - fp.measureText(t), h - 22, fp)
        }
        fun newPage() {
            if (pageNo > 0) { footer(); doc.finishPage(page) }
            page = doc.startPage(PdfDocument.PageInfo.Builder(w.toInt(), h.toInt(), ++pageNo).create()); cv = page.canvas; y = m
        }
        fun need(space: Float) { if (y + space > h - 48) newPage() }
        fun section(title: String) { need(40f); y += 10; cv.drawText(title.uppercase(), m, y, p(9f, mute, bold, 0.08f)); y += 12 }

        newPage()
        // Header band.
        rr(cv, m, y, w - m, y + 92, 18f, green)
        cv.drawText("Training report", m + 20, y + 40, p(24f, 0xFFFFFFFF.toInt(), bold))
        cv.drawText("${d.from.format(fmt)} to ${d.to.format(fmt)}", m + 20, y + 62, p(11f, 0xCCFFFFFF.toInt()))
        cv.drawText("Woozy Fit", m + 20, y + 78, p(9f, 0x99FFFFFF.toInt(), bold, 0.06f))
        y += 108

        // Tiles.
        val tiles = listOf(
            "Workouts" to "${d.rows.size}", "Sets" to "${d.sets}",
            "Volume" to if (d.volume >= 1000) "${NumberEntry.format(Math.round(d.volume / 100.0) / 10.0)} t" else "${formatWhole(d.volume)} kg",
            "Time" to "${d.minutes / 60}h ${d.minutes % 60}m",
        )
        val gap = 10f; val tw = (w - 2 * m - gap * 3) / 4
        tiles.forEachIndexed { i, (k, v) ->
            val l = m + i * (tw + gap)
            rr(cv, l, y, l + tw, y + 62, 14f, panel)
            cv.drawText(v, l + 14, y + 32, p(20f, ink, bold))
            cv.drawText(k, l + 14, y + 50, p(9f, mute))
        }
        y += 76
        val extras = listOfNotNull(
            if (d.weightStart != null && d.weightEnd != null) "Weight ${NumberEntry.format(d.weightStart)} kg to ${NumberEntry.format(d.weightEnd)} kg (" +
                (if (d.weightEnd - d.weightStart >= 0) "+" else "") + NumberEntry.format(Math.round((d.weightEnd - d.weightStart) * 10) / 10.0) + " kg)" else null,
            d.sleep?.let { "Average sleep rating ${NumberEntry.format(Math.round(it * 10) / 10.0)} of 5" },
            d.energy?.let { "Average energy rating ${NumberEntry.format(Math.round(it * 10) / 10.0)} of 5" },
        )
        extras.forEach { cv.drawText(it, m, y + 4, p(10.5f)); y += 16 }
        if (extras.isNotEmpty()) y += 4

        // Weekly volume bars.
        val weeks = d.weeks
        if (weeks.any { it.second > 0 }) {
            section("Volume each week")
            val ch = 110f; val top = y; val max = weeks.maxOf { it.second }.coerceAtLeast(1.0)
            val bw = (w - 2 * m) / weeks.size
            weeks.forEachIndexed { i, (wk, v) ->
                val bh = (v / max * (ch - 18)).toFloat()
                val l = m + i * bw + bw * 0.18f; val r = m + (i + 1) * bw - bw * 0.18f
                rr(cv, l, top + ch - 14 - bh, r, top + ch - 14, 3f, if (v > 0) green else track)
                if (weeks.size <= 16 || i % 4 == 0) { val t = wk.dayOfMonth.toString(); val lp = p(7f, mute); cv.drawText(t, (l + r) / 2 - lp.measureText(t) / 2, top + ch, lp) }
            }
            y += ch + 8
        }

        // Sets per muscle.
        val ms = d.muscles.filter { it.value > 0 }.toList().sortedByDescending { it.second }
        if (ms.isNotEmpty()) {
            section("Working sets per muscle")
            val max = ms.first().second
            ms.forEach { (k, v) ->
                need(16f)
                cv.drawText(uk.woozy.fit.data.Muscles.NAMES[k] ?: k, m, y + 8, p(9.5f))
                val l = m + 90; val r = w - m - 40
                rr(cv, l, y + 1, r, y + 9, 4f, track)
                rr(cv, l, y + 1, l + ((r - l) * (v / max)).toFloat().coerceAtLeast(6f), y + 9, 4f, green)
                val t = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.UK, "%.1f", v)
                cv.drawText(t, r + 8, y + 8, p(9.5f, mute))
                y += 15
            }
            cv.drawText("A helping muscle counts half a set. Warm-ups are left out.", m, y + 6, p(8f, mute)); y += 14
        }

        // Best lifts.
        if (d.lifts.isNotEmpty()) {
            section("Best lifts")
            d.lifts.forEachIndexed { i, lift ->
                need(18f)
                if (i % 2 == 0) rr(cv, m - 6, y - 2, w - m + 6, y + 14, 6f, panel)
                cv.drawText(lift.name.take(46), m, y + 9, p(10f))
                cv.drawText("${NumberEntry.format(lift.weightKg)} kg × ${lift.reps}", m + 300, y + 9, p(10f, ink, bold))
                cv.drawText("e1RM ${formatWhole(lift.e1rm)} kg", m + 410, y + 9, p(9.5f, mute))
                y += 17
            }
        }

        // Every workout.
        section("Every workout")
        fun header() {
            val hp = p(8.5f, mute, bold, 0.06f)
            cv.drawText("DATE", m, y + 8, hp); cv.drawText("WORKOUT", m + 90, y + 8, hp)
            cv.drawText("SETS", m + 330, y + 8, hp); cv.drawText("VOLUME", m + 380, y + 8, hp); cv.drawText("MIN", m + 470, y + 8, hp)
            y += 13; fill.color = hair; cv.drawRect(m, y, w - m, y + 0.7f, fill); y += 6
        }
        header()
        d.rows.forEachIndexed { i, r ->
            if (y + 18 > h - 48) { newPage(); header() }
            if (i % 2 == 1) rr(cv, m - 6, y - 2, w - m + 6, y + 14, 6f, panel)
            cv.drawText(r.date.format(day), m, y + 9, p(10f))
            cv.drawText(r.name.take(40), m + 90, y + 9, p(10f, ink, bold))
            cv.drawText("${r.sets}", m + 330, y + 9, p(10f))
            cv.drawText(if (r.volumeKg > 0) "${formatWhole(r.volumeKg)} kg" else "-", m + 380, y + 9, p(10f))
            cv.drawText(r.minutes?.toString() ?: "-", m + 470, y + 9, p(10f))
            y += 17
        }
        if (d.rows.isEmpty()) cv.drawText("No workouts in this period.", m, y + 9, p(10f, mute))
        footer()
        doc.finishPage(page)
        doc.writeTo(out)
        doc.close()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportSheet(db: HqFitDatabase, onDismiss: () -> Unit) {
    val c = HqTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ranges = listOf(30, 90, 365)
    var pick by remember { mutableStateOf(0) }
    val data by produceState<TrainingReport.Data?>(null, pick) { value = withContext(Dispatchers.IO) { TrainingReport.build(db, ranges[pick], MuscleCatalog.get(context)) } }
    var savedUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var saved by remember { mutableStateOf<String?>(null) }
    val save = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val d = data
        if (uri != null && d != null) scope.launch {
            val fonts = runCatching {
                androidx.core.content.res.ResourcesCompat.getFont(context, uk.woozy.fit.designsystem.R.font.googlesans_regular)!! to
                    androidx.core.content.res.ResourcesCompat.getFont(context, uk.woozy.fit.designsystem.R.font.googlesans_bold)!!
            }.getOrDefault(Typeface.DEFAULT to Typeface.DEFAULT_BOLD)
            val ok = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openOutputStream(uri)!!.use { TrainingReport.write(d, it, fonts) } }.isSuccess }
            saved = if (ok) "Saved" else "Could not save the report"
            savedUri = if (ok) uri else null
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.card) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HqText("Report", 20.sp, FontWeight.SemiBold)
            HqText("Your training for a period as a PDF: totals, weight, weekly volume, sets per muscle, best lifts and every workout.", 13.sp, color = c.muted)
            SegmentedChoice(listOf("30 days", "90 days", "1 year"), pick, onSelect = { pick = it; saved = null; savedUri = null })
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
            savedUri?.let { u ->
                // Open or share the PDF straight away, so it is not lost in Downloads.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HqPillButton("Open", onClick = {
                        runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(u, "application/pdf").addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                    }, style = uk.woozy.fit.designsystem.PillStyle.Outline, modifier = Modifier.weight(1f))
                    HqPillButton("Share", onClick = {
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("application/pdf").putExtra(android.content.Intent.EXTRA_STREAM, u).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        runCatching { context.startActivity(android.content.Intent.createChooser(send, "Share report")) }
                    }, style = uk.woozy.fit.designsystem.PillStyle.Outline, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
