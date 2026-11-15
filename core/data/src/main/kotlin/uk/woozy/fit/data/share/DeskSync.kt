package uk.woozy.fit.data.share

import org.json.JSONArray
import org.json.JSONObject
import uk.woozy.fit.data.db.Diary
import uk.woozy.fit.data.db.ExerciseEntity
import uk.woozy.fit.data.db.FoodEntity
import uk.woozy.fit.data.db.WaterEntryEntity
import uk.woozy.fit.model.Meal
import uk.woozy.fit.model.Nutrients
import uk.woozy.fit.data.db.HqFitDatabase
import uk.woozy.fit.data.db.MetaEntity
import uk.woozy.fit.data.db.ScheduledWorkoutEntity
import uk.woozy.fit.data.db.SetLogEntity
import uk.woozy.fit.data.db.TemplateItemEntity
import uk.woozy.fit.data.db.WorkoutSessionEntity
import uk.woozy.fit.data.db.WorkoutTemplateEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Two-way bridge with the HQ desktop page, carried by HQ through Notion.
 *
 * [export] describes routines, the calendar and recent training so the desktop can edit them.
 * [apply] runs the desktop's queued changes. Every op has an id; applied ids are remembered in `meta`
 * so a change that arrives twice is only applied once. The phone stays the master copy.
 *
 * Exercises travel by name (the bundled catalogue names), so the desktop never depends on database ids;
 * a name that is not in the library becomes a custom exercise.
 */
object DeskSync {
    private const val APPLIED_KEY = "desk_applied"
    private const val KEEP_APPLIED = 400
    private const val TMP_KEY = "desk_tmp"

    private suspend fun tmpMap(db: HqFitDatabase): HashMap<String, Long> {
        val out = HashMap<String, Long>()
        db.meta().get(TMP_KEY)?.let { raw -> runCatching { val o = JSONObject(raw); o.keys().forEach { out[it] = o.getLong(it) } } }
        return out
    }

    suspend fun export(db: HqFitDatabase, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): JSONObject {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val out = JSONObject().put("v", 1).put("at", nowMs)
        val usedIds = LinkedHashSet<Long>()

        val tpls = JSONArray()
        db.templates().all().forEach { t ->
            val items = JSONArray()
            db.templates().items(t.id).forEach { i ->
                usedIds += i.exerciseId
                items.put(JSONObject().put("x", i.exerciseName).put("e", i.exerciseId).put("s", i.section).put("n", i.sets)
                    .put("rmin", i.repsMin ?: JSONObject.NULL).put("rmax", i.repsMax ?: JSONObject.NULL)
                    .put("dur", i.durationSec ?: JSONObject.NULL).put("w", i.weightKg ?: JSONObject.NULL)
                    .put("rest", i.restSec).put("ss", i.supersetGroup ?: JSONObject.NULL).put("note", i.note))
            }
            tpls.put(JSONObject().put("id", t.id).put("name", t.name).put("focus", t.focus).put("items", items))
        }
        out.put("templates", tpls)

        val sch = JSONArray()
        db.schedule().between(today.minusDays(28).toString(), today.plusDays(84).toString()).forEach { s ->
            sch.put(JSONObject().put("id", s.id).put("t", s.templateId).put("name", s.templateName).put("date", s.date).put("status", s.status))
        }
        out.put("schedule", sch)

        val from = today.minusDays(56).atStartOfDay(zone).toInstant().toEpochMilli()
        val ses = JSONArray()
        db.sessions().doneSince(from).sortedByDescending { it.startedMs }.forEach { s ->
            val sets = db.sessions().sets(s.id).filter { it.done }
            sets.forEach { usedIds += it.exerciseId }
            val vol = sets.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }
            ses.put(JSONObject().put("id", s.id).put("name", s.name).put("start", s.startedMs).put("end", s.endedMs ?: JSONObject.NULL)
                .put("feeling", s.feeling ?: JSONObject.NULL).put("sets", sets.size).put("vol", Math.round(vol))
                .put("ex", JSONArray(sets.map { it.exerciseName }.distinct())))
        }
        out.put("sessions", ses)

        // Last time's sets per exercise (by set number), for the desktop logger's "previous" column.
        val last = JSONObject()
        usedIds.forEach { id ->
            val row = JSONArray()
            for (n in 1..8) {
                val p = db.sessions().previous(id, n, -1L) ?: break
                row.put(JSONArray().put(p.weightKg ?: JSONObject.NULL).put(p.reps ?: JSONObject.NULL))
            }
            if (row.length() > 0) {
                val name = db.exercises().findById(id)?.name ?: return@forEach
                last.put(name, row)
            }
        }
        out.put("last", last)
        out.put("applied", JSONArray(appliedIds(db)))
        out.put("tmp", JSONObject().also { o -> tmpMap(db).forEach { (k, v) -> o.put(k, v) } })
        db.sessions().active()?.let { out.put("active", JSONObject().put("name", it.name).put("start", it.startedMs)) }
        out.put("nutrition", nutrition(db, nowMs, today, zone))
        return out
    }

    private val KEYS = listOf(Nutrients.ENERGY, Nutrients.PROTEIN, Nutrients.CARBS, Nutrients.FIBRE, Nutrients.FAT, Nutrients.SUGARS, Nutrients.SAT_FAT, Nutrients.SODIUM)
    private fun slim(per: String): String = Nutrients.decode(per).filterKeys { it in KEYS }.let { Nutrients.encode(it) }

    /** Diary for the last 14 days, targets, water, and your own and recent foods for the desktop food log. */
    private suspend fun nutrition(db: HqFitDatabase, nowMs: Long, today: LocalDate, zone: ZoneId): JSONObject {
        val o = JSONObject()
        val sum = HqSummaryBuilder.build(db, nowMs, zone)
        o.put("targets", JSONObject().put("kcal", sum.kcalTarget ?: JSONObject.NULL).put("protein", sum.proteinTarget ?: JSONObject.NULL)
            .put("netCarbs", sum.netCarbsTarget ?: JSONObject.NULL).put("fat", sum.fatTarget ?: JSONObject.NULL)
            .put("water", db.targets().get("water")?.value ?: 2500.0))
        val from = today.minusDays(13)
        val days = JSONObject()
        db.diary().between(from.toString(), today.toString()).forEach { e ->
            val arr = days.optJSONArray(e.date) ?: JSONArray().also { days.put(e.date, it) }
            arr.put(JSONObject().put("id", e.id).put("meal", e.meal).put("name", e.name).put("g", e.grams ?: JSONObject.NULL)
                .put("f", e.foodId ?: JSONObject.NULL).put("n", slim(e.nutrients)).put("rep", e.recurringId != null))
        }
        o.put("diary", days)
        val water = JSONObject()
        for (i in 0..13) {
            val d = today.minusDays(i.toLong())
            val a = d.atStartOfDay(zone).toInstant().toEpochMilli(); val b = d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val ml = db.water().totalBetween(a, b); if (ml > 0) water.put(d.toString(), ml)
        }
        o.put("water", water)
        val foods = LinkedHashMap<Long, FoodEntity>()
        db.foods().recentlyLogged(150).forEach { foods[it.id] = it }
        db.foods().own(150).forEach { foods.putIfAbsent(it.id, it) }
        o.put("foods", JSONArray(foods.values.map { f ->
            JSONObject().put("id", f.id).put("name", f.name).put("brand", f.brand).put("src", f.source)
                .put("sg", f.servingG ?: JSONObject.NULL).put("sl", f.servingLabel).put("n", slim(f.per100))
        }))
        return o
    }

    private suspend fun appliedIds(db: HqFitDatabase): List<String> {
        val raw = db.meta().get(APPLIED_KEY) ?: return emptyList()
        return runCatching { JSONArray(raw).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrDefault(emptyList())
    }

    /** Applies desktop ops. Returns `{applied: [...], errors: {opId: message}}`. */
    suspend fun apply(db: HqFitDatabase, opsJson: String, nowMs: Long): JSONObject {
        val ops = JSONArray(opsJson)
        val done = appliedIds(db).toMutableList()
        val seen = done.toHashSet()
        val tmpIds = tmpMap(db)
        val applied = JSONArray()
        val errors = JSONObject()
        for (k in 0 until ops.length()) {
            val op = ops.getJSONObject(k)
            val id = op.optString("id")
            if (id.isEmpty() || id in seen) continue
            try {
                when (op.optString("op")) {
                    "tpl.save" -> saveTemplate(db, op, tmpIds)
                    "tpl.delete" -> {
                        val tid = templateId(op, "t", tmpIds) ?: error("unknown routine")
                        db.schedule().deletePlanned(LocalDate.now().toString(), listOf(tid))
                        db.templates().delete(tid)
                    }
                    "sch.add" -> {
                        val tid = templateId(op, "t", tmpIds) ?: error("unknown routine")
                        val t = db.templates().get(tid) ?: error("routine not found")
                        val date = op.getString("date")
                        db.schedule().insert(ScheduledWorkoutEntity(programmeId = null, templateId = tid, templateName = t.name, date = date, originalDate = date))
                    }
                    "sch.move" -> db.schedule().move(op.getLong("sid"), op.getString("date"))
                    "sch.remove" -> db.schedule().deletePlannedById(op.getLong("sid"))
                    "ses.log" -> logSession(db, op, tmpIds)
                    "food.log" -> {
                        val food = food(db, op, nowMs)
                        Diary.log(db, food, op.getDouble("g"), op.getString("date"), Meal.fromKey(op.optString("meal")), nowMs)
                    }
                    "food.quick" -> {
                        val n = LinkedHashMap<String, Double>()
                        listOf(Nutrients.ENERGY, Nutrients.PROTEIN, Nutrients.CARBS, Nutrients.FAT, Nutrients.FIBRE).forEach { k -> dblOrNull(op, k)?.let { n[k] = it } }
                        Diary.quickAdd(db, op.optString("name").ifBlank { "Quick add" }, n, op.getString("date"), Meal.fromKey(op.optString("meal")), nowMs)
                    }
                    "food.edit" -> {
                        val eid = op.getLong("eid")
                        val e = db.diary().get(eid) ?: error("entry not found")
                        Diary.changeAmount(db, eid, dblOrNull(op, "g") ?: e.grams ?: 0.0, Meal.fromKey(op.optString("meal", e.meal)))
                    }
                    "food.delete" -> db.diary().delete(op.getLong("eid"))
                    "food.create" -> food(db, JSONObject().put("food", op.getJSONObject("food").put("src", "custom")), nowMs)
                    "water.add" -> {
                        val ml = op.getInt("ml")
                        val date = LocalDate.parse(op.optString("date", LocalDate.now().toString()))
                        val zone = ZoneId.systemDefault()
                        val at = if (date == LocalDate.now()) nowMs else date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
                        if (ml > 0) db.water().insert(WaterEntryEntity(epochMs = at, ml = ml))
                        else { val a = date.atStartOfDay(zone).toInstant().toEpochMilli(); db.water().deleteLatestBetween(a, a + 86_400_000L) }
                    }
                    else -> error("unknown op")
                }
                done += id; seen += id; applied.put(id)
            } catch (e: Exception) {
                errors.put(id, (e.message ?: e.javaClass.simpleName).take(160))
                // A bad op is marked applied too, so it can never block the queue; the error is reported back.
                done += id; seen += id
            }
        }
        db.meta().put(MetaEntity(APPLIED_KEY, JSONArray(done.takeLast(KEEP_APPLIED)).toString()))
        val ids = JSONObject(); tmpIds.entries.toList().takeLast(200).forEach { (k, v) -> ids.put(k, v) }
        db.meta().put(MetaEntity(TMP_KEY, ids.toString()))
        return JSONObject().put("applied", applied).put("errors", errors).put("ids", ids)
    }

    /** A logged food: one of yours by id, else a CoFID food by name, else a new custom food from the desktop's figures. */
    private suspend fun food(db: HqFitDatabase, op: JSONObject, nowMs: Long): FoodEntity {
        if (op.has("f") && !op.isNull("f")) db.foods().get(op.getLong("f"))?.let { return it }
        val f = op.getJSONObject("food")
        val name = f.getString("name").trim()
        val src = f.optString("src", "custom")
        if (src == "cofid") db.foods().byNameSource(name, "cofid")?.let { return it }
        if (src == "custom") db.foods().byNameSource(name, "custom")?.let { if (it.per100 == f.optString("n")) return it }
        val row = FoodEntity(
            name = name, brand = f.optString("brand"), source = if (src == "cofid") "cofid" else "custom",
            servingG = dblOrNull(f, "sg"), servingLabel = f.optString("sl"), per100 = f.optString("n"), createdMs = nowMs,
        )
        return row.copy(id = db.foods().insert(row))
    }

    private fun templateId(op: JSONObject, key: String, tmp: Map<String, Long>): Long? {
        val v = op.opt(key) ?: return null
        return when (v) {
            is Number -> v.toLong()
            is String -> tmp[v] ?: v.toLongOrNull()
            else -> null
        }
    }

    private suspend fun exercise(db: HqFitDatabase, o: JSONObject): ExerciseEntity {
        val name = o.optString("x").trim()
        if (o.has("e") && !o.isNull("e")) db.exercises().findById(o.getLong("e"))?.let { if (name.isEmpty() || it.name.equals(name, true)) return it }
        require(name.isNotEmpty()) { "exercise has no name" }
        db.exercises().findByName(name)?.let { return it }
        val row = ExerciseEntity(name = name, muscle = o.optString("muscle"), equipment = o.optString("equip"), custom = true)
        return row.copy(id = db.exercises().insert(row))
    }

    private fun intOrNull(o: JSONObject, k: String): Int? = if (!o.has(k) || o.isNull(k)) null else o.optDouble(k).takeIf { !it.isNaN() }?.toInt()
    private fun dblOrNull(o: JSONObject, k: String): Double? = if (!o.has(k) || o.isNull(k)) null else o.optDouble(k).takeIf { !it.isNaN() }

    private suspend fun saveTemplate(db: HqFitDatabase, op: JSONObject, tmp: HashMap<String, Long>) {
        val name = op.getString("name").trim().ifEmpty { "Workout" }
        val focus = op.optString("focus")
        val existing = templateId(op, "t", tmp)?.takeIf { db.templates().get(it) != null }
        val tid = if (existing != null) {
            db.templates().rename(existing, name); db.templates().setFocus(existing, focus); db.templates().deleteItems(existing); existing
        } else {
            db.templates().insert(WorkoutTemplateEntity(name = name, focus = focus)).also { new -> op.optString("tmp").takeIf { it.isNotEmpty() }?.let { tmp[it] = new } }
        }
        val items = op.getJSONArray("items")
        val rows = ArrayList<TemplateItemEntity>()
        for (i in 0 until items.length()) {
            val it = items.getJSONObject(i)
            val ex = exercise(db, it)
            rows += TemplateItemEntity(
                templateId = tid, section = it.optString("s", "workout").ifEmpty { "workout" }, position = i, exerciseId = ex.id,
                sets = it.optInt("n", 3).coerceIn(1, 20), repsMin = intOrNull(it, "rmin"), repsMax = intOrNull(it, "rmax"),
                durationSec = intOrNull(it, "dur"), weightKg = dblOrNull(it, "w"), restSec = it.optInt("rest", 90).coerceIn(0, 900),
                supersetGroup = intOrNull(it, "ss"), note = it.optString("note"),
            )
        }
        db.templates().insertItems(rows)
        // Keep future planned days pointing at the new name.
        if (existing != null) {
            val today = LocalDate.now()
            db.schedule().between(today.toString(), today.plusDays(365).toString()).filter { it.templateId == tid && it.status == "planned" && it.templateName != name }
                .forEach { s -> db.schedule().deletePlannedById(s.id); db.schedule().insert(s.copy(id = 0, templateName = name)) }
        }
    }

    private suspend fun logSession(db: HqFitDatabase, op: JSONObject, tmp: Map<String, Long>) {
        val name = op.getString("name")
        val start = op.getLong("start")
        if (db.sessions().countAt(start, name) > 0) return
        val sched = if (op.has("sid") && !op.isNull("sid")) op.getLong("sid") else null
        val sid = db.sessions().insertSession(
            WorkoutSessionEntity(scheduledId = sched, templateId = templateId(op, "t", tmp), name = name, startedMs = start,
                endedMs = op.optLong("end", start), status = "done", feeling = intOrNull(op, "feeling")),
        )
        val sets = op.getJSONArray("sets")
        val rows = ArrayList<SetLogEntity>()
        for (i in 0 until sets.length()) {
            val s = sets.getJSONObject(i)
            val ex = exercise(db, s)
            rows += SetLogEntity(
                sessionId = sid, position = s.optInt("p", i), setIndex = s.optInt("i", 1), section = s.optString("s", "workout"),
                exerciseId = ex.id, exerciseName = ex.name, supersetGroup = intOrNull(s, "ss"), durationSec = intOrNull(s, "dur"),
                targetRepsMin = null, targetRepsMax = null, restSec = s.optInt("rest", 0), note = "",
                weightKg = dblOrNull(s, "w"), reps = intOrNull(s, "r"), done = true, doneAtMs = s.optLong("at", start),
            )
        }
        db.sessions().insertSets(rows)
        sched?.let { db.schedule().setStatus(it, "done") }
    }
}
