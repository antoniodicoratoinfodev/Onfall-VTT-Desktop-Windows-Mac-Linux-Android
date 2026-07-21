package app.d6d.ui.roster

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.d6d.ui.battle.GameButton
import app.d6d.ui.components.Chip
import app.d6d.ui.components.Eyebrow
import app.d6d.ui.images.PortraitRepository
import app.d6d.ui.sheet.CharacterSheetEditor
import app.d6d.ui.sheet.MonsterStatBlockEditor
import app.d6d.ui.theme.Palette

/**
 * Compendio unificato: personaggi e creature in un solo posto.
 *
 * Un personaggio si redige con la scheda completa, una creatura con lo stat block.
 * Non c'e' piu' un editor leggero separato per i personaggi: la scheda e' l'unica
 * fonte, e il catalogo da combattimento ne discende. Cosi' non possono divergere.
 */
@Composable
fun RosterScreen(
    viewModel: RosterViewModel,
    portraits: PortraitRepository,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(Palette.Night)) {
        Row(
            Modifier.fillMaxWidth().background(Palette.Surface).padding(14.dp, 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Compendio",
                    color = Palette.Text,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "Personaggi come schede complete, creature come stat block. " +
                        "Il catalogo di combattimento discende da qui.",
                    color = Palette.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            GameButton("+ Personaggio", accent = Palette.Party, onClick = { viewModel.newCharacter() })
            GameButton("+ Creatura", accent = Palette.Enemy, onClick = { viewModel.newCreature() })
        }

        (viewModel.status ?: viewModel.sheets.status)?.let {
            Text(
                text = it,
                color = Palette.Gold,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth()
                    .background(Palette.Gold.copy(alpha = 0.10f))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }

        val editor: @Composable (Modifier) -> Unit = { editorModifier ->
            when (viewModel.editorKind) {
                RosterKind.PERSONAGGIO ->
                    CharacterSheetEditor(viewModel.sheets, portraits, compact, editorModifier)

                RosterKind.CREATURA ->
                    MonsterStatBlockEditor(viewModel.sheets, portraits, compact, editorModifier)
            }
        }

        if (compact) {
            Column(Modifier.weight(1f)) {
                RosterList(viewModel, Modifier.fillMaxWidth().padding(7.dp))
                Box(Modifier.weight(1f)) { editor(Modifier.fillMaxSize()) }
            }
        } else {
            Row(Modifier.weight(1f)) {
                RosterList(viewModel, Modifier.width(258.dp))
                Box(Modifier.weight(1f)) { editor(Modifier.fillMaxSize()) }
            }
        }
    }
}

@Composable
private fun RosterList(viewModel: RosterViewModel, modifier: Modifier = Modifier) {
    val items = viewModel.items
    val people = items.filter { it.kind == RosterKind.PERSONAGGIO }
    val creatures = items.filter { it.kind == RosterKind.CREATURA }

    Column(
        modifier
            .fillMaxSize()
            .background(Palette.Surface.copy(alpha = 0.45f))
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            if (people.isNotEmpty()) {
                item { Eyebrow("Personaggi (${people.size})", color = Palette.Party) }
                items(people) { RosterRow(it, viewModel) }
            }
            if (creatures.isNotEmpty()) {
                item {
                    Eyebrow(
                        "Creature (${creatures.size})",
                        color = Palette.Enemy,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                items(creatures) { RosterRow(it, viewModel) }
            }
        }
    }
}

@Composable
private fun RosterRow(item: RosterItem, viewModel: RosterViewModel) {
    val selected = viewModel.selectedId == item.id && viewModel.editorKind == item.kind
    val accent = if (item.kind == RosterKind.PERSONAGGIO) Palette.Party else Palette.Enemy

    Column(
        Modifier.fillMaxWidth()
            .background(if (selected) Palette.SurfaceHigh else Color.Transparent, RoundedCornerShape(8.dp))
            .border(1.dp, if (selected) Palette.Gold else Palette.Line, RoundedCornerShape(8.dp))
            .clickable { viewModel.select(item) }
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = item.name,
            color = Palette.Text,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium,
        )
        Chip(item.subtitle, accent)
    }
}
