package app.d6d.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.d6d.ui.battle.GameButton
import app.d6d.ui.components.Chip
import app.d6d.ui.components.Eyebrow
import app.d6d.ui.runDiskIo
import app.d6d.ui.theme.Palette
import kotlinx.coroutines.launch

/**
 * Pannello delle partite mantenute vive contemporaneamente.
 *
 * Selezionare una scheda cambia soltanto il documento attivo; nessun BattleViewModel
 * viene ricreato o adottato sopra un altro. Salvataggio e chiusura si riferiscono
 * sempre alla scheda evidenziata.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OpenSessionsPanel(
    workspace: SessionWorkspace,
    onOpenBattle: () -> Unit,
    onNewSession: () -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var closeCandidate by remember { mutableStateOf<OpenGameSession?>(null) }
    var compactMenuOpen by remember { mutableStateOf(false) }
    var closeAfterSaveId by remember { mutableStateOf<String?>(null) }
    val active = workspace.activeSession
    val listState = rememberLazyListState()
    val activeIndex = workspace.openSessions.indexOfFirst { it.id == active.id }

    LaunchedEffect(compact, active.id, workspace.openSessions.size) {
        if (!compact && activeIndex >= 0) listState.animateScrollToItem(activeIndex)
    }

    val pendingClose = workspace.openSessions.firstOrNull { it.id == closeAfterSaveId }
    LaunchedEffect(
        closeAfterSaveId,
        pendingClose?.manager?.menuOpen,
        pendingClose?.manager?.hasUnsavedChanges,
    ) {
        val pendingId = closeAfterSaveId ?: return@LaunchedEffect
        if (pendingClose == null) {
            closeAfterSaveId = null
        } else if (!pendingClose.manager.menuOpen) {
            if (!pendingClose.manager.hasUnsavedChanges && pendingClose.manager.currentSlug != null) {
                workspace.requestClose(pendingId)
            }
            closeAfterSaveId = null
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .background(Palette.Surface.copy(alpha = 0.92f))
            .padding(
                horizontal = 12.dp,
                vertical = if (compact) 6.dp else 9.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (compact) {
            GameButton(
                label = shortDisplayName(active),
                subtitle = "${workspace.openSessions.size} aperte · ${saveState(active)} · Gestisci",
                accent = if (active.manager.hasUnsavedChanges) Palette.Bloodied else Palette.Gold,
                selected = true,
                modifier = Modifier.fillMaxWidth(),
                onClick = { compactMenuOpen = true },
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Eyebrow("Sessioni aperte")
                Chip("${workspace.openSessions.size}", Palette.Gold)
                Text(
                    "Mappe, turni, dadi, registro e Annulla restano indipendenti.",
                    color = Palette.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(workspace.openSessions, key = { it.id }) { opened ->
                    val selected = opened.id == active.id
                    GameButton(
                        label = shortDisplayName(opened),
                        subtitle = saveState(opened),
                        accent = when {
                            selected -> Palette.Gold
                            opened.manager.hasUnsavedChanges -> Palette.Bloodied
                            else -> Palette.TextMuted
                        },
                        selected = selected,
                        role = Role.Tab,
                        dense = true,
                        modifier = Modifier.widthIn(min = 160.dp, max = 260.dp),
                        onClick = { workspace.activate(opened.id) },
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GameButton("Vai alla mappa", accent = Palette.Party, dense = true, onClick = onOpenBattle)
                GameButton("Salva / gestisci", accent = Palette.Heal, dense = true, onClick = {
                    active.manager.menuOpen = true
                    scope.launch { runDiskIo { active.manager.refresh() } }
                })
                GameButton(
                    label = "Chiudi scheda",
                    accent = Palette.TextMuted,
                    dense = true,
                    enabled = workspace.openSessions.size > 1,
                    onClick = {
                        when (workspace.requestClose(active.id)) {
                            WorkspaceCloseResult.UNSAVED_CHANGES -> closeCandidate = active
                            else -> Unit
                        }
                    },
                )
                GameButton("Nuova partita", accent = Palette.Gold, dense = true, onClick = onNewSession)
            }
        }

        workspace.status?.let { message ->
            Text(
                text = message,
                color = Palette.GoldBright,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Palette.Gold.copy(alpha = 0.10f), RoundedCornerShape(5.dp))
                    .clickable { workspace.dismissStatus() }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            )
        }

        workspace.autosaveWarning?.let { message ->
            Text(
                text = message,
                color = Palette.Bloodied,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Palette.Bloodied.copy(alpha = 0.10f), RoundedCornerShape(5.dp))
                    .clickable { workspace.dismissAutosaveWarning() }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            )
        }
    }

    if (compactMenuOpen) {
        AlertDialog(
            onDismissRequest = { compactMenuOpen = false },
            containerColor = Palette.Surface,
            title = {
                Text(
                    "Sessioni aperte · ${workspace.openSessions.size}",
                    color = Palette.Text,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        // Lascia spazio a titolo e azioni anche in landscape;
                        // l'elenco interno resta scorrevole con qualunque numero di tab.
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Text(
                        "Seleziona una partita. Ogni scheda conserva la propria mappa e il proprio combattimento.",
                        color = Palette.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Eyebrow("Azioni sulla sessione attiva")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        GameButton("Vai alla mappa", accent = Palette.Party, onClick = {
                            compactMenuOpen = false
                            onOpenBattle()
                        })
                        GameButton("Salva / gestisci", accent = Palette.Heal, onClick = {
                            compactMenuOpen = false
                            active.manager.menuOpen = true
                            scope.launch { runDiskIo { active.manager.refresh() } }
                        })
                        GameButton(
                            label = "Chiudi scheda",
                            accent = Palette.TextMuted,
                            enabled = workspace.openSessions.size > 1,
                            onClick = {
                                compactMenuOpen = false
                                when (workspace.requestClose(active.id)) {
                                    WorkspaceCloseResult.UNSAVED_CHANGES -> closeCandidate = active
                                    else -> Unit
                                }
                            },
                        )
                        GameButton("Nuova partita", accent = Palette.Gold, onClick = {
                            compactMenuOpen = false
                            onNewSession()
                        })
                    }

                    Eyebrow("Cambia sessione")
                    Column(
                        Modifier.fillMaxWidth().selectableGroup(),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        workspace.openSessions.forEach { opened ->
                            GameButton(
                                label = shortDisplayName(opened),
                                subtitle = saveState(opened),
                                accent = if (opened.manager.hasUnsavedChanges) Palette.Bloodied else Palette.Gold,
                                selected = opened.id == active.id,
                                role = Role.Tab,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    workspace.activate(opened.id)
                                    compactMenuOpen = false
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                GameButton("Fine", accent = Palette.TextMuted, onClick = {
                    compactMenuOpen = false
                })
            },
        )
    }

    closeCandidate?.let { opened ->
        val isDraft = opened.manager.currentSlug == null
        AlertDialog(
            onDismissRequest = { closeCandidate = null },
            containerColor = Palette.Surface,
            title = {
                Text(
                    if (isDraft) "Bozza non salvata" else "Modifiche non salvate",
                    color = Palette.Text,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    if (isDraft) {
                        "«${opened.displayName}» non è mai stata salvata. Chiudendo la scheda la bozza verrà persa."
                    } else {
                        "«${opened.displayName}» rimarrà nell'archivio solo fino all'ultimo salvataggio. " +
                            "La chiusura della scheda non elimina il file salvato."
                    },
                    color = Palette.TextMuted,
                )
            },
            confirmButton = {
                GameButton("Salva prima", accent = Palette.Heal, onClick = {
                    workspace.activate(opened.id)
                    opened.manager.menuOpen = true
                    scope.launch { runDiskIo { opened.manager.refresh() } }
                    closeAfterSaveId = opened.id
                    closeCandidate = null
                })
            },
            dismissButton = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    GameButton("Annulla", accent = Palette.TextMuted, onClick = {
                        closeCandidate = null
                    })
                    GameButton("Chiudi senza salvare", accent = Palette.Enemy, onClick = {
                        workspace.requestClose(opened.id, discardUnsavedChanges = true)
                        closeCandidate = null
                    })
                }
            },
        )
    }
}

private fun saveState(opened: OpenGameSession): String = when {
    opened.manager.currentSlug == null -> "Bozza da salvare"
    opened.manager.hasUnsavedChanges -> "Modifiche da salvare"
    else -> "Salvata · round ${opened.battle.round}"
}

internal fun shortDisplayName(opened: OpenGameSession, maxLength: Int = 30): String {
    val name = opened.displayName.ifBlank { "Partita senza nome" }
    return if (name.length <= maxLength) name else name.take(maxLength - 1).trimEnd() + "…"
}
