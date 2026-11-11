package uk.woozy.fit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.woozy.fit.designsystem.HqCard
import uk.woozy.fit.designsystem.HqText
import uk.woozy.fit.designsystem.HqTheme
import uk.woozy.fit.designsystem.SectionLabel
import uk.woozy.fit.designsystem.formatWhole
import uk.woozy.fit.model.Targets
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
private fun TabPage(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        content()
        Spacer(Modifier.height(8.dp))
    }
}

/** Progress: the cards from the spec, each saying what feeds it. */
@Composable
fun ProgressScreen() {
    TabPage {
        ScreenTitle("Progress")
        EmptyCard("Report summary", "Average calories and macros against target. Needs the food diary (milestone 5).")
        EmptyCard("Weight change", "Weight against your 80 kg goal, with the forecast date. Needs Health Connect (milestone 7).")
        EmptyCard("Strength", "Best weight, estimated 1RM and volume per lift. Needs logged workouts (milestone 4).")
        EmptyCard("Personal records", "Your latest PRs. Needs logged workouts (milestone 4).")
        EmptyCard("Training frequency", "Sessions per week, planned against done. Needs a programme (milestone 3).")
    }
}
