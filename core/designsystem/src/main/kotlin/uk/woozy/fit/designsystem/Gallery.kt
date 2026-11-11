package uk.woozy.fit.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Every design-system component on one page. The app shows it under Settings > Design components,
 * and CI renders it in light and dark so each release carries screenshots of the look.
 * It does not scroll itself, so screenshot tests can render it at full height.
 */
@Composable
fun ComponentGallery(modifier: Modifier = Modifier) {
    val c = HqTheme.colors
    var setDone by remember { mutableStateOf(true) }
    var secondDone by remember { mutableStateOf(false) }
    var theme by remember { mutableStateOf(0) }

    Column(
        modifier = modifier.fillMaxWidth().background(c.page).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Block("Colours") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Swatch("Page", c.page)
                Swatch("Card", c.card)
                Swatch("Blue", c.primary)
                Swatch("Ink", c.ink)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Swatch("Protein", c.protein)
                Swatch("Carbs", c.carbs)
                Swatch("Fat", c.fat)
                Swatch("Track", c.track)
            }
        }

        Block("Type") {
            SpacedTitle("Fullbody A")
            Spacer(Modifier.height(8.dp))
            HqText("Hello, Mahesh!", 28.sp, FontWeight.Bold)
            HqText("Body text in Google Sans, with tabular figures: 1,234 / 5,678", 15.sp)
            HqText("Muted caption · 2,297 kcal", 13.sp, color = c.muted)
        }

        Block("Buttons") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HqPillButton("Save", onClick = {})
                HqPillButton("Cancel", onClick = {}, style = PillStyle.Outline)
                HqPillButton("Off", onClick = {}, enabled = false)
            }
            Spacer(Modifier.height(12.dp))
            SegmentedChoice(listOf("Light", "Dark", "System"), theme, { theme = it })
        }

        Block("Gradient card") {
            GradientCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    HqText("TODAY'S WORKOUT", 12.sp, FontWeight.SemiBold, androidx.compose.ui.graphics.Color.White, letterSpacing = 1.sp)
                    Spacer(Modifier.height(6.dp))
                    SpacedTitle("Fullbody A", color = androidx.compose.ui.graphics.Color.White)
                    Spacer(Modifier.height(16.dp))
                    HqPillButton("Start workout", onClick = {}, style = PillStyle.OnGradient)
                }
            }
        }

        Block("Rings and bars") {
            HqCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        HqRing(0.72f, c.primary) { HqText("1,654", 16.sp, FontWeight.Bold) }
                        HqRing(0.40f, c.protein) { HqText("60", 16.sp, FontWeight.Bold) }
                        HqRing(0.0f, c.fat) { HqText("0", 16.sp, FontWeight.Bold) }
                    }
                    MacroBar("Protein", 96.0, 150.0, "g", c.protein)
                    MacroBar("Net carbs", 180.0, 260.0, "g", c.carbs)
                    MacroBar("Fat", 38.0, 70.0, "g", c.fat)
                }
            }
        }

        Block("Set table") {
            HqCard(modifier = Modifier.fillMaxWidth()) {
                SetTableHeader()
                SetRow("1", "50 × 12", "50", "12", setDone, { setDone = !setDone })
                SetRow("2", "50 × 11", "50", "10", secondDone, { secondDone = !secondDone })
            }
        }

        Block("Number keypad") {
            HqCard(modifier = Modifier.fillMaxWidth()) {
                NumberKeypad(onKey = {}, stepLabel = "2.5 kg")
            }
        }

        Block("Bottom bar") {
            HqCard(modifier = Modifier.fillMaxWidth()) {
                HqBottomBar(
                    items = listOf(
                        BarItem("Today", Icons.Rounded.Home),
                        BarItem("Train", Icons.Rounded.FitnessCenter),
                        BarItem("Diary", Icons.Rounded.Restaurant),
                        BarItem("Progress", Icons.Rounded.BarChart),
                    ),
                    selected = 0,
                    onSelect = {},
                    onCentre = {},
                )
            }
        }
    }
}

@Composable
private fun Block(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionLabel(title)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun Swatch(name: String, color: androidx.compose.ui.graphics.Color) {
    val c = HqTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(width = 72.dp, height = 40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(color),
        )
        Spacer(Modifier.height(4.dp))
        HqText(name, 11.sp, color = c.muted)
    }
}
