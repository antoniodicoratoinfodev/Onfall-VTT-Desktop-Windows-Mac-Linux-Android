package app.d6d.ui.battle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.d6d.domain.combat.CombatResourceState
import app.d6d.domain.combat.TurnResource
import app.d6d.sheet.isPactSpellSlot
import app.d6d.sheet.isPactSlotMirrorResourceId
import app.d6d.sheet.spellSlotLevelOrNull
import app.d6d.ui.components.CombatantPortrait
import app.d6d.ui.components.ConditionChip
import app.d6d.ui.components.EditableValue
import app.d6d.ui.components.Faction
import app.d6d.ui.components.HealthBar
import app.d6d.ui.components.ResourcePips
import app.d6d.ui.components.color
import app.d6d.ui.state.BattleViewModel
import app.d6d.ui.i18n.Strings
import app.d6d.ui.i18n.strings
import app.d6d.ui.theme.Palette

/**
 * Carta di un combattente nelle barre laterali: squadra a sinistra, nemici a destra.
 *
 * Deve restare leggibile a colpo d'occhio durante il turno, quindi mostra solo
 * cio' che serve a decidere: PF, CA, condizioni e risorse del turno.
 *
 * La carta e' responsiva: misura la larghezza reale disponibile e, quando la barra
 * viene stretta col mouse, ripiega ritratto, nome e statistiche in un elenco
 * verticale invece di troncarli su una riga sola.
 */
@Composable
fun CombatantRailCard(
    viewModel: BattleViewModel,
    combatantId: String,
    faction: Faction,
    onOpenSheet: (String) -> Unit,
    modifier: Modifier = Modifier,
    dropTarget: TokenPlacementDrag? = null,
) {
    val combatant = viewModel.combatant(combatantId) ?: return
    val snapshot = combatant.snapshot()
    // In un turno simultaneo sono attivi tutti i membri del gruppo, non solo il primo.
    val active = viewModel.isActive(combatantId)
    val targeted = viewModel.selectedTargetId == combatantId
    val inspected = viewModel.inspectedCombatantId == combatantId
    val defeated = combatant.defeated() || combatant.dead()
    val budget = viewModel.budget(combatantId)
    var resourceEditor by remember(combatantId) {
        mutableStateOf<ResourceEditorTarget?>(null)
    }

    LaunchedEffect(viewModel.editMode) {
        if (!viewModel.editMode) resourceEditor = null
    }

    val shape = RoundedCornerShape(10.dp)
    val outline = when {
        targeted -> Modifier.border(2.dp, faction.color, shape)
        active -> Modifier.border(2.dp, Palette.TurnBright.copy(alpha = 0.95f), shape)
        inspected -> Modifier.border(1.5.dp, Palette.Text.copy(alpha = 0.82f), shape)
        else -> Modifier.border(1.dp, Palette.Line, shape)
    }
    val words = strings.battle
    val activationWords = strings.glossary
    val cardState = buildString {
        append(words.hitPointsSentence(combatant.currentHitPoints(), snapshot.maxHitPoints()))
        if (targeted) append(words.selectedTargetSentence)
        if (active) append(words.activeTurnSentence)
        if (inspected) append(words.inspectedSentence)
        if (combatant.dead()) append(words.deadSentence)
        else if (defeated) append(words.defeatedSentence)
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Sotto questa soglia la riga ritratto + nome + statistiche non entra piu':
        // si passa a una disposizione verticale in modo che nulla venga tagliato.
        val narrow = maxWidth < 208.dp

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .clip(shape)
                .background(
                    when {
                        // Bersaglio: velatura di fazione che sfuma. Il turno attivo
                        // usa invece l'oro caldo del puntatore, ben distinto dalla
                        // superficie chiara riservata alla scheda in esame.
                        targeted -> Brush.verticalGradient(
                            listOf(faction.color.copy(alpha = 0.16f), faction.color.copy(alpha = 0.06f)),
                        )
                        active -> Brush.verticalGradient(
                            listOf(Palette.Turn.copy(alpha = 0.24f), Palette.Turn.copy(alpha = 0.07f)),
                        )
                        inspected -> SolidColor(Palette.SurfaceHigh)
                        else -> SolidColor(Palette.Surface)
                    },
                    shape,
                )
                // Strip di fazione sul bordo sinistro, come le carte di iniziativa
                // del riferimento: argento per la squadra, salmone per i nemici.
                .drawBehind {
                    drawRect(
                        color = faction.color.copy(alpha = 0.9f),
                        size = Size(3.dp.toPx(), size.height),
                    )
                }
                .then(outline)
                .semantics {
                    contentDescription = words.combatantNamed(snapshot.name())
                    stateDescription = cardState
                    selected = inspected
                }
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (viewModel.singleTargeting != null) {
                        words.chooseAsTarget(snapshot.name())
                    } else {
                        words.showAbilitiesOf(snapshot.name())
                    },
                ) { viewModel.onCombatantClicked(combatantId) }
                .padding(9.dp)
                .alpha(if (defeated) 0.5f else 1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = buildList {
                        if (active) add(words.inTurnBadge)
                        if (targeted) add(words.targetBadge)
                        if (inspected && !active) add(words.inspectedBadge)
                    }.joinToString(" · "),
                    color = when {
                        targeted -> faction.color
                        active -> Palette.TurnBright
                        else -> Palette.TextMuted
                    },
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = words.openSheetBadge,
                    color = Palette.TextMuted,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .clickable(
                            role = Role.Button,
                            onClickLabel = words.openFullSheetOf(snapshot.name()),
                        ) { onOpenSheet(snapshot.definitionId()) }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }

            CombatantHeader(
                viewModel = viewModel,
                combatantId = combatantId,
                snapshot = snapshot,
                faction = faction,
                active = active,
                currentHitPoints = combatant.currentHitPoints(),
                defeated = defeated,
                narrow = narrow,
                dropTarget = dropTarget,
            )

            HealthBar(
                current = combatant.currentHitPoints(),
                max = snapshot.maxHitPoints(),
                temporary = combatant.temporaryHitPoints(),
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = buildString {
                        append("${combatant.currentHitPoints()}/${snapshot.maxHitPoints()}")
                        if (combatant.temporaryHitPoints() > 0) append(" +${combatant.temporaryHitPoints()}")
                    },
                    color = Palette.Text,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                )
                if ((active || viewModel.editMode) && budget != null) {
                    ResourcePips(
                        actionAvailable = budget.actionAvailable(),
                        bonusAvailable = budget.bonusActionAvailable(),
                        reactionAvailable = budget.reactionAvailable(),
                        editable = viewModel.editMode,
                        onActionClick = {
                            resourceEditor = ResourceEditorTarget.Turn(
                                TurnResource.ACTION,
                                activationWords.action,
                                budget.actionAvailable(),
                                Palette.Gold,
                            )
                        },
                        onBonusClick = {
                            resourceEditor = ResourceEditorTarget.Turn(
                                TurnResource.BONUS_ACTION,
                                words.bonusActionLabel,
                                budget.bonusActionAvailable(),
                                Palette.Party,
                            )
                        },
                        onReactionClick = {
                            resourceEditor = ResourceEditorTarget.Turn(
                                TurnResource.REACTION,
                                activationWords.reaction,
                                budget.reactionAvailable(),
                                Palette.Heal,
                            )
                        },
                    )
                }
            }

            val combatResources = combatant.resources()
            if (viewModel.editMode && (budget != null || combatResources.isNotEmpty())) {
                Text(
                    text = "✎  ${words.editResourcesHint}",
                    color = Palette.Heal,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            // In gioco normale il dettaglio resta nella colonna della squadra;
            // in Modifica compare anche sui nemici, perche' una correzione del
            // tavolo deve poter raggiungere ogni risorsa presente nell'incontro.
            if (faction == Faction.PARTY || viewModel.editMode) {
                val spellSlots = spellSlotIndicators(combatResources)
                if (spellSlots.isNotEmpty()) {
                    SpellSlotIndicators(
                        slots = spellSlots,
                        editing = viewModel.editMode,
                        onEdit = { slot ->
                            resourceEditor = ResourceEditorTarget.Pool(
                                id = slot.resourceId,
                                name = slot.name,
                                remaining = slot.remaining,
                                maximum = slot.total,
                                accent = if (slot.kind == SpellSlotKind.PACT) Palette.Gold else Palette.Party,
                            )
                        },
                    )
                }
                val classResources = classResourceIndicators(combatResources)
                if (classResources.isNotEmpty()) {
                    ClassResourceIndicators(
                        resources = classResources,
                        editing = viewModel.editMode,
                        onEdit = { resource ->
                            resourceEditor = ResourceEditorTarget.Pool(
                                id = resource.id,
                                name = resource.name,
                                remaining = resource.remaining,
                                maximum = resource.total,
                                accent = Palette.Gold,
                            )
                        },
                    )
                }
            }

            if (combatant.conditions().isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    combatant.conditions().forEach { condition ->
                        ConditionChip(
                            type = condition.type(),
                            rounds = condition.duration().remainingOccurrences(),
                        )
                    }
                }
            }
        }

        resourceEditor?.let { target ->
            ResourceQuantityEditor(
                target = target,
                combatantName = snapshot.name(),
                onConfirm = { remaining, maximum ->
                    when (target) {
                        is ResourceEditorTarget.Pool -> viewModel.setCombatResourceQuantities(
                            combatantId = combatantId,
                            resourceId = target.id,
                            remaining = remaining,
                            maximum = maximum,
                        )
                        is ResourceEditorTarget.Turn -> viewModel.setTurnResourceAvailable(
                            combatantId,
                            target.resource,
                            remaining > 0,
                        )
                    }
                    resourceEditor = null
                },
                onDismiss = { resourceEditor = null },
            )
        }
    }
}

internal enum class SpellSlotKind {
    STANDARD,
    PACT,
}

/** Intestazione in maiuscolo sopra i pallini degli slot. */
internal fun SpellSlotKind.visibleLabel(strings: Strings): String = when (this) {
    SpellSlotKind.STANDARD -> strings.battle.spellSlotsCapitalized
    SpellSlotKind.PACT -> strings.battle.pactSlotsCapitalized
}

/** Nome pronunciabile, per chi ascolta invece di guardare. */
internal fun SpellSlotKind.accessibleLabel(strings: Strings): String = when (this) {
    SpellSlotKind.STANDARD -> strings.battle.spellSlots
    SpellSlotKind.PACT -> strings.battle.pactSlots
}

internal data class SpellSlotIndicator(
    val resourceId: String,
    val name: String,
    val kind: SpellSlotKind,
    val level: Int,
    val total: Int,
    val remaining: Int,
)

internal data class ClassResourceIndicator(
    val id: String,
    val name: String,
    val total: Int,
    val remaining: Int,
)

/** Mantiene separati gli slot Incantesimo e del Patto anche quando hanno lo stesso livello. */
internal fun spellSlotIndicators(resources: List<CombatResourceState>): List<SpellSlotIndicator> =
    resources
        .mapNotNull { resource ->
            resource.spellSlotLevelOrNull()?.let { level ->
                val kind = if (resource.isPactSpellSlot()) SpellSlotKind.PACT else SpellSlotKind.STANDARD
                (kind to level) to resource
            }
        }
        .groupBy({ it.first }, { it.second })
        .map { (kindAndLevel, sameKindAndLevel) ->
            SpellSlotIndicator(
                resourceId = sameKindAndLevel.first().id(),
                name = sameKindAndLevel.first().name(),
                kind = kindAndLevel.first,
                level = kindAndLevel.second,
                total = sameKindAndLevel.sumOf { it.maximum() },
                remaining = sameKindAndLevel.sumOf { it.remaining() },
            )
        }
        .filter { it.total > 0 }
        .sortedWith(compareBy<SpellSlotIndicator> { it.kind.ordinal }.thenBy { it.level })

/**
 * Le riserve limitate della progressione viaggiano nello stesso snapshot degli
 * slot. Le separiamo solo nella presentazione, così Ira, Ispirazione bardica,
 * Recuperare energie e le altre risorse non vengono confuse con livelli di
 * incantesimo.
 */
internal fun classResourceIndicators(resources: List<CombatResourceState>): List<ClassResourceIndicator> =
    resources
        .asSequence()
        .filter { it.spellSlotLevelOrNull() == null }
        // La progressione del Warlock conserva anche la riga SRD della Magia del
        // patto, ma lo snapshot possiede già gli slot canonici con il loro livello.
        // Mostrarla di nuovo produrrebbe due contatori per la stessa riserva.
        .filterNot { resource ->
            resource.id().isPactSlotMirrorResourceId() &&
                resources.any { it.isPactSpellSlot() }
        }
        .filter { it.maximum() > 0 }
        .map {
            ClassResourceIndicator(
                id = it.id(),
                name = it.name(),
                total = it.maximum(),
                remaining = it.remaining(),
            )
        }
        .toList()

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SpellSlotIndicators(
    slots: List<SpellSlotIndicator>,
    editing: Boolean,
    onEdit: (SpellSlotIndicator) -> Unit,
) {
    val strings = strings
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.semantics {
            contentDescription = slots.joinToString("; ") {
                strings.battle.slotsRemaining(
                    kind = it.kind.accessibleLabel(strings),
                    level = it.level,
                    remaining = it.remaining,
                    total = it.total,
                )
            }
        },
    ) {
        slots.groupBy { it.kind }.forEach { (kind, kindSlots) ->
            val slotColor = when (kind) {
                SpellSlotKind.STANDARD -> Palette.Party
                SpellSlotKind.PACT -> Palette.Gold
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = kind.visibleLabel(strings),
                    color = slotColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    kindSlots.forEach { slot ->
                        SpellSlotSquare(slot, slotColor, editing) { onEdit(slot) }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SpellSlotSquare(
    slot: SpellSlotIndicator,
    slotColor: Color,
    editing: Boolean,
    onEdit: () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    Column(
        modifier = Modifier
            .size(44.dp)
            .background(slotColor.copy(alpha = if (editing) 0.16f else 0.10f), shape)
            .border(
                if (editing) 1.5.dp else 1.dp,
                if (editing) Palette.Heal else slotColor.copy(alpha = 0.9f),
                shape,
            )
            .then(
                if (editing) {
                    Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = slot.name,
                        onClick = onEdit,
                    )
                } else {
                    Modifier
                },
            )
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            "${slot.level}°",
            color = slotColor,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall,
        )
        if (slot.total <= MAX_SPELL_SLOT_PIPS) {
            FlowRow(
                modifier = Modifier.width(28.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                maxItemsInEachRow = 4,
            ) {
                repeat(slot.total) { index ->
                    val available = index < slot.remaining
                    Box(
                        Modifier
                            .size(5.dp)
                            .then(
                                if (available) {
                                    Modifier.background(slotColor, RoundedCornerShape(1.dp))
                                } else {
                                    Modifier.border(1.dp, Palette.TextFaint, RoundedCornerShape(1.dp))
                                },
                            ),
                    )
                }
            }
        } else {
            Text(
                text = "${slot.remaining}/${slot.total}",
                color = slotColor,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun ClassResourceIndicators(
    resources: List<ClassResourceIndicator>,
    editing: Boolean,
    onEdit: (ClassResourceIndicator) -> Unit,
) {
    val words = strings.battle
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.semantics {
            contentDescription = resources.joinToString("; ") {
                words.classResourceRemaining(it.name, it.remaining, it.total)
            }
        },
    ) {
        Text(
            text = words.classResourcesCapitalized,
            color = Palette.Gold,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall,
        )
        resources.forEach { resource ->
            ClassResourceRow(resource, editing) { onEdit(resource) }
        }
    }
}

@Composable
private fun ClassResourceRow(
    resource: ClassResourceIndicator,
    editing: Boolean,
    onEdit: () -> Unit,
) {
    val resourceColor = Palette.Gold
    val shape = RoundedCornerShape(6.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(resourceColor.copy(alpha = if (editing) 0.13f else 0.08f), shape)
            .border(
                if (editing) 1.5.dp else 1.dp,
                if (editing) Palette.Heal else resourceColor.copy(alpha = 0.65f),
                shape,
            )
            .then(
                if (editing) {
                    Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = resource.name,
                        onClick = onEdit,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = resource.name,
                color = Palette.Text,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${resource.remaining}/${resource.total}",
                color = resourceColor,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (resource.total <= MAX_CLASS_RESOURCE_PIPS) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(resource.total) { index ->
                    val available = index < resource.remaining
                    Box(
                        Modifier
                            .size(6.dp)
                            .then(
                                if (available) {
                                    Modifier.background(resourceColor, RoundedCornerShape(1.dp))
                                } else {
                                    Modifier.border(
                                        1.dp,
                                        Palette.TextFaint,
                                        RoundedCornerShape(1.dp),
                                    )
                                },
                            ),
                    )
                }
            }
        }
    }
}

private const val MAX_CLASS_RESOURCE_PIPS = 8
private const val MAX_SPELL_SLOT_PIPS = 8

/**
 * Testata della carta: ritratto, nome e statistiche.
 *
 * `narrow` sceglie fra la disposizione affiancata (barra larga) e quella
 * impilata a elenco verticale (barra stretta), senza mai troncare i valori.
 */
@Composable
private fun CombatantHeader(
    viewModel: BattleViewModel,
    combatantId: String,
    snapshot: app.d6d.domain.combat.CombatantSnapshot,
    faction: Faction,
    active: Boolean,
    currentHitPoints: Int,
    defeated: Boolean,
    narrow: Boolean,
    dropTarget: TokenPlacementDrag? = null,
) {
    val name = @Composable {
        EditableValue(
            value = snapshot.name(),
            editMode = viewModel.editMode,
            onCommit = { viewModel.editCombatant(combatantId, name = it) },
            fieldWidth = 128.dp,
        ) {
            Text(
                text = snapshot.name(),
                color = when {
                    defeated -> Palette.TextMuted
                    active -> Palette.TurnBright
                    else -> Palette.Text
                },
                fontWeight = FontWeight.Bold,
                // Quando la barra e' stretta il nome puo' andare a capo invece di
                // essere troncato con i puntini.
                maxLines = if (narrow) 2 else 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
    val stats = @Composable {
        CombatantStats(viewModel, combatantId, snapshot, currentHitPoints, narrow)
    }

    // In modifica il ritratto diventa la maniglia per trascinare il personaggio
    // sulla mappa: al rilascio il motore lo colloca nella casella scelta (e
    // rifiuta quelle occupate, evitando i doppioni).
    val portrait = @Composable {
        var portraitCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
        val dragModifier = if (dropTarget != null && viewModel.editMode) {
            Modifier
                .onGloballyPositioned { portraitCoords = it }
                .pointerInput(combatantId, viewModel.editMode) {
                    detectDragGestures(
                        onDragStart = { local ->
                            portraitCoords?.let {
                                dropTarget.start(combatantId, faction == Faction.PARTY, it.localToWindow(local))
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            portraitCoords?.let { dropTarget.update(it.localToWindow(change.position)) }
                        },
                        onDragEnd = {
                            dropTarget.drop()?.let { viewModel.reposition(combatantId, it.x, it.y) }
                        },
                        onDragCancel = { dropTarget.cancel() },
                    )
                }
        } else {
            Modifier
        }
        Box(dragModifier) {
            CombatantPortrait(
                name = snapshot.name(),
                currentHitPoints = currentHitPoints,
                maxHitPoints = snapshot.maxHitPoints(),
                faction = faction,
                active = active,
                diameter = if (narrow) 34.dp else 42.dp,
            )
        }
    }

    if (narrow) {
        // Elenco verticale: ritratto e nome in cima, statistiche impilate sotto.
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                portrait()
                Column(Modifier.weight(1f)) { name() }
            }
            stats()
        }
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            portrait()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                name()
                stats()
            }
        }
    }
}

/**
 * CA, PF massimi e iniziativa. In riga quando c'e' spazio, altrimenti in colonna
 * (un valore per riga) cosi' restano tutti leggibili nella barra stretta.
 */
@Composable
private fun CombatantStats(
    viewModel: BattleViewModel,
    combatantId: String,
    snapshot: app.d6d.domain.combat.CombatantSnapshot,
    currentHitPoints: Int,
    narrow: Boolean,
) {
    val words = strings.battle
    val armorClass = @Composable {
        EditableValue(
            value = snapshot.armorClass().toString(),
            editMode = viewModel.editMode,
            numeric = true,
            fieldWidth = 46.dp,
            onCommit = { text ->
                text.trim().toIntOrNull()?.let {
                    viewModel.editCombatant(combatantId, armorClass = it)
                }
            },
        ) {
            Text(
                text = words.armorClassShort(snapshot.armorClass()),
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    val hitPoints = @Composable {
        EditableValue(
            value = snapshot.maxHitPoints().toString(),
            editMode = viewModel.editMode,
            numeric = true,
            fieldWidth = 52.dp,
            onCommit = { text ->
                text.trim().toIntOrNull()?.let {
                    viewModel.editCombatant(combatantId, maxHitPoints = it)
                }
            },
        ) {
            Text(
                text = "${words.maxHitPointsAbbrev} ${snapshot.maxHitPoints()}",
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    val currentHitPointsField = @Composable {
        EditableValue(
            value = currentHitPoints.toString(),
            editMode = viewModel.editMode,
            numeric = true,
            fieldWidth = 68.dp,
            onCommit = { text ->
                text.trim().toIntOrNull()?.let {
                    viewModel.setCurrentHitPoints(combatantId, it)
                }
            },
        ) {
            Text(
                text = "${words.currentHitPointsAbbrev} $currentHitPoints",
                color = if (currentHitPoints == 0) Palette.Critical else Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    val initiative: (@Composable () -> Unit)? = viewModel.initiativeScore(combatantId)?.let { score ->
        @Composable {
            EditableValue(
                value = score.toString(),
                editMode = viewModel.editMode,
                numeric = true,
                fieldWidth = 44.dp,
                onCommit = { text ->
                    text.trim().toIntOrNull()?.let {
                        viewModel.overrideInitiative(combatantId, it)
                    }
                },
            ) {
                Text(
                    text = "${words.initiativeAbbrev} $score",
                    color = Palette.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
    val items = listOfNotNull(armorClass, currentHitPointsField, hitPoints, initiative)

    if (narrow) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items.forEach { it() }
        }
    } else {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items.forEach { it() }
        }
    }
}
