package uk.woozy.fit.data.db

/**
 * Ready-made plans (GymMane style): classic programmes built from exercises already in the library. Adding one makes
 * its workouts in Programmes, named "Plan: Day", ready to schedule or change. Exercise names are catalogue names.
 */
object ReadyPlans {
    data class Item(val exercise: String, val sets: Int, val repsMin: Int?, val repsMax: Int?, val seconds: Int? = null, val rest: Int = 90)
    data class Day(val name: String, val items: List<Item>)
    data class Plan(val name: String, val about: String, val days: List<Day>, val group: String = "Classics")

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
        // Modern, evidence-led plans in the style of today's science-based coaches: fewer hard sets taken close to
        // failure (1-2 reps left), lengthened-position exercises, rep ranges with double progression. Our own plans,
        // not copies of anyone's paid programme.
        Plan(
            "Science Full Body", "Three full-body days, about an hour. Two or three hard sets each, stop with 1-2 reps left. Add weight once every set hits the top of the range.",
            listOf(
                Day("A", listOf(i("barbell full squat", 3, 6, 8, 180), i("dumbbell incline bench press", 3, 8, 10, 120), i("cable pulldown", 3, 8, 12), i("barbell romanian deadlift", 2, 8, 10, 120), i("dumbbell lateral raise", 3, 12, 20, 60), i("cable overhead triceps extension (rope attachment)", 2, 10, 15, 60))),
                Day("B", listOf(i("sled 45° leg press", 3, 10, 15, 120), i("barbell bench press", 3, 6, 8, 150), i("cable seated row", 3, 8, 12), i("lever seated leg curl", 3, 10, 15, 60), i("dumbbell incline curl", 2, 10, 15, 60), i("cable kneeling crunch", 2, 10, 15, 60))),
                Day("C", listOf(i("dumbbell bulgarian split squat", 2, 8, 12, 90), i("dumbbell seated shoulder press", 3, 8, 10, 120), i("pull-up", 3, 6, 10, 120), i("cable middle fly", 2, 12, 15, 60), i("lever leg extension", 2, 12, 15, 60), i("dumbbell hammer curl", 2, 10, 12, 60), i("lever seated calf raise", 3, 10, 15, 60))),
            ),
            group = "Modern hypertrophy",
        ),
        Plan(
            "Upper Lower Hypertrophy", "Four days: a heavier upper and lower, then a lighter pump upper and lower. Built for muscle with strength on the side.",
            listOf(
                Day("Upper Heavy", listOf(i("barbell bench press", 3, 5, 8, 180), i("barbell bent over row", 3, 6, 8, 150), i("dumbbell seated shoulder press", 2, 8, 10, 120), i("pull-up", 2, 6, 10, 120), i("ez barbell preacher curl", 2, 8, 12, 60), i("ez barbell lying triceps extension", 2, 8, 12, 60))),
                Day("Lower Heavy", listOf(i("barbell full squat", 3, 5, 8, 180), i("barbell romanian deadlift", 3, 6, 10, 150), i("sled 45° leg press", 2, 10, 12, 120), i("lever seated leg curl", 2, 10, 12, 60), i("lever standing calf raise", 3, 8, 12, 60))),
                Day("Upper Pump", listOf(i("dumbbell incline bench press", 3, 10, 12, 90), i("cable seated row", 3, 10, 12, 90), i("cable middle fly", 2, 12, 15, 60), i("cable pulldown", 2, 10, 15, 90), i("cable lateral raise", 3, 12, 20, 45), i("dumbbell incline curl", 2, 12, 15, 45), i("cable pushdown", 2, 12, 15, 45))),
                Day("Lower Pump", listOf(i("dumbbell bulgarian split squat", 3, 10, 12, 90), i("barbell hip thrust", 3, 8, 12, 90), i("lever leg extension", 3, 12, 15, 60), i("lever lying leg curl", 3, 12, 15, 60), i("lever seated calf raise", 3, 12, 15, 45), i("cable kneeling crunch", 3, 10, 15, 45))),
            ),
            group = "Modern hypertrophy",
        ),
        Plan(
            "Push Pull Legs Arms", "Four days, or five with an extra push or pull. A dedicated arms and shoulders day for the parts that lag.",
            listOf(
                Day("Push", listOf(i("barbell incline bench press", 3, 6, 10, 150), i("lever chest press", 2, 8, 12, 120), i("dumbbell seated shoulder press", 2, 8, 12, 120), i("cable middle fly", 2, 12, 15, 60), i("cable lateral raise", 3, 12, 20, 45), i("cable overhead triceps extension (rope attachment)", 2, 10, 15, 60))),
                Day("Pull", listOf(i("pull-up", 3, 6, 10, 150), i("lever t bar row", 3, 8, 12, 120), i("cable straight arm pulldown", 2, 12, 15, 60), i("dumbbell rear lateral raise", 3, 12, 20, 45), i("dumbbell shrug", 2, 10, 15, 60), i("barbell curl", 2, 8, 12, 60))),
                Day("Legs", listOf(i("sled hack squat", 3, 6, 10, 180), i("barbell romanian deadlift", 3, 8, 10, 150), i("lever leg extension", 2, 12, 15, 60), i("lever seated leg curl", 3, 10, 15, 60), i("lever seated hip abduction", 2, 12, 20, 45), i("lever standing calf raise", 3, 10, 15, 60))),
                Day("Arms and Shoulders", listOf(i("dumbbell incline curl", 3, 10, 12, 60), i("ez barbell lying triceps extension", 3, 8, 12, 60), i("dumbbell hammer curl", 2, 10, 15, 60), i("cable pushdown", 2, 12, 15, 45), i("cable lateral raise", 4, 12, 20, 45), i("dumbbell rear lateral raise", 2, 15, 20, 45))),
            ),
            group = "Modern hypertrophy",
        ),
        Plan(
            "Lean Cut 3-Day", "For a cut: keep the heavy work so you hold your strength, keep the volume low so you recover on fewer calories. 45 to 60 minutes.",
            listOf(
                Day("A", listOf(i("barbell full squat", 3, 4, 6, 180), i("barbell bench press", 3, 4, 6, 180), i("cable seated row", 2, 8, 12, 90), i("dumbbell lateral raise", 2, 12, 15, 45))),
                Day("B", listOf(i("barbell deadlift", 2, 3, 5, 210), i("dumbbell seated shoulder press", 3, 6, 8, 120), i("pull-up", 3, 5, 8, 120), i("dumbbell incline curl", 2, 10, 12, 45))),
                Day("C", listOf(i("sled 45° leg press", 3, 8, 12, 120), i("dumbbell incline bench press", 3, 6, 10, 120), i("barbell bent over row", 3, 6, 8, 120), i("cable pushdown", 2, 10, 15, 45))),
            ),
            group = "Modern hypertrophy",
        ),
        Plan(
            "Minimal 2-Day", "Two 45-minute sessions for busy weeks. Big lifts, then the bits that matter. Something beats nothing.",
            listOf(
                Day("1", listOf(i("barbell full squat", 3, 5, 8, 150), i("barbell bench press", 3, 5, 8, 150), i("cable pulldown", 3, 8, 12, 90), i("dumbbell lateral raise", 2, 12, 15, 45))),
                Day("2", listOf(i("barbell romanian deadlift", 3, 6, 10, 150), i("dumbbell seated shoulder press", 3, 8, 10, 120), i("cable seated row", 3, 8, 12, 90), i("dumbbell hammer curl", 2, 10, 12, 45))),
            ),
            group = "Modern hypertrophy",
        ),
        Plan(
            "Joint Friendly Full Body", "Machines, cables and neutral grips that go easier on sore shoulders and knees, two days a week. Still progressive.",
            listOf(
                Day("A", listOf(i("sled 45° leg press", 3, 10, 15, 120), i("lever chest press", 3, 8, 12, 120), i("cable pulldown", 3, 8, 12), i("lever seated leg curl", 3, 10, 15, 60), i("cable lateral raise", 2, 12, 20, 45), i("cable pushdown", 2, 12, 15, 45))),
                Day("B", listOf(i("barbell hip thrust", 3, 8, 12, 120), i("landmine press", 3, 8, 12, 90), i("cable seated row", 3, 8, 12), i("lever seated hip abduction", 2, 12, 20, 45), i("dumbbell hammer curl", 2, 10, 12, 45), Item("front plank", 2, null, null, seconds = 45, rest = 45))),
            ),
            group = "Modern hypertrophy",
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
