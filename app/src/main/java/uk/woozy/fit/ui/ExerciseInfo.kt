package uk.woozy.fit.ui

/**
 * Short "what it is, what it works, why do it" text for the exercise picture sheet. The programme's own lifts are
 * written out here; anything else falls back to the catalogue description and muscle.
 */
object ExerciseInfo {
    data class Info(val what: String, val works: String, val why: String)

    private val notes = mapOf(
        "barbell curl" to Info("Standing curl with a straight bar, elbows pinned to your sides.", "Biceps, forearms", "Builds arm size and the pulling strength behind rows and pull-ups."),
        "barbell hip thrust" to Info("Shoulders on a bench, bar across the hips, drive the hips up to full lockout.", "Glutes, hamstrings", "Strong glutes protect the lower back and help squats, deadlifts and sprinting."),
        "cable rope overhead tricep extension" to Info("Facing away from the cable, press the rope from behind your head to straight arms.", "Triceps (long head)", "Trains the triceps at full stretch, which adds the most arm size."),
        "cable triceps pushdown" to Info("Elbows tucked, push the cable attachment down until the arms are straight.", "Triceps", "Simple, joint-friendly triceps work that carries over to every press."),
        "decline chest press machine" to Info("Seated machine press angled slightly downwards.", "Lower chest, triceps", "Lets you push the chest hard with a fixed, safe path."),
        "hack squat" to Info("Machine squat with your back on a pad and the weight on your shoulders.", "Quads, glutes", "Heavy leg work with the back supported, so the legs give out before the spine."),
        "hammer curl" to Info("Dumbbell curl with palms facing each other.", "Brachialis, forearms, biceps", "Thickens the arm and builds grip and elbow strength."),
        "hyperextension back extension" to Info("Hips on the pad, lower the body and raise it until it is in line with the legs.", "Lower back, glutes, hamstrings", "Builds a resilient lower back and better posture."),
        "leg extension" to Info("Seated, straighten the knees against the pad.", "Quads", "Isolates the quads and strengthens the knee through its full range."),
        "leg press" to Info("Seated, push the platform away with both feet.", "Quads, glutes, hamstrings", "Lets you load the legs heavily without balancing a bar."),
        "leverage chest press" to Info("Seated plate-loaded chest press.", "Chest, front shoulders, triceps", "Steady pressing strength with a guided path and little setup."),
        "lying leg curl" to Info("Face down, curl the pad towards your glutes.", "Hamstrings", "Strong hamstrings balance the quads and help protect the knees."),
        "machine fly" to Info("Seated, bring the handles together in a wide hugging arc.", "Chest", "Stretches and squeezes the chest without needing the triceps."),
        "machine lateral raise" to Info("Raise the arms out to the sides against the machine pads.", "Side delts", "Widens the shoulders with steady tension all the way up."),
        "machine assisted pull up" to Info("Pull-up with the machine taking part of your weight. More assistance makes it easier.", "Lats, biceps, upper back", "Builds towards unassisted pull-ups and a wider back."),
        "preacher curl" to Info("Upper arms on the angled pad, curl the bar or dumbbells up.", "Biceps", "Stops swinging, so the biceps do all the work, especially at the bottom."),
        "reverse machine flye" to Info("Facing the pec-deck, sweep the handles back with straight-ish arms.", "Rear delts, upper back", "Balances all the pressing and helps shoulder health and posture."),
        "seated cable row" to Info("Seated, pull the handle to your stomach and squeeze the shoulder blades.", "Mid back, lats, biceps", "Builds a thick upper back and better posture."),
        "wide grip lat pulldown" to Info("Pull a wide bar down to the top of the chest.", "Lats, upper back, biceps", "Widens the back and builds pull-up strength."),
        "external rotation with cable" to Info("Elbow at your side, rotate the forearm outwards against the cable.", "Rotator cuff", "Keeps the shoulder joint stable and healthy under heavy pressing."),
        "treadmill walk" to Info("Brisk walk, with incline if you like.", "Heart, legs", "Warms the whole body up, or brings the heart rate down after."),
        "overhead stretch" to Info("Reach both arms overhead and lengthen through the sides.", "Lats, shoulders, sides", "Opens the shoulders and upper back before or after training."),
        "shoulder stretch" to Info("Pull one arm across the chest and hold.", "Rear shoulder", "Eases shoulder tightness after pressing and pulling."),
        "tricep stretch" to Info("Hand behind the head, gently press the elbow down.", "Triceps, lats", "Loosens the back of the arm and shoulder."),
        "upward stretch" to Info("Interlace the fingers and press the palms to the ceiling.", "Shoulders, upper back", "A quick full-body lengthen to finish the session."),
    )

    fun of(name: String, catalogueText: String, muscle: String): Info? {
        notes[ExerciseMedia.norm(name)]?.let { return it }
        if (catalogueText.isBlank() && muscle.isBlank()) return null
        return Info(catalogueText, muscle.replaceFirstChar { it.uppercase() }, "")
    }
}
