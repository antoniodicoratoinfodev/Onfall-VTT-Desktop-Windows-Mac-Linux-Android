package app.d6d.ui.battle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.d6d.ui.components.Chip
import app.d6d.ui.components.Eyebrow
import app.d6d.ui.components.Faction
import app.d6d.ui.components.PanelScrollbar
import app.d6d.ui.roster.RosterItem
import app.d6d.ui.roster.RosterKind
import app.d6d.ui.roster.RosterViewModel
import app.d6d.ui.state.BattleViewModel
import app.d6d.ui.theme.OrnateDivider
import app.d6d.ui.i18n.strings
import app.d6d.ui.theme.Palette
import app.d6d.ui.theme.ornateFrame
import app.d6d.ui.theme.panelBrush

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BattleRosterDialog(
    open: Boolean,
    targetFaction: Faction,
    viewModel: BattleViewModel,
    roster: RosterViewModel,
    onCreateCharacter: () -> Unit,
    onCreateCreature: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!open) return

    val words = strings.battle
    val party = targetFaction == Faction.PARTY
    val accent = if (party) Palette.Party else Palette.Enemy
    val destination = if (party) strings.battle.squad else strings.battle.enemies
    val people = roster.items.filter { it.kind == RosterKind.PERSONAGGIO }
    val creatures = roster.items.filter { it.kind == RosterKind.CREATURA }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            val dialogShape = RoundedCornerShape(14.dp)
            Column(
                Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .panelBrush(dialogShape)
                    .border(1.dp, Palette.Bronze.copy(alpha = 0.6f), dialogShape)
                    .ornateFrame(accent = accent, alpha = 0.5f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            words.addTo(destination),
                            color = Palette.Text,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            words.pickFromGrimoireOrCreate,
                            color = Palette.TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    GameButton(strings.common.close, accent = Palette.TextMuted, onClick = onDismiss)
                }
                OrnateDivider(color = accent.copy(alpha = 0.65f))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    GameButton(words.addCharacter, accent = Palette.Party, onClick = onCreateCharacter)
                    GameButton(words.addCreature, accent = Palette.Enemy, onClick = onCreateCreature)
                }

                val listState = rememberLazyListState()
                Box(Modifier.weight(1f).fillMaxWidth().background(Palette.Surface.copy(alpha = 0.35f))) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(9.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (people.isNotEmpty()) {
                            item { Eyebrow(words.charactersCount(people.size), color = Palette.Party) }
                            items(people, key = { it.id }) { item ->
                                RosterCombatantRow(item, Palette.Party) {
                                    roster.definitionFor(item.id)?.let { actor ->
                                        if (viewModel.addRosterCombatant(actor, party) != null) onDismiss()
                                    } ?: viewModel.showMessage(words.sheetUnavailable(item.name))
                                }
                            }
                        }
                        if (creatures.isNotEmpty()) {
                            item { Eyebrow(words.creaturesCount(creatures.size), color = Palette.Enemy) }
                            items(creatures, key = { it.id }) { item ->
                                RosterCombatantRow(item, Palette.Enemy) {
                                    roster.definitionFor(item.id)?.let { actor ->
                                        if (viewModel.addRosterCombatant(actor, party) != null) onDismiss()
                                    } ?: viewModel.showMessage(words.sheetUnavailable(item.name))
                                }
                            }
                        }
                        if (people.isEmpty() && creatures.isEmpty()) {
                            item {
                                Text(
                                    words.grimoireEmpty,
                                    color = Palette.TextMuted,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                    PanelScrollbar(listState, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun RosterCombatantRow(
    item: RosterItem,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.SurfaceHigh.copy(alpha = 0.72f), RoundedCornerShape(8.dp))
            .border(1.dp, Palette.Line, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                item.name,
                color = Palette.Text,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
            Chip(item.subtitle, accent)
        }
        GameButton(strings.common.add, accent = accent, dense = true, onClick = onClick)
    }
}
