package app.d6d.ui.battle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.d6d.board.BoardLimits
import app.d6d.board.SceneToken
import app.d6d.board.TokenCategory
import app.d6d.board.TokenLootCategory
import app.d6d.ui.board.BoardController
import app.d6d.ui.board.BoardToolState
import app.d6d.ui.board.SceneTokenDraft
import app.d6d.ui.components.initials
import app.d6d.ui.components.DialogTitle
import app.d6d.ui.components.Eyebrow
import app.d6d.ui.i18n.BoardStrings
import app.d6d.ui.i18n.strings
import app.d6d.ui.images.PortraitRepository
import app.d6d.ui.images.FramedPortraitImage
import app.d6d.ui.images.PortraitFramingEditor
import app.d6d.ui.images.rememberPortrait
import app.d6d.ui.roster.RosterViewModel
import app.d6d.ui.state.BattleViewModel
import app.d6d.ui.settings.LocalAppPreferences
import app.d6d.ui.theme.OnfallTheme
import app.d6d.ui.theme.Palette
import java.util.UUID
import app.d6d.sheet.PortraitFraming

/** Popup unico per creare una pedina di scena o modificarne i metadati. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SceneTokenDialogHost(
    state: BoardToolState,
    board: BoardController,
    portraits: PortraitRepository,
    battle: BattleViewModel,
    roster: RosterViewModel,
    compact: Boolean,
) {
    val request = state.tokenDialogId ?: return
    val creating = request == BoardToolState.NEW_TOKEN_DIALOG
    val existing = if (creating) null else board.document.objects()
        .filterIsInstance<SceneToken>()
        .firstOrNull { it.id() == request }
    if (!creating && existing == null) {
        LaunchedEffect(request) { state.closeTokenDialog() }
        return
    }

    val words = strings.board
    val preferences = LocalAppPreferences.current
    var name by remember(request) { mutableStateOf(existing?.name().orEmpty()) }
    var category by remember(request) { mutableStateOf(existing?.category() ?: TokenCategory.OBJECT) }
    var sizeSquares by remember(request) { mutableStateOf(existing?.sizeSquares() ?: 1.0) }
    var colorArgb by remember(request) { mutableStateOf(existing?.colorArgb() ?: preferences.boardColorArgb) }
    var showLabel by remember(request) { mutableStateOf(existing?.showLabel() ?: true) }
    var visibleToPlayers by remember(request) { mutableStateOf(existing?.visibleToPlayers() ?: true) }
    var lootable by remember(request) { mutableStateOf(existing?.lootable() ?: false) }
    var lootCategory by remember(request) {
        mutableStateOf(existing?.lootCategory() ?: TokenLootCategory.MISC)
    }
    var lootQuantity by remember(request) { mutableStateOf(existing?.lootQuantity()?.toString() ?: "1") }
    var lootDescription by remember(request) { mutableStateOf(existing?.lootDescription().orEmpty()) }
    var notes by remember(request) { mutableStateOf(existing?.notes().orEmpty()) }
    val collectors = eligibleLootCollectors(battle, roster)
    var collectorId by remember(request) { mutableStateOf(collectors.firstOrNull()?.characterId) }
    // Conserva l'esito, non la frase già tradotta: cambiando lingua anche un
    // errore rimasto aperto viene ridisegnato nel nuovo vocabolario.
    var lootResult by remember(request) { mutableStateOf<LootTransferResult?>(null) }
    var removeOriginalImage by remember(request) { mutableStateOf(false) }
    val originalAssetId = existing?.imageAssetId().orEmpty()
    var imageFraming by remember(request) {
        mutableStateOf(portraits.portraitFraming(originalAssetId))
    }
    var framingEditorOpen by remember(request) { mutableStateOf(false) }
    val candidateAssetId = remember(request) { "scene-token-image-${UUID.randomUUID()}" }
    val retainCandidate = remember(request) { mutableStateOf(false) }
    val dialogActive = remember(request) { mutableStateOf(true) }

    // L'import candidato è separato dall'asset esistente: Annulla non modifica una
    // pedina già salvata. Un candidato non confermato viene ripulito alla chiusura.
    @Suppress("UNUSED_EXPRESSION")
    portraits.revision
    val candidateStored = portraits.portraitName(candidateAssetId) != null
    val candidateImage = portraits.rememberPortrait(candidateAssetId)
    val originalImage = portraits.rememberPortrait(originalAssetId)
    val shownImage = candidateImage.takeUnless { removeOriginalImage }
        ?: originalImage.takeUnless { removeOriginalImage }

    DisposableEffect(request, candidateAssetId) {
        onDispose {
            dialogActive.value = false
            if (!retainCandidate.value && portraits.portraitName(candidateAssetId) != null) {
                portraits.clearPortrait(candidateAssetId)
            }
        }
    }

    fun dismiss() {
        state.closeTokenDialog()
        if (creating) state.table()
    }

    AlertDialog(
        onDismissRequest = ::dismiss,
        title = { DialogTitle(if (creating) words.createToken else words.editToken) },
        text = {
            Column(
                Modifier
                    .widthIn(max = 620.dp)
                    .heightIn(max = if (compact) 560.dp else 650.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(words.tokenVisualOnlyHint, color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
                TextField(
                    value = name,
                    onValueChange = { name = it.take(BoardLimits.MAX_TOKEN_NAME_LENGTH) },
                    label = { Text(words.tokenName) },
                    placeholder = { Text(words.tokenNameHint) },
                    singleLine = true,
                )

                Eyebrow(words.tokenCategory)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    TokenCategory.entries.forEach { choice ->
                        GameButton(
                            label = choice.label(words),
                            selected = category == choice,
                            dense = true,
                            modifier = Modifier.sizeIn(minHeight = if (compact) 48.dp else 40.dp),
                            onClick = {
                                category = choice
                                visibleToPlayers = choice !in setOf(TokenCategory.TRAP, TokenCategory.HAZARD)
                                if (choice == TokenCategory.LOOT) lootable = true
                            },
                        )
                    }
                }

                SceneTokenImagePicker(
                    portraits = portraits,
                    candidateAssetId = candidateAssetId,
                    shownImage = shownImage,
                    candidateStored = candidateStored,
                    name = name,
                    color = Color(colorArgb),
                    compact = compact,
                    onImageRequested = {
                        removeOriginalImage = false
                        if (!candidateStored) {
                            imageFraming = portraits.portraitFraming(originalAssetId)
                        }
                    },
                    onImageImportFinished = { imported ->
                        if (imported && !dialogActive.value && !retainCandidate.value) {
                            portraits.clearPortrait(candidateAssetId)
                        } else if (imported) {
                            imageFraming = PortraitFraming.DEFAULT
                            framingEditorOpen = true
                        }
                    },
                    onRemove = {
                        removeOriginalImage = true
                        imageFraming = PortraitFraming.DEFAULT
                        framingEditorOpen = false
                        if (candidateStored) portraits.clearPortrait(candidateAssetId)
                    },
                    framing = imageFraming,
                    framingEditorOpen = framingEditorOpen,
                    onFramingEditorOpenChange = { framingEditorOpen = it },
                    onFramingChange = { imageFraming = it },
                )
                Text(words.tokenImageHint, color = Palette.TextFaint, style = MaterialTheme.typography.bodySmall)

                Eyebrow(words.colour)
                TokenColourChoices(colorArgb, compact) { colorArgb = it }

                Eyebrow(words.tokenSize)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(0.5, 1.0, 2.0, 3.0, 4.0).forEach { size ->
                        GameButton(
                            label = if (size == 0.5) "½" else size.toInt().toString(),
                            selected = sizeSquares == size,
                            dense = true,
                            modifier = Modifier.sizeIn(
                                minWidth = if (compact) 48.dp else 40.dp,
                                minHeight = if (compact) 48.dp else 40.dp,
                            ),
                            onClick = { sizeSquares = size },
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    GameButton(
                        words.showTokenName,
                        selected = showLabel,
                        modifier = Modifier.sizeIn(minHeight = if (compact) 48.dp else 40.dp),
                        dense = true,
                        onClick = { showLabel = !showLabel },
                    )
                    GameButton(
                        words.visibleToPlayers,
                        selected = visibleToPlayers,
                        modifier = Modifier.sizeIn(minHeight = if (compact) 48.dp else 40.dp),
                        dense = true,
                        onClick = { visibleToPlayers = !visibleToPlayers },
                    )
                }

                GameButton(
                    words.lootable,
                    selected = lootable,
                    modifier = Modifier.sizeIn(minHeight = if (compact) 48.dp else 40.dp),
                    dense = true,
                    onClick = { lootable = !lootable },
                )
                if (lootable) {
                    Text(
                        words.lootSettingsHint,
                        color = Palette.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Eyebrow(words.lootInventoryCategory)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        TokenLootCategory.entries.forEach { choice ->
                            GameButton(
                                label = choice.label(strings),
                                selected = lootCategory == choice,
                                dense = true,
                                modifier = Modifier.sizeIn(minHeight = if (compact) 48.dp else 40.dp),
                                onClick = { lootCategory = choice },
                            )
                        }
                    }
                    TextField(
                        value = lootQuantity,
                        onValueChange = { value -> lootQuantity = value.filter(Char::isDigit).take(4) },
                        label = { Text(words.lootQuantity) },
                        singleLine = true,
                    )
                    TextField(
                        value = lootDescription,
                        onValueChange = {
                            lootDescription = it.take(BoardLimits.MAX_TOKEN_LOOT_DESCRIPTION_LENGTH)
                        },
                        label = { Text(words.lootDescription) },
                        placeholder = { Text(words.lootDescriptionHint) },
                        minLines = 2,
                        maxLines = 4,
                    )

                    if (!creating) {
                        Eyebrow(words.collectLoot)
                        Text(words.collectLootHint, color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
                        if (collectors.isEmpty()) {
                            Text(words.noLootCollectors, color = Palette.TextFaint, style = MaterialTheme.typography.bodySmall)
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                collectors.forEach { collector ->
                                    GameButton(
                                        label = collector.name,
                                        selected = collectorId == collector.characterId,
                                        dense = true,
                                        modifier = Modifier.sizeIn(minHeight = if (compact) 48.dp else 40.dp),
                                        onClick = { collectorId = collector.characterId },
                                    )
                                }
                            }
                        }
                        lootResult?.message(words)?.let { message ->
                            Text(message, color = Palette.Bloodied, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                TextField(
                    value = notes,
                    onValueChange = { notes = it.take(BoardLimits.MAX_TOKEN_NOTES_LENGTH) },
                    label = { Text(words.masterNotes) },
                    placeholder = { Text(words.masterNotesHint) },
                    minLines = 2,
                    maxLines = 4,
                )
            }
        },
        confirmButton = {
            GameButton(
                label = if (creating) words.createAndPlace else strings.common.apply,
                enabled = name.isNotBlank() && (!lootable || lootQuantity.toIntOrNull() in 1..BoardLimits.MAX_TOKEN_LOOT_QUANTITY),
                onClick = {
                    val imageAssetId = when {
                        candidateStored && !removeOriginalImage -> candidateAssetId
                        removeOriginalImage -> ""
                        else -> originalAssetId
                    }
                    if (imageAssetId.isNotBlank()) {
                        portraits.setPortraitFraming(imageAssetId, imageFraming)
                    }
                    val draft = SceneTokenDraft(
                        name = name.trim(),
                        category = category,
                        sizeSquares = sizeSquares.coerceIn(
                            BoardLimits.MIN_TOKEN_SIZE_SQUARES,
                            BoardLimits.MAX_TOKEN_SIZE_SQUARES,
                        ),
                        colorArgb = colorArgb,
                        imageAssetId = imageAssetId,
                        showLabel = showLabel,
                        visibleToPlayers = visibleToPlayers,
                        lootable = lootable,
                        lootCategory = lootCategory,
                        lootQuantity = lootQuantity.toIntOrNull() ?: 1,
                        lootDescription = lootDescription.trim(),
                        notes = notes.trim(),
                    )
                    if (creating) {
                        retainCandidate.value = candidateStored && !removeOriginalImage
                        state.prepareToken(draft)
                    } else {
                        val token = requireNotNull(existing)
                        val replaced = board.replace(
                            SceneToken(
                                token.id(), draft.name, draft.category, token.position(), draft.sizeSquares,
                                token.rotationDegrees(), draft.colorArgb, draft.imageAssetId, draft.showLabel,
                                draft.visibleToPlayers, draft.lootable, draft.lootCategory, draft.lootQuantity,
                                draft.lootDescription, draft.notes,
                            ),
                        )
                        if (replaced) {
                            retainCandidate.value = candidateStored && !removeOriginalImage
                            state.closeTokenDialog()
                        }
                    }
                },
            )
        },
        dismissButton = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (!creating && lootable) {
                    GameButton(
                        words.collectLoot,
                        accent = Palette.Heal,
                        enabled = collectorId != null && name.isNotBlank() &&
                            lootQuantity.toIntOrNull() in 1..BoardLimits.MAX_TOKEN_LOOT_QUANTITY,
                        onClick = {
                            val token = requireNotNull(existing)
                            val visibleDraft = SceneToken(
                                token.id(), name.trim(), category, token.position(), sizeSquares,
                                token.rotationDegrees(), colorArgb, token.imageAssetId(), showLabel,
                                visibleToPlayers, lootable, lootCategory, lootQuantity.toIntOrNull() ?: 1,
                                lootDescription.trim(), notes.trim(),
                            )
                            val result = transferSceneLoot(
                                visibleDraft,
                                collectorId.orEmpty(),
                                board,
                                roster,
                            )
                            if (result == LootTransferResult.SUCCESS) {
                                state.selectedId = null
                                state.closeTokenDialog()
                            } else {
                                lootResult = result
                            }
                        },
                    )
                }
                GameButton(strings.common.cancel, onClick = ::dismiss)
            }
        },
    )
}

internal fun LootTransferResult.message(words: BoardStrings): String? = when (this) {
    LootTransferResult.SUCCESS -> null
    LootTransferResult.TOKEN_NOT_FOUND -> words.lootTransferTokenMissing
    LootTransferResult.TOKEN_NOT_LOOTABLE -> words.lootTransferNotLootable
    LootTransferResult.COLLECTOR_NOT_AVAILABLE -> words.lootTransferCollectorMissing
    LootTransferResult.INVENTORY_WRITE_FAILED -> words.lootTransferSaveFailed
    LootTransferResult.BOARD_CHANGED -> words.lootTransferBoardChanged
}

@Composable
private fun SceneTokenImagePicker(
    portraits: PortraitRepository,
    candidateAssetId: String,
    shownImage: androidx.compose.ui.graphics.ImageBitmap?,
    candidateStored: Boolean,
    name: String,
    color: Color,
    compact: Boolean,
    onImageRequested: () -> Unit,
    onImageImportFinished: (Boolean) -> Unit,
    onRemove: () -> Unit,
    framing: PortraitFraming,
    framingEditorOpen: Boolean,
    onFramingEditorOpenChange: (Boolean) -> Unit,
    onFramingChange: (PortraitFraming) -> Unit,
) {
    val mapWords = strings.maps
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.34f))
                .border(2.dp, color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (shownImage != null) {
                FramedPortraitImage(
                    image = shownImage,
                    framing = framing,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    initials(name),
                    color = Palette.Text,
                    fontSize = 27.sp,
                    style = OnfallTheme.typography.tokenInitials,
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            GameButton(
                label = if (shownImage == null) mapWords.uploadImage else mapWords.changeImage,
                modifier = Modifier.sizeIn(minHeight = if (compact) 48.dp else 40.dp),
                dense = true,
                onClick = {
                    onImageRequested()
                    portraits.assignPortraitAsync(candidateAssetId, onImageImportFinished)
                },
            )
            if (shownImage != null) {
                GameButton(
                    strings.maps.frameImage,
                    modifier = Modifier.sizeIn(minHeight = if (compact) 48.dp else 40.dp),
                    selected = framingEditorOpen,
                    dense = true,
                    onClick = { onFramingEditorOpenChange(!framingEditorOpen) },
                )
                GameButton(
                    strings.common.remove,
                    modifier = Modifier.sizeIn(minHeight = if (compact) 48.dp else 40.dp),
                    dense = true,
                    onClick = onRemove,
                )
            }
        }
        if (shownImage != null && framingEditorOpen) {
            PortraitFramingEditor(
                image = shownImage,
                framing = framing,
                onFramingChange = onFramingChange,
                previewSize = if (compact) 154.dp else 176.dp,
            )
        }
        if (candidateStored) {
            Text(mapWords.portraitAssigned, color = Palette.Gold, style = MaterialTheme.typography.labelMedium)
        }
        portraits.message?.let {
            Text(it, color = Palette.Gold, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TokenColourChoices(selected: Int, compact: Boolean, onSelect: (Int) -> Unit) {
    val description = strings.board.colour
    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        listOf(0xFFFFC857, 0xFFE35D6A, 0xFF66D9EF, 0xFF8BE28B, 0xFFB79CED, 0xFFFFFFFF).forEach { raw ->
            val argb = raw.toInt()
            Box(
                Modifier
                    .size(if (compact) 48.dp else 40.dp)
                    .background(Color(argb), RoundedCornerShape(24.dp))
                    .border(if (selected == argb) 3.dp else 1.dp, Palette.Text, RoundedCornerShape(24.dp))
                    .semantics {
                        role = Role.Button
                        contentDescription = description
                        this.selected = selected == argb
                    }
                    .clickable(role = Role.Button) { onSelect(argb) },
                contentAlignment = Alignment.Center,
            ) {
                if (selected == argb) {
                    Text("✓", color = Palette.Abyss, style = OnfallTheme.typography.tokenInitials)
                }
            }
        }
    }
}

internal fun TokenCategory.label(words: BoardStrings): String = when (this) {
    TokenCategory.CHARACTER -> words.categoryCharacter
    TokenCategory.ALLY -> words.categoryAlly
    TokenCategory.NPC -> words.categoryNpc
    TokenCategory.MONSTER -> words.categoryMonster
    TokenCategory.OBJECT -> words.categoryObject
    TokenCategory.TRAP -> words.categoryTrap
    TokenCategory.HAZARD -> words.categoryHazard
    TokenCategory.TERRAIN -> words.categoryTerrain
    TokenCategory.LOOT -> words.categoryLoot
    TokenCategory.VEHICLE -> words.categoryVehicle
    TokenCategory.MARKER -> words.categoryMarker
    TokenCategory.OTHER -> words.categoryOther
}
