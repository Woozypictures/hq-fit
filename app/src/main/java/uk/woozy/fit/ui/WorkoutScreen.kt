package uk.woozy.fit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.woozy.fit.designsystem.GradientHeader
import uk.woozy.fit.designsystem.HqPillButton
import uk.woozy.fit.designsystem.HqText
import uk.woozy.fit.designsystem.HqTheme
import uk.woozy.fit.designsystem.PillStyle
import uk.woozy.fit.designsystem.SpacedTitle

/**
 * The full-screen route the active workout will use (milestone 4). It sits over the tab bar, as in Everfit.
 * For now it only proves the route and the header.
 */
@Composable
fun WorkoutPlaceholderScreen(onClose: () -> Unit) {
    val c = HqTheme.colors
    Column(Modifier.fillMaxSize()) {
        GradientHeader {
            Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 24.dp)) {
                HqText("WORKOUT", 12.sp, FontWeight.SemiBold, Color.White, letterSpacing = 1.sp)
                Spacer(Modifier.height(6.dp))
                SpacedTitle("Active workout", color = Color.White)
            }
        }
        Column(
            modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HqText("Logging sets, the rest timer and the finish summary arrive in milestone 4.", 16.sp)
            HqText("This full-screen route is the one they will use.", 14.sp, color = c.muted)
            Spacer(Modifier.weight(1f))
            HqPillButton("Close", onClick = onClose, modifier = Modifier.fillMaxWidth(), style = PillStyle.Filled)
        }
    }
}
