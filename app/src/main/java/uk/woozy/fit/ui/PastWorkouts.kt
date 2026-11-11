package uk.woozy.fit.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.woozy.fit.data.db.HqFitDatabase
import uk.woozy.fit.data.db.WorkoutSessionEntity
import uk.woozy.fit.designsystem.HqText
import uk.woozy.fit.designsystem.HqTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Finished workouts, newest first. Open one to see its summary and fix any set. */
@Composable
fun PastWorkoutsScreen(db: HqFitDatabase, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val c = HqTheme.colors
    val sessions by produceState(emptyList<WorkoutSessionEntity>(), db) { value = db.sessions().doneSince(0L).reversed() }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { BackBar("Past workouts", onBack) }
        if (sessions.isEmpty()) HqText("No finished workouts yet.", 14.sp, color = c.muted, modifier = Modifier.padding(16.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(sessions, key = { it.id }) { s ->
                Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { onOpen(s.id) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    HqText(s.name, 16.sp, FontWeight.Medium)
                    HqText(SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale.UK).format(Date(s.startedMs)), 13.sp, color = c.muted)
                }
            }
        }
    }
}
