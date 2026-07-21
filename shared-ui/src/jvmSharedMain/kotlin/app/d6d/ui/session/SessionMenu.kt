package app.d6d.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.d6d.persistence.session.SessionSummary
import app.d6d.ui.battle.GameButton
import app.d6d.ui.components.Chip
import app.d6d.ui.components.Eyebrow
import app.d6d.ui.theme.Palette

/** Pulsante che apre il menù delle sessioni, da mettere nell'intestazione. */
@Composable
fun SessionMenuButton(manager: SessionManager, modifier: Modifier = Modifier) {
    GameButton(
        label = "💾 Sessione",
        accent = Palette.Gold,
        onClick = {
            manager.refresh()
            manager.menuOpen = true
        },
        modifier = modifier,
    )
}

/**
 * Menù delle sessioni: salva con un nome, riapri o elimina quelle salvate.
 *
 * L'elenco mostra round, combattenti e stato di ciascuna, cosi' si riconosce la
 * partita giusta senza doverla aprire.
 */
@Composable
fun SessionMenuDialog(manager: SessionManager) {
    if (!manager.menuOpen) return

    var name by remember(manager.currentName) { mutableStateOf(manager.currentName) }

    Dialog(onDismissRequest = { manager.menuOpen = false }) {
        Column(
            Modifier
                .width(520.dp)
                .background(Palette.Surface, RoundedCornerShape(12.dp))
                .border(1.dp, Palette.Line, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Sessioni",
                color = Palette.Text,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "Una sessione salvata conserva combattimento, mappa, segnaposti, " +
                    "registro completo e stato dei dadi: riaprendola i tiri futuri sono gli stessi.",
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )

            manager.status?.let {
                Text(
                    text = it,
                    color = Palette.Gold,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Palette.Gold.copy(alpha = 0.10f), RoundedCornerShape(5.dp))
                        .clickable { manager.dismissStatus() }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                )
            }

            Eyebrow("Salva con nome")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Palette.Text, fontSize = 13.sp),
                    cursorBrush = SolidColor(Palette.Gold),
                    modifier = Modifier
                        .weight(1f)
                        .background(Palette.Night, RoundedCornerShape(5.dp))
                        .border(1.dp, Palette.Line, RoundedCornerShape(5.dp))
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                )
                GameButton(
                    label = "Salva",
                    accent = Palette.Heal,
                    enabled = name.isNotBlank(),
                    onClick = { manager.save(name) },
                )
            }

            Eyebrow("Sessioni salvate (${manager.sessions.size})")
            if (manager.sessions.isEmpty()) {
                Text(
                    text = "Nessuna sessione salvata.",
                    color = Palette.TextFaint,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                LazyColumn(
                    Modifier.heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(manager.sessions) { summary ->
                        SessionRow(summary, manager)
                    }
                }
            }

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                GameButton("Chiudi", accent = Palette.TextMuted, onClick = { manager.menuOpen = false })
            }
        }
    }
}

@Composable
private fun SessionRow(summary: SessionSummary, manager: SessionManager) {
    val damaged = summary.status == "ILLEGGIBILE"

    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.Night, RoundedCornerShape(8.dp))
            .border(1.dp, if (damaged) Palette.Critical else Palette.Line, RoundedCornerShape(8.dp))
            .padding(9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = summary.displayName,
                color = if (damaged) Palette.Critical else Palette.Text,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (damaged) {
                    Chip("File danneggiato", Palette.Critical)
                } else {
                    Chip("Round ${summary.round}", Palette.Gold)
                    Chip("${summary.combatantCount} combattenti", Palette.TextMuted)
                    Chip(summary.status, Palette.Party)
                }
            }
            if (summary.savedAt.isNotBlank()) {
                Text(
                    // L'istante ISO va mostrato in forma breve: data e ora bastano.
                    text = summary.savedAt.replace('T', ' ').take(16),
                    color = Palette.TextFaint,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        if (!damaged) {
            GameButton("Apri", accent = Palette.Party, onClick = { manager.load(summary) })
        }
        GameButton("Elimina", accent = Palette.Enemy, onClick = { manager.delete(summary) })
    }
}
