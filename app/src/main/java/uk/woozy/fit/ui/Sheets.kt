package uk.woozy.fit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.woozy.fit.designsystem.HqPillButton
import uk.woozy.fit.designsystem.HqText
import uk.woozy.fit.designsystem.HqTheme

/**
 * A question or small form as a sheet from the bottom (GymMane style) instead of a pop-up in the middle:
 * grab handle, centred title and message, optional content, a full-width main button and a quiet second choice.
 * [danger] colours the main button as a warning (delete, discard).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetDialog(
    onDismissRequest: () -> Unit,
    title: String,
    message: String? = null,
    confirm: String,
    onConfirm: () -> Unit,
    dismiss: String = "Cancel",
    onDismiss: () -> Unit = onDismissRequest,
    danger: Boolean = false,
    confirmEnabled: Boolean = true,
    content: (@Composable () -> Unit)? = null,
) {
    val c = HqTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.card,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HqText(title, 19.sp, FontWeight.SemiBold, textAlign = TextAlign.Center)
            if (message != null) HqText(message, 14.sp, color = c.muted, textAlign = TextAlign.Center)
            if (content != null) Column(Modifier.fillMaxWidth().padding(top = 4.dp)) { content() }
            HqPillButton(
                confirm, onClick = onConfirm, enabled = confirmEnabled,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            if (danger) HqText("This can't be undone.", 12.sp, color = c.warn)
            TextButton(onClick = onDismiss) { HqText(dismiss, 15.sp, FontWeight.Medium, c.muted) }
        }
    }
}
