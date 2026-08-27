package app.d6d.ui.encounter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.d6d.engine.CombatSession
import app.d6d.engine.ai.EnemyCpuDifficulty
import app.d6d.ui.i18n.Strings
import app.d6d.ui.i18n.strings
import app.d6d.ui.state.label
import app.d6d.sheet.i18n.distanceLabel
import app.d6d.ui.i18n.currentLanguage
import app.d6d.ui.battle.GameButton
import app.d6d.ui.components.Chip
import app.d6d.ui.components.Eyebrow
import app.d6d.ui.roster.RosterKind
import app.d6d.ui.components.ClassIcon
import app.d6d.ui.session.OpenSavedSessionDialog
import app.d6d.ui.session.OpenSessionsPanel
import app.d6d.ui.session.SessionWorkspace
import app.d6d.ui.theme.GoldenRule
import app.d6d.ui.theme.Palette

/** Configuratore del prossimo combattimento, alimentato dal Compendio. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EncounterBuilderScreen(
    viewModel: EncounterBuilderViewModel,
    compact: Boolean,
    workspace: SessionWorkspace,
    // La presentazione iniziale arriva gia' composta: la difficolta' e' una scelta
    // della procedura, non di chi apre la scheda.
    onStarted: (CombatSession, String, Map<String, String>) -> Unit,
    onOpenBattle: () -> Unit,
    onOpenCompendium: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // La procedura guidata non dipende da una partita aperta: quando non ce n'e'
    // nessuna e' anzi il suo caso d'uso principale. L'archivio si sfoglia dal
    // workspace, che esiste sempre.
    var openSavedOpen by remember { mutableStateOf(false) }
    // Fondo trasparente: lascia trasparire il fondale atmosferico condiviso di
    // AppRoot. Intestazione e pannelli hanno superfici proprie e restano leggibili.
    Column(modifier.fillMaxSize()) {
        EncounterHeader(viewModel.step, compact)
        GoldenRule()
        OpenSessionsPanel(
            workspace = workspace,
            onOpenBattle = onOpenBattle,
            onNewSession = { viewModel.restartWizard() },
            compact = compact,
        )
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

        if (openSavedOpen) {
            OpenSavedSessionDialog(
                workspace = workspace,
                onDismiss = { openSavedOpen = false },
                onOpened = {
                    openSavedOpen = false
                    viewModel.restartWizard()
                    onOpenBattle()
                },
            )
        }

        when (viewModel.step) {
            NewGameStep.TEMPLATE -> TemplateChoiceStep(
                viewModel = viewModel,
                compact = compact,
                savedCount = workspace.savedSessions.size,
                onOpenSaved = { openSavedOpen = true },
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
            NewGameStep.DIFFICOLTA -> DifficultyStep(viewModel, compact, Modifier.weight(1f))
        }

        if (viewModel.step != NewGameStep.TEMPLATE) GoldenRule()
        NewGameFooter(viewModel, compact, onStarted, onOpenCompendium)
    }
}

@Composable
private fun EncounterHeader(step: NewGameStep, compact: Boolean) {
    val words = strings.encounter
    Column(
        Modifier.fillMaxWidth().background(Palette.Surface).padding(
            horizontal = if (compact) 12.dp else 18.dp,
            vertical = 11.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = strings.nav.game,
            color = Palette.Text,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = when (step) {
                NewGameStep.TEMPLATE -> words.step(1, 5, words.stepSource)
                NewGameStep.PARTECIPANTI -> words.step(2, 5, words.stepParticipants)
                NewGameStep.GRIGLIA -> words.step(3, 5, words.stepGrid)
                NewGameStep.MODALITA -> words.step(4, 5, words.stepMode)
                NewGameStep.DIFFICOLTA -> words.step(5, 5, words.stepDifficulty)
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
    savedCount: Int,
    onOpenSaved: () -> Unit,
    onCreateFromScratch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val words = strings.encounter
    val people = viewModel.participants.count { it.kind == RosterKind.PERSONAGGIO }
    val creatures = viewModel.participants.count { it.kind != RosterKind.PERSONAGGIO }
    Box(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(if (compact) 12.dp else 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                words.whereToStart,
                color = Palette.Text,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                words.whereToStartBody,
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodyMedium,
            )

            // Le partite incluse stanno in cima: sono la strada piu' corta per
            // avere un tavolo giocabile, e chi apre l'app la prima volta non ha
            // ancora niente di proprio da usare.
            Eyebrow(words.includedGames)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                viewModel.includedTemplates.forEach { template ->
                    GameButton(
                        label = template.name,
                        subtitle = words.templateSummary(
                            template.partyLevel,
                            template.partyCount,
                            template.opponentCount,
                        ),
                        accent = Palette.Gold,
                        onClick = { viewModel.useIncludedTemplate(template) },
                    )
                }
            }
            Text(
                viewModel.includedTemplates.joinToString("\n") { words.templateOpponentLine(it.name, it.summary) },
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )

            Eyebrow(words.orElse)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GameButton(
                    label = words.useExistingTemplates,
                    subtitle = words.peopleAndCreatures(people, creatures),
                    accent = Palette.Party,
                    enabled = people + creatures > 0,
                    onClick = { viewModel.useExistingTemplates() },
                )
                GameButton(
                    label = words.createFromScratch,
                    subtitle = words.createFromScratchHint,
                    accent = Palette.Gold,
                    onClick = onCreateFromScratch,
                )
                GameButton(
                    label = words.openSavedSession,
                    subtitle = when (savedCount) {
                        0 -> words.noSavedSession
                        1 -> words.oneSavedSession
                        else -> words.savedSessions(savedCount)
                    },
                    accent = Palette.Heal,
                    onClick = onOpenSaved,
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
    val words = strings.encounter
    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(
                horizontal = if (compact) 10.dp else 18.dp,
                vertical = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Eyebrow(words.gameName)
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
                Chip(words.participants(viewModel.selectedCount), Palette.Gold)
                Chip(words.allies(viewModel.allyCount), Palette.Party)
                Chip(words.opponents(viewModel.opponentCount), Palette.Enemy)
            }
        }

        val people = viewModel.participants.filter { it.kind == RosterKind.PERSONAGGIO }
        val npcs = viewModel.participants.filter { it.kind == RosterKind.NPC }
        val creatures = viewModel.participants.filter { it.kind == RosterKind.CREATURA }
        if (people.isEmpty() && npcs.isEmpty() && creatures.isEmpty()) {
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
                    item { Eyebrow(words.charactersCount(people.size), Palette.Party) }
                    items(people, key = { "personaggio-${it.id}" }) { ParticipantCard(it, viewModel) }
                }
                if (creatures.isNotEmpty()) {
                    item { Eyebrow(words.creaturesCount(creatures.size), Palette.Enemy, Modifier.padding(top = 7.dp)) }
                    items(creatures, key = { "creatura-${it.id}" }) { ParticipantCard(it, viewModel) }
                }
                if (npcs.isNotEmpty()) {
                    item { Eyebrow(strings.compendium.npcsCount(npcs.size), Palette.Gold, Modifier.padding(top = 7.dp)) }
                    items(npcs, key = { "npc-${it.id}" }) { ParticipantCard(it, viewModel) }
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
    val words = strings.encounter
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
            participant.item.classId?.let { ClassIcon(it, size = 30.dp) }
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
                when (participant.kind) {
                    RosterKind.PERSONAGGIO -> strings.compendium.characterLabel
                    RosterKind.NPC -> strings.compendium.npcLabel
                    RosterKind.CREATURA -> strings.compendium.creatureLabel
                },
                when (participant.kind) {
                    RosterKind.PERSONAGGIO -> Palette.Party
                    RosterKind.NPC -> Palette.Gold
                    RosterKind.CREATURA -> Palette.Enemy
                },
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
                Chip(words.quantity(participant.quantity), Palette.Text)
                GameButton(
                    label = "+",
                    accent = Palette.TextMuted,
                    onClick = { viewModel.changeQuantity(participant.id, 1) },
                )
                EncounterFaction.entries.forEach { faction ->
                    val selected = participant.faction == faction
                    GameButton(
                        label = faction.label(strings),
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
    val words = strings.encounter
    Box(modifier.padding(18.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = if (creatingFromScratch) words.emptyCompendiumBody else words.emptyCompendiumTitle,
                color = Palette.Text,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (creatingFromScratch) {
                    words.emptyCompendiumHint
                } else {
                    words.emptyCompendiumRequirement
                },
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            GameButton(words.openCompendium, accent = Palette.Party, onClick = onOpenCompendium)
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
    val words = strings.encounter
    val language = currentLanguage
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(if (compact) 12.dp else 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            words.gridSize,
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
                    label = words.gridDimensions(columns, rows),
                    subtitle = if (columns == 20 && rows == 15) words.defaultFeminine else null,
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
            GameButton(words.fewerColumns, accent = Palette.TextMuted, onClick = {
                viewModel.updateGridColumns(viewModel.gridColumns - 1)
            })
            Chip(words.columnsCount(viewModel.gridColumns), Palette.Text)
            GameButton(words.moreColumns, accent = Palette.TextMuted, onClick = {
                viewModel.updateGridColumns(viewModel.gridColumns + 1)
            })
            GameButton(words.fewerRows, accent = Palette.TextMuted, onClick = {
                viewModel.updateGridRows(viewModel.gridRows - 1)
            })
            Chip(words.rowsCount(viewModel.gridRows), Palette.Text)
            GameButton(words.moreRows, accent = Palette.TextMuted, onClick = {
                viewModel.updateGridRows(viewModel.gridRows + 1)
            })
        }

        Text(
            words.squareScaleLabel,
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
                    label = words.scalePerSquare(distanceLabel(feet, language)),
                    subtitle = if (feet == 5) words.defaultMasculine else null,
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
            GameButton(words.decreaseScale(distanceLabel(1, language)), accent = Palette.TextMuted, onClick = {
                viewModel.updateFeetPerSquare(viewModel.feetPerSquare - 1)
            })
            Chip(words.chosenScale(distanceLabel(viewModel.feetPerSquare, language)), Palette.GoldBright)
            GameButton(words.increaseScale(distanceLabel(1, language)), accent = Palette.TextMuted, onClick = {
                viewModel.updateFeetPerSquare(viewModel.feetPerSquare + 1)
            })
        }
        val width = distanceLabel(viewModel.gridColumns * viewModel.feetPerSquare, language)
        val height = distanceLabel(viewModel.gridRows * viewModel.feetPerSquare, language)
        Text(
            words.totalArea(width, height, viewModel.gridColumns, viewModel.gridRows),
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
    val words = strings.encounter
    Box(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(if (compact) 12.dp else 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                words.howToStart,
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
                        label = mode.label(strings),
                        subtitle = mode.description(strings),
                        accent = if (selected) Palette.GoldBright else Palette.TextMuted,
                        selected = selected,
                        onClick = { viewModel.mode = mode },
                    )
                }
            }
            Text(
                if (viewModel.mode == EncounterMode.FIGHT) {
                    words.fightPlacementNote
                } else {
                    words.fullPlacementNote
                },
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Ultimo passaggio: rende esplicito quanto la CPU coordina lo schieramento nemico. */
@Composable
private fun DifficultyStep(
    viewModel: EncounterBuilderViewModel,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val strings = strings
    val words = strings.encounter
    Box(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(if (compact) 12.dp else 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                words.howDangerous,
                color = Palette.Text,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                words.difficultyBody,
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
            viewModel.enemyCpuInactiveReason?.let { warning ->
                Text(
                    enemyCpuInactiveWarning(warning, strings),
                    color = Palette.Enemy,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
                        .background(Palette.Enemy.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                )
            }
            // La sandbox apre l'elenco perche' e' l'unica voce che spegne la CPU:
            // le tre sotto scelgono soltanto quanto sara' tattica.
            val sandbox = viewModel.enemyCpuDifficulty == null
            GameButton(
                label = words.sandbox,
                subtitle = words.sandboxHint,
                accent = if (sandbox) Palette.Party else Palette.TextMuted,
                selected = sandbox,
                onClick = { viewModel.enemyCpuDifficulty = null },
                modifier = Modifier.fillMaxWidth(),
            )
            EnemyCpuDifficulty.values().forEach { difficulty ->
                val selected = viewModel.enemyCpuDifficulty == difficulty
                GameButton(
                    label = difficulty.label(strings),
                    subtitle = difficulty.comparison(strings),
                    accent = if (selected) difficulty.accent else Palette.TextMuted,
                    selected = selected,
                    onClick = { viewModel.enemyCpuDifficulty = difficulty },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // Il ritmo non si chiede piu' qui: e' una preferenza di chi guarda, non
            // una proprieta' dello scontro, e vive nelle Impostazioni.
        }
    }
}

internal fun enemyCpuInactiveWarning(reason: String, strings: Strings): String =
    strings.encounter.singleFactionNote(reason)

// Il nome dei livelli vive in `ui.state` accanto al modello di battaglia, che lo
// usa per il riepilogo del turno CPU: una copia qui rischiava di divergere da
// quella senza che nulla se ne accorgesse. Restano invece di questa schermata il
// confronto fra livelli e il colore, che nessun altro mostra.
internal fun EnemyCpuDifficulty.comparison(strings: Strings): String = when (this) {
    EnemyCpuDifficulty.EASY -> strings.encounter.easyHint
    EnemyCpuDifficulty.MEDIUM -> strings.encounter.mediumHint
    EnemyCpuDifficulty.SORRY_FOR_YOU -> strings.encounter.sorryForYouHint
}

private val EnemyCpuDifficulty.accent
    get() = when (this) {
        EnemyCpuDifficulty.EASY -> Palette.Heal
        EnemyCpuDifficulty.MEDIUM -> Palette.GoldBright
        EnemyCpuDifficulty.SORRY_FOR_YOU -> Palette.Enemy
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewGameFooter(
    viewModel: EncounterBuilderViewModel,
    compact: Boolean,
    // La presentazione iniziale arriva gia' composta: la difficolta' e' una scelta
    // della procedura, non di chi apre la scheda.
    onStarted: (CombatSession, String, Map<String, String>) -> Unit,
    onOpenCompendium: () -> Unit,
) {
    val strings = strings
    val words = strings.encounter
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
                GameButton(strings.common.back, accent = Palette.TextMuted, onClick = { viewModel.back() })
                GameButton(strings.common.clear, accent = Palette.TextMuted, onClick = { viewModel.clearSelection() })
                if (viewModel.templateSource == TemplateSource.ESISTENTI) {
                    GameButton(words.baseParty, accent = Palette.Party, onClick = { viewModel.resetRecommended() })
                } else {
                    GameButton(words.createMoreFromScratch, accent = Palette.Party, onClick = onOpenCompendium)
                }
                GameButton(
                    label = words.nextGrid,
                    accent = Palette.Heal,
                    enabled = viewModel.canStart,
                    onClick = { viewModel.continueFromParticipants() },
                )
            }
            NewGameStep.GRIGLIA -> {
                GameButton(strings.common.back, accent = Palette.TextMuted, onClick = { viewModel.back() })
                GameButton(words.resetGrid, accent = Palette.TextMuted, onClick = {
                    viewModel.useGridPreset(20, 15)
                    viewModel.updateFeetPerSquare(5)
                })
                GameButton(words.nextMode, accent = Palette.Heal, onClick = { viewModel.continueFromGrid() })
            }
            NewGameStep.MODALITA -> {
                GameButton(strings.common.back, accent = Palette.TextMuted, onClick = { viewModel.back() })
                GameButton(
                    label = words.nextDifficulty,
                    subtitle = words.nextDifficultyHint,
                    accent = Palette.Heal,
                    onClick = { viewModel.continueFromMode() },
                )
            }
            NewGameStep.DIFFICOLTA -> {
                GameButton(strings.common.back, accent = Palette.TextMuted, onClick = { viewModel.back() })
                GameButton(
                    label = words.startGame,
                    subtitle = viewModel.enemyCpuInactiveReason
                        ?: words.startSummary(
                            viewModel.mode.label(strings),
                            viewModel.enemyCpuDifficulty?.let { "CPU ${it.label(strings)}" }
                                ?: words.sandbox,
                        ),
                    accent = Palette.Heal,
                    enabled = viewModel.canStart,
                    onClick = {
                        viewModel.tryStart()?.let { session ->
                            onStarted(
                                session,
                                viewModel.encounterName.trim(),
                                newEncounterPresentation(
                                    viewModel.mode,
                                    viewModel.enemyCpuDifficulty,
                                    viewModel.enemyCpuSpeed,
                                ),
                            )
                        }
                    },
                )
            }
        }
    }
}
