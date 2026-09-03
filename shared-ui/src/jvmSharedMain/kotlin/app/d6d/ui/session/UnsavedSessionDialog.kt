package app.d6d.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.d6d.ui.battle.GameButton
import app.d6d.ui.components.dismissDialogOnTap
import app.d6d.ui.components.keepDialogOpenOnTap
import app.d6d.ui.i18n.strings
import app.d6d.ui.theme.Palette
import app.d6d.ui.theme.ornateFrame
import app.d6d.ui.theme.panelBrush

/** Conferma esplicita prima di sostituire una battaglia non ancora salvata. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnsavedSessionDialog(
    open: Boolean,
    onDismiss: () -> Unit,
    onSaveFirst: () -> Unit,
    onDiscard: () -> Unit,
) {
    val words = strings.session
    if (!open) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier.fillMaxSize().dismissDialogOnTap(onDismiss).padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val dialogShape = RoundedCornerShape(14.dp)
            Column(
                Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .panelBrush(dialogShape)
                    // Cornice ambrata: e' un avvertimento, non un pannello qualunque.
                    .border(1.dp, Palette.Bloodied.copy(alpha = 0.65f), dialogShape)
                    .ornateFrame(accent = Palette.Bloodied, alpha = 0.6f)
                    .keepDialogOpenOnTap()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    words.currentBattleNotSaved,
                    color = Palette.Text,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    words.currentBattleNotSavedBody,
                    color = Palette.TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GameButton(words.keepPreparing, accent = Palette.TextMuted, onClick = onDismiss)
                    GameButton(words.goBackAndSave, accent = Palette.Heal, onClick = onSaveFirst)
                    GameButton(words.discardAndStart, accent = Palette.Critical, onClick = onDiscard)
                }
            }
        }
    }
}
