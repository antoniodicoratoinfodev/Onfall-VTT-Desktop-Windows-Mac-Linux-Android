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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.d6d.ui.battle.GameButton
import app.d6d.ui.theme.Palette

/** Conferma esplicita prima di sostituire una battaglia non ancora salvata. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnsavedSessionDialog(
    open: Boolean,
    onDismiss: () -> Unit,
    onSaveFirst: () -> Unit,
    onDiscard: () -> Unit,
) {
    if (!open) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .background(Palette.Surface, RoundedCornerShape(14.dp))
                    .border(1.dp, Palette.Bloodied.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "La battaglia corrente non è salvata",
                    color = Palette.Text,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    "Avviando il nuovo incontro perderai lo stato corrente. Puoi tornare alla battaglia e salvarla con un nome.",
                    color = Palette.TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GameButton("Continua a preparare", accent = Palette.TextMuted, onClick = onDismiss)
                    GameButton("Torna e salva", accent = Palette.Heal, onClick = onSaveFirst)
                    GameButton("Scarta e avvia", accent = Palette.Critical, onClick = onDiscard)
                }
            }
        }
    }
}
