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
import app.d6d.sheet.metresFromFeet
import app.d6d.ui.battle.GameButton
import app.d6d.ui.components.Chip
import app.d6d.ui.components.Eyebrow
import app.d6d.ui.roster.RosterKind
import app.d6d.ui.session.SessionManager
import app.d6d.ui.session.SessionMenuDialog
import app.d6d.ui.theme.GoldenRule
import app.d6d.ui.theme.Palette

/** Configuratore del prossimo combattimento, alimentato dal Compendio. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EncounterBuilderScreen(
    viewModel: EncounterBuilderViewModel,
    compact: Boolean,
    sessions: SessionManager,
    onStarted: (CombatSession, String, EncounterMode) -> Unit,
    onSessionLoaded: () -> Unit,
    onOpenCompendium: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Fondo trasparente: lascia trasparire il fondale atmosferico condiviso di
    // AppRoot. Intestazione e pannelli hanno superfici proprie e restano leggibili.
    Column(modifier.fillMaxSize()) {
        EncounterHeader(viewModel.step, compact)
        GoldenRule()

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

        SessionMenuDialog(sessions, onLoaded = onSessionLoaded)

        when (viewModel.step) {
            NewGameStep.TEMPLATE -> TemplateChoiceStep(
                viewModel = viewModel,
                compact = compact,
                sessions = sessions,
                onCreateFromScratch = {
                    viewModel.createFromScratch()
                    onOpenCompendium()
                },
                modifier = Modifier.weight(1f),
            )

            NewGameStep.PARTECIPANTI -> ParticipantsStep(
                viewModel = viewModel,
                compact = compact,
                onOpenCompendium = onOpenCompendium,
                modifier = Modifier.weight(1f),
            )

            NewGameStep.GRIGLIA -> GridStep(viewModel, compact, Modifier.weight(1f))
            NewGameStep.MODALITA -> ModeStep(viewModel, compact, Modifier.weight(1f))
        }

        if (viewModel.step != NewGameStep.TEMPLATE) GoldenRule()
        NewGameFooter(viewModel, compact, onStarted, onOpenCompendium)
    }
}

@Composable
private fun EncounterHeader(step: NewGameStep, compact: Boolean) {
    Column(
        Modifier.fillMaxWidth().background(Palette.Surface).padding(
            horizontal = if (compact) 12.dp else 18.dp,
            vertical = 11.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = "Partita",
            color = Palette.Text,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = when (step) {
                NewGameStep.TEMPLATE -> "1 di 4 · Scegli se usare i template salvati o creare tutto da zero."
                NewGameStep.PARTECIPANTI -> "2 di 4 · Scegli personaggi, mob, quantità e schieramenti."
                NewGameStep.GRIGLIA -> "3 di 4 · Imposta dimensioni e scala metrica della griglia."
                NewGameStep.MODALITA -> "4 di 4 · Scegli l'esperienza e avvia la partita."
            },
            color = Palette.TextMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun TemplateChoiceStep(
    viewModel: EncounterBuilderViewModel,
    compact: Boolean,
    sessions: SessionManager,
    onCreateFromScratch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val people = viewModel.participants.count { it.kind == RosterKind.PERSONAGGIO }
    val creatures = viewModel.participants.count { it.kind == RosterKind.CREATURA }
    Box(modifier.fillMaxWidth().padding(if (compact) 12.dp else 24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Da dove vuoi partire?",
                color = Palette.Text,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                "I template esistenti non vengono mai cancellati scegliendo di creare da zero.",
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GameButton(
                    label = "Usa template già creati",
                    subtitle = "$people personaggi · $creatures mob",
                    accent = Palette.Party,
                    enabled = people + creatures > 0,
                    onClick = { viewModel.useExistingTemplates() },
                )
                GameButton(
                    label = "Crea personaggi e mob da zero",
                    subtitle = "Apre il Compendio per creare nuove schede",
                    accent = Palette.Gold,
                    onClick = onCreateFromScratch,
                )
                GameButton(
                    label = "Carica sessione",
                    subtitle = when (sessions.sessions.size) {
                        0 -> "Nessuna sessione salvata"
                        1 -> "1 sessione salvata"
                        else -> "${sessions.sessions.size} sessioni salvate"
                    },
                    accent = Palette.Heal,
                    onClick = {
                        sessions.refresh()
                        sessions.menuOpen = true
                    },
                )
            }
        }
    }
}

@Composable
private fun ParticipantsStep(
    viewModel: EncounterBuilderViewModel,
    compact: Boolean,
    onOpenCompendium: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(
                horizontal = if (compact) 10.dp else 18.dp,
                vertical = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Eyebrow("Nome partita")
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

        val people = viewModel.participants.filter { it.kind == RosterKind.PERSONAGGIO }
        val creatures = viewModel.participants.filter { it.kind == RosterKind.CREATURA }
        if (people.isEmpty() && creatures.isEmpty()) {
            EmptyCompendium(
                onOpenCompendium = onOpenCompendium,
                creatingFromScratch = viewModel.templateSource == TemplateSource.DA_ZERO,
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
                    items(people, key = { "personaggio-${it.id}" }) { ParticipantCard(it, viewModel) }
                }
                if (creatures.isNotEmpty()) {
                    item { Eyebrow("Mob e creature (${creatures.size})", Palette.Enemy, Modifier.padding(top = 7.dp)) }
                    items(creatures, key = { "creatura-${it.id}" }) { ParticipantCard(it, viewModel) }
                }
            }
        }
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
private fun EmptyCompendium(
    onOpenCompendium: () -> Unit,
    creatingFromScratch: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier.padding(18.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = if (creatingFromScratch) "Crea i protagonisti della partita." else "Il Compendio è vuoto.",
                color = Palette.Text,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (creatingFromScratch) {
                    "Crea e salva personaggi e mob; tornando qui vedrai soltanto i nuovi template."
                } else {
                    "Crea e salva almeno una scheda o uno stat block."
                },
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            GameButton("Apri Compendio", accent = Palette.Party, onClick = onOpenCompendium)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GridStep(
    viewModel: EncounterBuilderViewModel,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(if (compact) 12.dp else 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            "Dimensione della griglia",
            color = Palette.Text,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.titleLarge,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(20 to 15, 30 to 20, 40 to 30).forEach { (columns, rows) ->
                val selected = viewModel.gridColumns == columns && viewModel.gridRows == rows
                GameButton(
                    label = "$columns × $rows",
                    subtitle = if (columns == 20 && rows == 15) "Predefinita" else null,
                    accent = if (selected) Palette.Gold else Palette.TextMuted,
                    selected = selected,
                    onClick = { viewModel.useGridPreset(columns, rows) },
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GameButton("− colonne", accent = Palette.TextMuted, onClick = {
                viewModel.updateGridColumns(viewModel.gridColumns - 5)
            })
            Chip("${viewModel.gridColumns} colonne", Palette.Text)
            GameButton("+ colonne", accent = Palette.TextMuted, onClick = {
                viewModel.updateGridColumns(viewModel.gridColumns + 5)
            })
            GameButton("− righe", accent = Palette.TextMuted, onClick = {
                viewModel.updateGridRows(viewModel.gridRows - 5)
            })
            Chip("${viewModel.gridRows} righe", Palette.Text)
            GameButton("+ righe", accent = Palette.TextMuted, onClick = {
                viewModel.updateGridRows(viewModel.gridRows + 5)
            })
        }

        Text(
            "Metri rappresentati da ogni quadratino",
            color = Palette.Text,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.titleLarge,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(5, 10, 20, 50).forEach { feet ->
                val selected = viewModel.feetPerSquare == feet
                GameButton(
                    label = "${metresFromFeet(feet)} m / quadratino",
                    subtitle = "$feet piedi${if (feet == 5) " · Predefinito" else ""}",
                    accent = if (selected) Palette.Gold else Palette.TextMuted,
                    selected = selected,
                    onClick = { viewModel.updateFeetPerSquare(feet) },
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GameButton("− 0,3048 m", accent = Palette.TextMuted, onClick = {
                viewModel.updateFeetPerSquare(viewModel.feetPerSquare - 1)
            })
            Chip("Scala scelta: ${metresFromFeet(viewModel.feetPerSquare)} m", Palette.GoldBright)
            GameButton("+ 0,3048 m", accent = Palette.TextMuted, onClick = {
                viewModel.updateFeetPerSquare(viewModel.feetPerSquare + 1)
            })
        }
        val widthMetres = metresFromFeet(viewModel.gridColumns * viewModel.feetPerSquare)
        val heightMetres = metresFromFeet(viewModel.gridRows * viewModel.feetPerSquare)
        Text(
            "Area totale: $widthMetres × $heightMetres m · " +
                "${viewModel.gridColumns} × ${viewModel.gridRows} quadratini.",
            color = Palette.GoldBright,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth()
                .background(Palette.Gold.copy(alpha = 0.09f), RoundedCornerShape(8.dp))
                .padding(10.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModeStep(
    viewModel: EncounterBuilderViewModel,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().padding(if (compact) 12.dp else 24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Come vuoi iniziare?",
                color = Palette.Text,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.headlineSmall,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                EncounterMode.entries.forEach { mode ->
                    val selected = viewModel.mode == mode
                    GameButton(
                        label = mode.label,
                        subtitle = mode.description,
                        accent = if (selected) Palette.GoldBright else Palette.TextMuted,
                        selected = selected,
                        onClick = { viewModel.mode = mode },
                    )
                }
            }
            Text(
                if (viewModel.mode == EncounterMode.FIGHT) {
                    "I token verranno disposti al centro in due schieramenti vicini. Potrai trascinarli in Modifica."
                } else {
                    "La griglia sarà pronta ma vuota: potrai preparare liberamente esplorazione e scene narrative."
                },
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewGameFooter(
    viewModel: EncounterBuilderViewModel,
    compact: Boolean,
    onStarted: (CombatSession, String, EncounterMode) -> Unit,
    onOpenCompendium: () -> Unit,
) {
    if (viewModel.step == NewGameStep.TEMPLATE) return
    FlowRow(
        modifier = Modifier.fillMaxWidth().background(Palette.Surface).padding(
            horizontal = if (compact) 10.dp else 18.dp,
            vertical = 10.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (viewModel.step) {
            NewGameStep.TEMPLATE -> Unit
            NewGameStep.PARTECIPANTI -> {
                GameButton("Indietro", accent = Palette.TextMuted, onClick = { viewModel.back() })
                GameButton("Azzera", accent = Palette.TextMuted, onClick = { viewModel.clearSelection() })
                if (viewModel.templateSource == TemplateSource.ESISTENTI) {
                    GameButton("Squadra base", accent = Palette.Party, onClick = { viewModel.resetRecommended() })
                } else {
                    GameButton("Crea altri da zero", accent = Palette.Party, onClick = onOpenCompendium)
                }
                GameButton(
                    label = "Avanti · Griglia",
                    accent = Palette.Heal,
                    enabled = viewModel.canStart,
                    onClick = { viewModel.continueFromParticipants() },
                )
            }
            NewGameStep.GRIGLIA -> {
                GameButton("Indietro", accent = Palette.TextMuted, onClick = { viewModel.back() })
                GameButton("Ripristina 20 × 15", accent = Palette.TextMuted, onClick = {
                    viewModel.useGridPreset(20, 15)
                    viewModel.updateFeetPerSquare(5)
                })
                GameButton("Avanti · Modalità", accent = Palette.Heal, onClick = { viewModel.continueFromGrid() })
            }
            NewGameStep.MODALITA -> {
                GameButton("Indietro", accent = Palette.TextMuted, onClick = { viewModel.back() })
                GameButton(
                    label = "Avvia partita",
                    subtitle = viewModel.mode.label,
                    accent = Palette.Heal,
                    enabled = viewModel.canStart,
                    onClick = {
                        viewModel.tryStart()?.let { session ->
                            onStarted(session, viewModel.encounterName.trim(), viewModel.mode)
                        }
                    },
                )
            }
        }
    }
}
