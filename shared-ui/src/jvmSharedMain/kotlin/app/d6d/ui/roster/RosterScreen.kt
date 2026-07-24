package app.d6d.ui.roster

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.d6d.ui.abilities.AbilityArchive
import app.d6d.ui.battle.GameButton
import app.d6d.ui.components.Chip
import app.d6d.ui.components.Eyebrow
import app.d6d.ui.components.PanelScrollbar
import app.d6d.ui.images.PortraitRepository
import app.d6d.ui.maps.MapArchive
import app.d6d.ui.sheet.CharacterSheetEditor
import app.d6d.ui.sheet.MonsterStatBlockEditor
import app.d6d.ui.sheet.SheetKind
import app.d6d.ui.sheet.SheetNavigationResult
import app.d6d.ui.theme.GoldenRule
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
    requestedItemId: String? = null,
    onRequestedItemHandled: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var section by remember { mutableStateOf(RosterSection.SCHEDE) }
    var compactPane by remember { mutableStateOf(CompactRosterPane.LIST) }
    var pendingNavigation by remember { mutableStateOf<RosterNavigation?>(null) }

    val applyNavigation: (RosterNavigation, Boolean) -> SheetNavigationResult = { navigation, discard ->
        when (navigation) {
            is RosterNavigation.Select -> when (navigation.item.kind) {
                RosterKind.PERSONAGGIO ->
                    viewModel.sheets.selectCharacter(navigation.item.id, discardUnsavedChanges = discard)
                RosterKind.CREATURA ->
                    viewModel.sheets.selectMonster(navigation.item.id, discardUnsavedChanges = discard)
            }

            RosterNavigation.NewCharacter -> {
                val kindResult = viewModel.sheets.requestKind(
                    SheetKind.PERSONAGGIO,
                    discardUnsavedChanges = discard,
                )
                if (kindResult == SheetNavigationResult.APPLIED) {
                    viewModel.sheets.newSheet(discardUnsavedChanges = discard)
                } else {
                    kindResult
                }
            }

            RosterNavigation.NewCreature -> {
                val kindResult = viewModel.sheets.requestKind(
                    SheetKind.MOSTRO,
                    discardUnsavedChanges = discard,
                )
                if (kindResult == SheetNavigationResult.APPLIED) {
                    viewModel.sheets.newSheet(discardUnsavedChanges = discard)
                } else {
                    kindResult
                }
            }
        }
    }
    val requestNavigation: (RosterNavigation) -> Unit = { navigation ->
        when (applyNavigation(navigation, false)) {
            SheetNavigationResult.APPLIED -> compactPane = CompactRosterPane.DETAIL
            SheetNavigationResult.UNSAVED_CHANGES -> pendingNavigation = navigation
            SheetNavigationResult.NOT_FOUND,
            SheetNavigationResult.FAILED,
            -> Unit
        }
    }

    LaunchedEffect(requestedItemId) {
        requestedItemId?.let { id ->
            section = RosterSection.SCHEDE
            viewModel.items.firstOrNull { it.id == id }?.let { item ->
                requestNavigation(RosterNavigation.Select(item))
            }
            onRequestedItemHandled()
        }
    }

    val editor: @Composable (Modifier) -> Unit = { editorModifier ->
        when (viewModel.editorKind) {
            RosterKind.PERSONAGGIO ->
                CharacterSheetEditor(viewModel.sheets, portraits, compact, editorModifier)

            RosterKind.CREATURA ->
                MonsterStatBlockEditor(viewModel.sheets, portraits, compact, editorModifier)
        }
    }

    // Fondo trasparente: lascia trasparire il fondale atmosferico condiviso di
    // AppRoot. Intestazione, elenco ed editor hanno superfici proprie e restano leggibili.
    Column(modifier.fillMaxSize()) {
        // In modalità compatta, mentre si modifica una scheda l'intestazione
        // dell'editor gestisce già la navigazione: la barra di sezione si toglie
        // per non impilare due comandi «indietro».
        val editingCompactSheet = compact &&
            section == RosterSection.SCHEDE &&
            compactPane == CompactRosterPane.DETAIL
        if (!editingCompactSheet) {
            RosterSectionBar(section) { section = it }
        }

        when (section) {
            RosterSection.MAPPE -> MapArchive(portraits, compact, Modifier.weight(1f))
            RosterSection.ABILITA -> AbilityArchive(viewModel.sheets, compact, Modifier.weight(1f))

            RosterSection.SCHEDE ->
                if (compact && compactPane == CompactRosterPane.DETAIL) {
                    CompactEditorHeader(viewModel) { compactPane = CompactRosterPane.LIST }
                    GoldenRule()
                    RosterStatus(viewModel)
                    Box(Modifier.weight(1f)) { editor(Modifier.fillMaxSize()) }
                } else {
                    RosterHeader(
                        compact = compact,
                        onNewCharacter = { requestNavigation(RosterNavigation.NewCharacter) },
                        onNewCreature = { requestNavigation(RosterNavigation.NewCreature) },
                    )
                    GoldenRule()
                    RosterStatus(viewModel)

                    if (compact) {
                        RosterList(
                            viewModel = viewModel,
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(7.dp),
                            onSelect = { item -> requestNavigation(RosterNavigation.Select(item)) },
                        )
                    } else {
                        Row(Modifier.weight(1f)) {
                            RosterList(
                                viewModel = viewModel,
                                modifier = Modifier.width(258.dp),
                                onSelect = { item -> requestNavigation(RosterNavigation.Select(item)) },
                            )
                            Box(Modifier.weight(1f)) { editor(Modifier.fillMaxSize()) }
                        }
                    }
                }
        }
    }

    pendingNavigation?.let { navigation ->
        AlertDialog(
            onDismissRequest = { pendingNavigation = null },
            containerColor = Palette.Surface,
            title = { Text("Scartare la bozza?", color = Palette.Text) },
            text = {
                Text(
                    "La scheda contiene modifiche non salvate. Continuando verranno perse.",
                    color = Palette.TextMuted,
                )
            },
            confirmButton = {
                GameButton("Scarta e continua", accent = Palette.Enemy, onClick = {
                    if (applyNavigation(navigation, true) == SheetNavigationResult.APPLIED) {
                        compactPane = CompactRosterPane.DETAIL
                    }
                    pendingNavigation = null
                })
            },
            dismissButton = {
                GameButton("Annulla", accent = Palette.TextMuted, onClick = { pendingNavigation = null })
            },
        )
    }
}

private enum class CompactRosterPane { LIST, DETAIL }

/** Sezioni del Compendio: attori, capacità riusabili e archivio delle mappe. */
private enum class RosterSection(val label: String) {
    SCHEDE("Schede"),
    ABILITA("Abilità"),
    MAPPE("Mappe"),
}

/** Barra in cima al Compendio per passare fra i tre archivi. */
@Composable
private fun RosterSectionBar(current: RosterSection, onSelect: (RosterSection) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.Abyss)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RosterSection.entries.forEach { entry ->
            GameButton(
                label = entry.label,
                accent = if (current == entry) Palette.Gold else Palette.TextMuted,
                selected = current == entry,
                dense = true,
                onClick = { onSelect(entry) },
            )
        }
    }
}

private sealed interface RosterNavigation {
    data class Select(val item: RosterItem) : RosterNavigation
    data object NewCharacter : RosterNavigation
    data object NewCreature : RosterNavigation
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RosterHeader(
    compact: Boolean,
    onNewCharacter: () -> Unit,
    onNewCreature: () -> Unit,
) {
    if (compact) {
        Column(
            Modifier.fillMaxWidth().background(Palette.Surface).padding(14.dp, 10.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            RosterTitle()
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                GameButton("+ Personaggio", accent = Palette.Party, onClick = onNewCharacter)
                GameButton("+ Creatura", accent = Palette.Enemy, onClick = onNewCreature)
            }
        }
    } else {
        Row(
            Modifier.fillMaxWidth().background(Palette.Surface).padding(14.dp, 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RosterTitle(Modifier.weight(1f))
            GameButton("+ Personaggio", accent = Palette.Party, onClick = onNewCharacter)
            GameButton("+ Creatura", accent = Palette.Enemy, onClick = onNewCreature)
        }
    }
}

@Composable
private fun RosterTitle(modifier: Modifier = Modifier) {
    Column(modifier) {
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
}

@Composable
private fun CompactEditorHeader(viewModel: RosterViewModel, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Palette.Surface).padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameButton("← Compendio", accent = Palette.TextMuted, onClick = onBack)
        Column(Modifier.weight(1f)) {
            Text(
                text = if (viewModel.editorKind == RosterKind.PERSONAGGIO) "Scheda personaggio" else "Stat block",
                color = Palette.Text,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (viewModel.selectedId == null) "Nuovo elemento" else "Modifica elemento",
                color = Palette.TextMuted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun RosterStatus(viewModel: RosterViewModel) {
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
}

@Composable
private fun RosterList(
    viewModel: RosterViewModel,
    modifier: Modifier = Modifier,
    onSelect: (RosterItem) -> Unit,
) {
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
        val listState = rememberLazyListState()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (people.isNotEmpty()) {
                    item { Eyebrow("Personaggi (${people.size})", color = Palette.Party) }
                    items(people) { RosterRow(it, viewModel, onSelect) }
                }
                if (creatures.isNotEmpty()) {
                    item {
                        Eyebrow(
                            "Creature (${creatures.size})",
                            color = Palette.Enemy,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    items(creatures) { RosterRow(it, viewModel, onSelect) }
                }
            }
            PanelScrollbar(listState, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}

@Composable
private fun RosterRow(
    item: RosterItem,
    viewModel: RosterViewModel,
    onSelect: (RosterItem) -> Unit,
) {
    val selected = viewModel.selectedId == item.id && viewModel.editorKind == item.kind
    val accent = if (item.kind == RosterKind.PERSONAGGIO) Palette.Party else Palette.Enemy

    Column(
        Modifier.fillMaxWidth()
            .background(if (selected) Palette.SurfaceHigh else Color.Transparent, RoundedCornerShape(8.dp))
            .border(1.dp, if (selected) Palette.Gold else Palette.Line, RoundedCornerShape(8.dp))
            .clickable { onSelect(item) }
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
