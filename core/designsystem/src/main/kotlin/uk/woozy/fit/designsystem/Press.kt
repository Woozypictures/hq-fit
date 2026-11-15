package uk.woozy.fit.designsystem

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
/**
 * Tap target with an iOS-style feel: scales to 0.97 on a spring while pressed (GPU layer only, no layout change),
 * no ripple flash, and a crisp tick the moment the press registers (inside a scroll this waits for the short tap
 * timeout, so scrolling past a card never ticks).
 */
fun Modifier.pressable(enabled: Boolean = true, role: Role = Role.Button, onClick: () -> Unit): Modifier = composed {
    val view = androidx.compose.ui.platform.LocalView.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessHigh), label = "press")
    androidx.compose.runtime.LaunchedEffect(source) {
        source.interactions.collect { if (it is androidx.compose.foundation.interaction.PressInteraction.Press) crispTick(view) }
    }
    this.graphicsLayer { scaleX = s; scaleY = s }
        .clickable(interactionSource = source, indication = null, enabled = enabled, role = role, onClick = onClick)
}

/** Shortest, sharpest haptic the phone has (a keyboard-style tick), not a buzz. */
fun crispTick(v: android.view.View) {
    v.performHapticFeedback(if (android.os.Build.VERSION.SDK_INT >= 27) android.view.HapticFeedbackConstants.KEYBOARD_PRESS else android.view.HapticFeedbackConstants.KEYBOARD_TAP)
}

/** Two quick crisp pulses: the rest is over. */
fun restOverPulse(c: android.content.Context) {
    @Suppress("DEPRECATION")
    val v = c.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator ?: return
    if (android.os.Build.VERSION.SDK_INT >= 29) v.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_DOUBLE_CLICK))
    else v.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 18, 90, 18), -1))
}
