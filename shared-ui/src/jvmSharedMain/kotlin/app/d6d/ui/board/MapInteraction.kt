package app.d6d.ui.board

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.d6d.board.GridPoint
import app.d6d.board.StampKind
import app.d6d.board.TemplateShape
import app.d6d.board.TokenCategory
import app.d6d.board.TokenLootCategory

/** Modalità del puntatore del Lucido; Strati resta un pannello e non compare qui. */
enum class BoardTool {
    TABLE,
    EDIT,
    HAND,
    MEASURE,
    INK,
    TEMPLATE,
    LABEL,
    PING,
    FOG,
    ERASER,
    TOKEN,
}

/** Dati convalidabili del popup, ancora privi della posizione scelta sulla mappa. */
data class SceneTokenDraft(
    val name: String,
    val category: TokenCategory,
    val sizeSquares: Double,
    val colorArgb: Int,
    val imageAssetId: String,
    val showLabel: Boolean,
    val visibleToPlayers: Boolean,
    val lootable: Boolean = false,
    val lootCategory: TokenLootCategory = TokenLootCategory.MISC,
    val lootQuantity: Int = 1,
    val lootDescription: String = "",
    val notes: String,
)

/** Stato effettivo dopo avere applicato la precedenza fra regole, mappa e Lucido. */
sealed interface MapInteraction {
    data object Table : MapInteraction
    data object RuleTargeting : MapInteraction
    data object BackgroundEditing : MapInteraction
    data object CpuPlayback : MapInteraction
    data class Board(val tool: BoardTool) : MapInteraction
    data class TemporaryPan(val previous: BoardTool) : MapInteraction
}

/** Router puro: la UI lo consulta invece di distribuire eccezioni fra i veli. */
fun resolveMapInteraction(
    selectedTool: BoardTool,
    ruleTargeting: Boolean,
    backgroundEditing: Boolean,
    cpuPlayback: Boolean,
    temporaryPan: Boolean = false,
): MapInteraction = when {
    cpuPlayback -> MapInteraction.CpuPlayback
    ruleTargeting -> MapInteraction.RuleTargeting
    backgroundEditing -> MapInteraction.BackgroundEditing
    temporaryPan -> MapInteraction.TemporaryPan(selectedTool)
    selectedTool == BoardTool.TABLE -> MapInteraction.Table
    else -> MapInteraction.Board(selectedTool)
}

/** Stato volatile della superficie mappa attiva; cambia sessione e torna a Tavolo. */
class BoardToolState(
    private val onDiscardTokenImage: (String) -> Unit = {},
) {
    companion object {
        const val NEW_TOKEN_DIALOG = "__new_scene_token__"
    }

    var active by mutableStateOf(BoardTool.TABLE)
    var toolboxOpen by mutableStateOf(false)
    var layersOpen by mutableStateOf(false)
    var selectedId by mutableStateOf<String?>(null)
    var labelEditorId by mutableStateOf<String?>(null)
    var measurePoints by mutableStateOf<List<GridPoint>>(emptyList())
    var templateShape by mutableStateOf(TemplateShape.SPHERE)
    var stampMode by mutableStateOf(false)
    var stampKind by mutableStateOf(StampKind.FLAME)
    var fogCovering by mutableStateOf(false)
    var playerPreview by mutableStateOf(false)
    var tokenDialogId by mutableStateOf<String?>(null)
        private set
    var pendingToken by mutableStateOf<SceneTokenDraft?>(null)
        private set

    fun select(tool: BoardTool) {
        if (tool != BoardTool.TOKEN) discardPendingToken()
        active = tool
        selectedId = null
        labelEditorId = null
        tokenDialogId = if (tool == BoardTool.TOKEN && pendingToken == null) NEW_TOKEN_DIALOG else null
        if (tool != BoardTool.MEASURE) measurePoints = emptyList()
        if (tool != BoardTool.TEMPLATE) stampMode = false
    }

    fun requestTokenCreation() {
        select(BoardTool.TOKEN)
    }

    fun requestTokenEdit(id: String) {
        tokenDialogId = id
    }

    fun closeTokenDialog() {
        tokenDialogId = null
    }

    fun prepareToken(value: SceneTokenDraft) {
        pendingToken = value
        tokenDialogId = null
        active = BoardTool.TOKEN
        selectedId = null
    }

    /** Consuma la posa senza considerare orfana l'immagine ormai usata dal documento. */
    fun consumePendingToken(): SceneTokenDraft? = pendingToken.also { pendingToken = null }

    fun discardPendingToken() {
        val image = pendingToken?.imageAssetId.orEmpty()
        pendingToken = null
        if (image.isNotBlank()) onDiscardTokenImage(image)
    }

    fun table() {
        select(BoardTool.TABLE)
        toolboxOpen = false
    }
}
