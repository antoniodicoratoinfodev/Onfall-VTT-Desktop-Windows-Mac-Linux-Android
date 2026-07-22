package app.d6d.ui.encounter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.d6d.engine.CombatSession
import app.d6d.ui.battle.GameButton
import app.d6d.ui.components.Chip
import app.d6d.ui.components.Eyebrow
import app.d6d.ui.roster.RosterKind
import app.d6d.ui.theme.Palette

/** Configuratore del prossimo combattimento, alimentato dal Compendio. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EncounterBuilderScreen(
    viewModel: EncounterBuilderViewModel,
    compact: Boolean,
    onStarted: (CombatSession, String) -> Unit,
    onUseDemo: () -> Unit,
    onOpenCompendium: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(Palette.Night)) {
        EncounterHeader(compact)

        Column(
            Modifier.fillMaxWidth().padding(
                horizontal = if (compact) 10.dp else 18.dp,
                vertical = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Eyebrow("Nome incontro")
            BasicTextField(
                value = viewModel.encounterName,
                onValueChange = { viewModel.encounterName = it; viewModel.dismissStatus() },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = Palette.Text),
                cursorBrush = SolidColor(Palette.Gold),
                modifier = Modifier.fillMaxWidth()
                    .background(Palette.Surface, RoundedCornerShape(8.dp))
                    .border(1.dp, Palette.Line, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Chip("${viewModel.selectedCount} partecipanti", Palette.Gold)
                Chip("${viewModel.allyCount} alleati", Palette.Party)
                Chip("${viewModel.opponentCount} avversari", Palette.Enemy)
            }
        }

        viewModel.status?.let { message ->
            Text(
                text = message,
                color = Palette.GoldBright,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth()
                    .background(Palette.Gold.copy(alpha = 0.10f))
                    .clickable { viewModel.dismissStatus() }
                    .padding(horizontal = 18.dp, vertical = 7.dp),
            )
        }

        val people = viewModel.participants.filter { it.kind == RosterKind.PERSONAGGIO }
        val creatures = viewModel.participants.filter { it.kind == RosterKind.CREATURA }

        if (people.isEmpty() && creatures.isEmpty()) {
            EmptyCompendium(
                onOpenCompendium = onOpenCompendium,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = if (compact) 10.dp else 18.dp,
                    end = if (compact) 10.dp else 18.dp,
                    bottom = 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (people.isNotEmpty()) {
                    item { Eyebrow("Personaggi (${people.size})", Palette.Party) }
                    items(people, key = { "personaggio-${it.id}" }) { participant ->
                        ParticipantCard(participant, viewModel)
                    }
                }
                if (creatures.isNotEmpty()) {
                    item {
                        Eyebrow(
                            "Creature (${creatures.size})",
                            Palette.Enemy,
                            Modifier.padding(top = 7.dp),
                        )
                    }
                    items(creatures, key = { "creatura-${it.id}" }) { participant ->
                        ParticipantCard(participant, viewModel)
                    }
                }
            }
        }

        EncounterFooter(
            viewModel = viewModel,
            compact = compact,
            onStarted = onStarted,
            onUseDemo = onUseDemo,
        )
    }
}

@Composable
private fun EncounterHeader(compact: Boolean) {
    Column(
        Modifier.fillMaxWidth().background(Palette.Surface).padding(
            horizontal = if (compact) 12.dp else 18.dp,
            vertical = 11.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = "Nuovo incontro",
            color = Palette.Text,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = "Scegli dal Compendio, assegna gli schieramenti e avvia la battaglia.",
            color = Palette.TextMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParticipantCard(
    participant: EncounterParticipant,
    viewModel: EncounterBuilderViewModel,
) {
    val accent = when {
        !participant.selected -> Palette.TextFaint
        participant.faction == EncounterFaction.ALLEATI -> Palette.Party
        else -> Palette.Enemy
    }
    Column(
        Modifier.fillMaxWidth()
            .background(Palette.Surface.copy(alpha = if (participant.selected) 0.92f else 0.52f), RoundedCornerShape(10.dp))
            .border(1.dp, accent.copy(alpha = 0.62f), RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().toggleable(
                value = participant.selected,
                role = Role.Checkbox,
                onValueChange = { viewModel.setSelected(participant.id, it) },
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(checked = participant.selected, onCheckedChange = null)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = participant.name,
                    color = Palette.Text,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = participant.subtitle,
                    color = Palette.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Chip(
                if (participant.kind == RosterKind.PERSONAGGIO) "Personaggio" else "Creatura",
                if (participant.kind == RosterKind.PERSONAGGIO) Palette.Party else Palette.Enemy,
            )
        }

        if (participant.selected) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                GameButton(
                    label = "−",
                    accent = Palette.TextMuted,
                    enabled = participant.quantity > 1,
                    onClick = { viewModel.changeQuantity(participant.id, -1) },
                )
                Chip("Quantità ${participant.quantity}", Palette.Text)
                GameButton(
                    label = "+",
                    accent = Palette.TextMuted,
                    onClick = { viewModel.changeQuantity(participant.id, 1) },
                )
                EncounterFaction.entries.forEach { faction ->
                    val selected = participant.faction == faction
                    GameButton(
                        label = faction.label,
                        accent = if (selected) {
                            if (faction == EncounterFaction.ALLEATI) Palette.Party else Palette.Enemy
                        } else {
                            Palette.TextFaint
                        },
                        onClick = { viewModel.setFaction(participant.id, faction) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyCompendium(onOpenCompendium: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.padding(18.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Il Compendio è vuoto.",
                color = Palette.Text,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Crea e salva almeno una scheda o uno stat block.",
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            GameButton("Apri Compendio", accent = Palette.Party, onClick = onOpenCompendium)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EncounterFooter(
    viewModel: EncounterBuilderViewModel,
    compact: Boolean,
    onStarted: (CombatSession, String) -> Unit,
    onUseDemo: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().background(Palette.Surface).padding(
            horizontal = if (compact) 10.dp else 18.dp,
            vertical = 10.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GameButton("Azzera", accent = Palette.TextMuted, onClick = { viewModel.clearSelection() })
        GameButton("Squadra base", accent = Palette.TextMuted, onClick = { viewModel.resetRecommended() })
        GameButton("Demo", accent = Palette.Party, onClick = onUseDemo)
        GameButton(
            label = "Avvia battaglia",
            accent = Palette.Heal,
            enabled = viewModel.canStart,
            onClick = {
                viewModel.tryStart()?.let { session ->
                    onStarted(session, viewModel.encounterName.trim())
                }
            },
        )
    }
}
