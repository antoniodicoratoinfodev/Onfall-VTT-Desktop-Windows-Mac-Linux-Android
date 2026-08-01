package app.d6d.ui.session

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.d6d.persistence.session.SessionSummary
import app.d6d.ui.battle.GameButton
import app.d6d.ui.runDiskIo
import app.d6d.ui.components.Chip
import app.d6d.ui.components.Eyebrow
import app.d6d.ui.theme.OrnateDivider
import app.d6d.ui.theme.Palette
import app.d6d.ui.theme.ornateFrame
import app.d6d.ui.theme.panelBrush
import kotlinx.coroutines.launch

/** Pulsante che apre il menù delle sessioni, da mettere nell'intestazione. */
@Composable
fun SessionMenuButton(
    manager: SessionManager,
    modifier: Modifier = Modifier,
    dense: Boolean = false,
    openSessionCount: Int = 1,
    autosaveWarning: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val subtitle = when {
        autosaveWarning && openSessionCount > 1 -> "Autosave da controllare · $openSessionCount aperte"
        autosaveWarning -> "Autosave da controllare"
        manager.hasUnsavedChanges && openSessionCount > 1 -> "Da salvare · $openSessionCount aperte"
        manager.hasUnsavedChanges -> "Da salvare"
        openSessionCount > 1 -> "$openSessionCount aperte"
        else -> null
    }
    GameButton(
        label = "Sessione",
        subtitle = subtitle,
        accent = Palette.Gold,
        dense = dense,
        onClick = {
            manager.menuOpen = true
            scope.launch { runDiskIo { manager.refresh() } }
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
fun SessionMenuDialog(
    manager: SessionManager,
    onLoaded: () -> Unit = {},
    onOpenInNewTab: ((SessionSummary) -> Unit)? = null,
    workspace: SessionWorkspace? = null,
) {
    if (!manager.menuOpen) return

    var name by remember(manager.currentName) { mutableStateOf(manager.currentName) }
    var overwriteName by remember { mutableStateOf<String?>(null) }
    var discardForSession by remember { mutableStateOf<SessionSummary?>(null) }
    var deleteSession by remember { mutableStateOf<SessionSummary?>(null) }
    val scope = rememberCoroutineScope()

    val saveSession: (String) -> Unit = { requestedName ->
        scope.launch {
            val result = runDiskIo { manager.save(requestedName) }
            workspace?.reconcileAutosaveWarning()
            if (result == SessionSaveResult.NAME_COLLISION) {
                overwriteName = requestedName
            }
        }
    }
    val openSession: (SessionSummary) -> Unit = { summary ->
        if (onOpenInNewTab != null) {
            manager.menuOpen = false
            onOpenInNewTab(summary)
        } else {
            scope.launch {
                when (runDiskIo { manager.requestLoad(summary) }) {
                    SessionLoadResult.LOADED -> onLoaded()
                    SessionLoadResult.UNSAVED_CHANGES -> discardForSession = summary
                    SessionLoadResult.FAILED -> Unit
                }
            }
        }
    }

    Dialog(
        onDismissRequest = { manager.menuOpen = false },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            val compact = maxWidth < 460.dp
            val dialogShape = RoundedCornerShape(12.dp)
            Column(
                Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .panelBrush(dialogShape)
                    .border(1.dp, Palette.Bronze.copy(alpha = 0.6f), dialogShape)
                    .ornateFrame(accent = Palette.Gold, alpha = 0.5f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            Text(
                text = "Sessioni",
                color = Palette.Text,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleLarge,
            )
            OrnateDivider(color = Palette.GoldDim)
            Text(
                text = "Una sessione salvata conserva combattimento, mappa, segnaposti, " +
                    "registro completo e stato dei dadi: riaprendola i tiri futuri sono gli stessi." +
                    if (onOpenInNewTab != null) {
                        " Viene aperta in una scheda indipendente senza chiudere le altre."
                    } else {
                        ""
                    },
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

            workspace?.status?.takeIf { it != manager.status }?.let {
                Text(
                    text = it,
                    color = Palette.Gold,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Palette.Gold.copy(alpha = 0.10f), RoundedCornerShape(5.dp))
                        .clickable { workspace.dismissStatus() }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                )
            }

            workspace?.autosaveWarning?.let {
                Text(
                    text = it,
                    color = Palette.Bloodied,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Palette.Bloodied.copy(alpha = 0.10f), RoundedCornerShape(5.dp))
                        .clickable { workspace.dismissAutosaveWarning() }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                )
            }

            if (manager.hasUnsavedChanges) {
                Text(
                    text = "Ci sono modifiche alla battaglia non ancora salvate.",
                    color = Palette.Bloodied,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Palette.Bloodied.copy(alpha = 0.10f), RoundedCornerShape(5.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                )
            }

            if (workspace != null && workspace.openSessions.size > 1) {
                Eyebrow("Sessioni preparate (${workspace.openSessions.size})")
                Text(
                    "Passa a un'altra mappa senza chiudere o ricaricare la partita corrente.",
                    color = Palette.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (compact) {
                    Column(
                        Modifier.selectableGroup(),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        workspace.openSessions.forEach { opened ->
                            OpenSessionButton(
                                opened = opened,
                                selected = opened.id == workspace.activeSession.id,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    manager.menuOpen = false
                                    workspace.activate(opened.id)
                                },
                            )
                        }
                    }
                } else {
                    FlowRow(
                        Modifier.selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        workspace.openSessions.forEach { opened ->
                            OpenSessionButton(
                                opened = opened,
                                selected = opened.id == workspace.activeSession.id,
                                onClick = {
                                    manager.menuOpen = false
                                    workspace.activate(opened.id)
                                },
                            )
                        }
                    }
                }
                OrnateDivider(color = Palette.GoldDim)
            }

            Eyebrow("Salva con nome")
                if (compact) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SessionNameField(name, onNameChange = { name = it })
                        GameButton(
                            label = "Salva",
                            accent = Palette.Heal,
                            enabled = name.isNotBlank(),
                            onClick = { saveSession(name) },
                        )
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SessionNameField(name, onNameChange = { name = it }, modifier = Modifier.weight(1f))
                        GameButton(
                            label = "Salva",
                            accent = Palette.Heal,
                            enabled = name.isNotBlank(),
                            onClick = { saveSession(name) },
                        )
                    }
                }

            Eyebrow("Sessioni salvate (${manager.sessions.size})")
            if (manager.sessions.isEmpty()) {
                Text(
                    text = "Nessuna sessione salvata.",
                    color = Palette.TextFaint,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    manager.sessions.forEach { summary ->
                        SessionRow(
                            summary = summary,
                            compact = compact,
                            openLabel = if (onOpenInNewTab != null) "Apri in scheda" else "Apri",
                            onOpen = openSession,
                            onDelete = { deleteSession = it },
                        )
                    }
                }
            }

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                GameButton("Chiudi", accent = Palette.TextMuted, onClick = { manager.menuOpen = false })
            }
        }
        }
    }

    overwriteName?.let { requestedName ->
        AlertDialog(
            onDismissRequest = { overwriteName = null },
            containerColor = Palette.Surface,
            title = { Text("Sostituire la sessione?", color = Palette.Text) },
            text = {
                Text(
                    "Esiste già una sessione con questo nome. Il salvataggio precedente verrà sostituito.",
                    color = Palette.TextMuted,
                )
            },
            confirmButton = {
                GameButton("Sostituisci", accent = Palette.Enemy, onClick = {
                    scope.launch {
                        runDiskIo { manager.save(requestedName, overwriteExisting = true) }
                        workspace?.reconcileAutosaveWarning()
                        overwriteName = null
                    }
                })
            },
            dismissButton = {
                GameButton("Annulla", accent = Palette.TextMuted, onClick = { overwriteName = null })
            },
        )
    }

    discardForSession?.let { summary ->
        AlertDialog(
            onDismissRequest = { discardForSession = null },
            containerColor = Palette.Surface,
            title = { Text("Scartare le modifiche?", color = Palette.Text) },
            text = {
                Text(
                    "Aprendo «${summary.displayName}» perderai le modifiche non salvate della battaglia corrente.",
                    color = Palette.TextMuted,
                )
            },
            confirmButton = {
                GameButton("Scarta e apri", accent = Palette.Enemy, onClick = {
                    scope.launch {
                        if (
                            runDiskIo { manager.requestLoad(summary, discardUnsavedChanges = true) } ==
                            SessionLoadResult.LOADED
                        ) {
                            onLoaded()
                        }
                        discardForSession = null
                    }
                })
            },
            dismissButton = {
                GameButton("Annulla", accent = Palette.TextMuted, onClick = { discardForSession = null })
            },
        )
    }

    deleteSession?.let { summary ->
        AlertDialog(
            onDismissRequest = { deleteSession = null },
            containerColor = Palette.Surface,
            title = { Text("Eliminare la sessione?", color = Palette.Text) },
            text = {
                Text(
                    "«${summary.displayName}» verrà eliminata definitivamente dal dispositivo.",
                    color = Palette.TextMuted,
                )
            },
            confirmButton = {
                GameButton("Elimina", accent = Palette.Enemy, onClick = {
                    scope.launch {
                        runDiskIo { manager.delete(summary) }
                        workspace?.reconcileAutosaveWarning()
                        deleteSession = null
                    }
                })
            },
            dismissButton = {
                GameButton("Annulla", accent = Palette.TextMuted, onClick = { deleteSession = null })
            },
        )
    }
}

@Composable
private fun OpenSessionButton(
    opened: OpenGameSession,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = when {
        opened.manager.currentSlug == null -> "Bozza"
        opened.manager.hasUnsavedChanges -> "Da salvare"
        else -> "Round ${opened.battle.round}"
    }
    GameButton(
        label = shortDisplayName(opened),
        subtitle = state,
        selected = selected,
        role = Role.Tab,
        accent = if (opened.manager.hasUnsavedChanges) Palette.Bloodied else Palette.Gold,
        modifier = modifier,
        onClick = onClick,
    )
}

@Composable
private fun SessionNameField(
    name: String,
    onNameChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = name,
        onValueChange = onNameChange,
        singleLine = true,
        textStyle = TextStyle(color = Palette.Text, fontSize = 13.sp),
        cursorBrush = SolidColor(Palette.Gold),
        modifier = modifier
            .fillMaxWidth()
            .background(Palette.Night, RoundedCornerShape(5.dp))
            .border(1.dp, Palette.Line, RoundedCornerShape(5.dp))
            .padding(horizontal = 8.dp, vertical = 7.dp),
    )
}

@Composable
private fun SessionRow(
    summary: SessionSummary,
    compact: Boolean,
    openLabel: String,
    onOpen: (SessionSummary) -> Unit,
    onDelete: (SessionSummary) -> Unit,
) {
    val damaged = summary.status == "ILLEGGIBILE"

    val container = Modifier
            .fillMaxWidth()
            .background(Palette.Night, RoundedCornerShape(8.dp))
            .border(1.dp, if (damaged) Palette.Critical else Palette.Line, RoundedCornerShape(8.dp))
            .padding(9.dp)

    if (compact) {
        Column(container, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SessionSummaryContent(summary, damaged)
            SessionActions(summary, damaged, openLabel, onOpen, onDelete)
        }
    } else {
        Row(
            container,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SessionSummaryContent(summary, damaged, Modifier.weight(1f))
            if (!damaged) {
                GameButton(openLabel, accent = Palette.Party, onClick = { onOpen(summary) })
            }
            GameButton("Elimina", accent = Palette.Enemy, onClick = { onDelete(summary) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionSummaryContent(
    summary: SessionSummary,
    damaged: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = summary.displayName,
                color = if (damaged) Palette.Critical else Palette.Text,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionActions(
    summary: SessionSummary,
    damaged: Boolean,
    openLabel: String,
    onOpen: (SessionSummary) -> Unit,
    onDelete: (SessionSummary) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (!damaged) {
            GameButton(openLabel, accent = Palette.Party, onClick = { onOpen(summary) })
        }
        GameButton("Elimina", accent = Palette.Enemy, onClick = { onDelete(summary) })
    }
}
