package uk.woozy.fit.data.db

/**
 * Ready-made plans (GymMane style): classic programmes built from exercises already in the library. Adding one makes
 * its workouts in Programmes, named "Plan: Day", ready to schedule or change. Exercise names are catalogue names.
 */
object ReadyPlans {
    data class Item(val exercise: String, val sets: Int, val repsMin: Int?, val repsMax: Int?, val seconds: Int? = null, val rest: Int = 90)
    data class Day(val name: String, val items: List<Item>)
    data class Plan(val name: String, val about: String, val days: List<Day>)

    private fun i(name: String, sets: Int, lo: Int, hi: Int, rest: Int = 90) = Item(name, sets, lo, hi, rest = rest)

    val all = listOf(
        Plan(
            "Full Body", "Two full-body days, alternated three times a week. The one to start with.",
            listOf(
                Day("A", listOf(i("barbell full squat", 3, 6, 10, 150), i("barbell bench press", 3, 6, 10, 150), i("barbell bent over row", 3, 8, 12), i("dumbbell lateral raise", 3, 12, 15, 60), Item("front plank", 3, null, null, seconds = 60, rest = 60))),
                Day("B", listOf(i("barbell deadlift", 3, 5, 5, 180), i("dumbbell seated shoulder press", 3, 8, 12), i("cable pulldown", 3, 8, 12), i("dumbbell incline bench press", 3, 8, 12), i("dumbbell hammer curl", 3, 10, 12, 60))),
            ),
        ),
        Plan(
            "Push Pull Legs", "Push, pull and legs. Three or six days a week.",
            listOf(
                Day("Push", listOf(i("barbell bench press", 4, 6, 10, 150), i("dumbbell incline bench press", 3, 8, 12), i("barbell standing military press", 3, 6, 10, 120), i("dumbbell lateral raise", 3, 12, 15, 60), i("cable pushdown", 3, 10, 15, 60))),
                Day("Pull", listOf(i("barbell deadlift", 3, 5, 5, 180), i("pull-up", 3, 6, 10, 120), i("cable seated row", 3, 8, 12), i("cable rear delt row (with rope)", 3, 12, 15, 60), i("barbell curl", 3, 8, 12, 60), i("dumbbell hammer curl", 2, 10, 12, 60))),
                Day("Legs", listOf(i("barbell full squat", 4, 6, 10, 150), i("barbell romanian deadlift", 3, 8, 12, 120), i("sled 45° leg press", 3, 10, 15), i("lever lying leg curl", 3, 10, 15, 60), i("lever standing calf raise", 4, 10, 15, 60))),
            ),
        ),
        Plan(
            "Upper Lower", "Upper and lower body, four days a week.",
            listOf(
                Day("Upper", listOf(i("barbell bench press", 4, 6, 10, 150), i("barbell bent over row", 4, 6, 10, 120), i("dumbbell seated shoulder press", 3, 8, 12), i("cable pulldown", 3, 8, 12), i("barbell curl", 2, 10, 12, 60), i("cable pushdown", 2, 10, 12, 60))),
                Day("Lower", listOf(i("barbell full squat", 4, 6, 10, 150), i("barbell romanian deadlift", 3, 8, 10, 120), i("dumbbell lunge", 3, 10, 12), i("lever leg extension", 3, 12, 15, 60), i("lever lying leg curl", 3, 12, 15, 60), i("lever standing calf raise", 4, 12, 15, 60))),
            ),
        ),
        Plan(
            "StrongLifts 5×5", "Three big lifts a session, five sets of five, adding weight each time.",
            listOf(
                Day("A", listOf(i("barbell full squat", 5, 5, 5, 180), i("barbell bench press", 5, 5, 5, 180), i("barbell bent over row", 5, 5, 5, 180))),
                Day("B", listOf(i("barbell full squat", 5, 5, 5, 180), i("barbell standing military press", 5, 5, 5, 180), i("barbell deadlift", 1, 5, 5, 180))),
            ),
        ),
    )

    /** Makes the plan's workouts. Returns how many were made; exercises missing from the library are skipped. */
    suspend fun add(db: HqFitDatabase, plan: Plan): Int {
        var made = 0
        plan.days.forEach { day ->
            val rows = day.items.mapNotNull { item -> db.exercises().findByName(item.exercise)?.let { it to item } }
            if (rows.isEmpty()) return@forEach
            val tid = db.templates().insert(WorkoutTemplateEntity(name = "${plan.name}: ${day.name}"))
            db.templates().insertItems(
                rows.mapIndexed { pos, (ex, item) ->
                    TemplateItemEntity(
                        templateId = tid, section = "Main", position = pos, exerciseId = ex.id, sets = item.sets,
                        repsMin = item.repsMin, repsMax = item.repsMax, durationSec = item.seconds, weightKg = null,
                        restSec = item.rest, supersetGroup = null, note = "",
                    )
                },
            )
            made++
        }
        return made
    }
}
