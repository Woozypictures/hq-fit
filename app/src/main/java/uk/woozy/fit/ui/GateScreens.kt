package uk.woozy.fit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import uk.woozy.fit.data.crypto.RecoveryCrypto
import uk.woozy.fit.data.db.DatabaseProvider
import uk.woozy.fit.designsystem.HqCard
import uk.woozy.fit.designsystem.HqPillButton
import uk.woozy.fit.designsystem.HqText
import uk.woozy.fit.designsystem.HqTheme

@Composable
private fun GateFrame(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) { content() }
    }
}

@Composable
fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        HqText("Woozy Fit", 20.sp, FontWeight.SemiBold, color = HqTheme.colors.muted)
    }
}

/**
 * First start: make the encryption key, show the recovery key once, and make the user type it back before
 * going on. Without it, data cannot be opened on a new phone.
 */
@Composable
fun FirstRunScreen(provider: DatabaseProvider, onDone: () -> Unit) {
    val c = HqTheme.colors
    val scope = rememberCoroutineScope()
    var recovery by remember { mutableStateOf<String?>(null) }
    var typed by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var wrong by remember { mutableStateOf(false) }

    GateFrame {
        HqText("Protect your data", 26.sp, FontWeight.Bold)
        HqText(
            "HQ Fit keeps everything on this phone, encrypted. If you change phone or this phone loses its lock, " +
                "a recovery key is the only way back in. Nobody can reset it for you.",
            15.sp,
            color = c.muted,
        )
        val shown = recovery
        if (shown == null) {
            HqPillButton(
                text = if (busy) "Creating…" else "Create recovery key",
                onClick = {
                    if (!busy) {
                        busy = true
                        scope.launch { recovery = provider.createNew(); busy = false }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            HqCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HqText("Your recovery key", 13.sp, color = c.muted)
                    HqText(shown, 20.sp, FontWeight.Bold)
                    HqText("Write it down or save it in a password manager. It is shown only once.", 13.sp, color = c.muted)
                }
            }
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it; wrong = false },
                label = { HqText("Type it back to confirm", 13.sp, color = c.muted) },
                singleLine = true,
                isError = wrong,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth(),
            )
            if (wrong) HqText("That does not match. Check it against the key above.", 13.sp, color = c.muted)
            HqPillButton(
                text = "Confirm and continue",
                onClick = {
                    if (RecoveryCrypto.normalise(typed) == RecoveryCrypto.normalise(shown)) {
                        scope.launch { provider.confirm(); onDone() }
                    } else {
                        wrong = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The data is here but this phone cannot open it, for example after a restore. */
@Composable
fun RecoveryScreen(provider: DatabaseProvider, onDone: () -> Unit) {
    val c = HqTheme.colors
    val scope = rememberCoroutineScope()
    var typed by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    GateFrame {
        HqText("Enter your recovery key", 26.sp, FontWeight.Bold)
        HqText("This phone cannot open your data on its own. Type the recovery key you saved when you first set up HQ Fit.", 15.sp, color = c.muted)
        OutlinedTextField(
            value = typed,
            onValueChange = { typed = it; wrong = false },
            label = { HqText("Recovery key", 13.sp, color = c.muted) },
            singleLine = true,
            isError = wrong,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth(),
        )
        if (wrong) HqText("That key does not open this data.", 13.sp, color = c.muted)
        HqPillButton(
            text = if (busy) "Checking…" else "Unlock",
            onClick = {
                if (!busy && provider.isWellFormed(typed)) {
                    busy = true
                    scope.launch {
                        if (provider.recover(typed)) onDone() else { wrong = true; busy = false }
                    }
                } else {
                    wrong = true
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
