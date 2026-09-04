package app.d6d.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.d6d.rules.character.RecoveryPeriod
import app.d6d.content.srd521it.SrdBeastForm
import app.d6d.sheet.CharacterSheet
import app.d6d.sheet.isPactSlotMirrorResourceId
import app.d6d.ui.battle.GameButton
import app.d6d.ui.components.Chip
import app.d6d.ui.components.DialogTitle
import app.d6d.ui.components.Eyebrow
import app.d6d.i18n.label
import app.d6d.i18n.pick
import app.d6d.ui.i18n.currentLanguage
import app.d6d.ui.i18n.strings
import app.d6d.ui.theme.Palette
import app.d6d.ui.theme.OnfallTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProgressionOverview(
    viewModel: SheetViewModel,
    sheet: CharacterSheet,
    compact: Boolean,
    onOpenProgression: () -> Unit,
) {
    val words = strings.sheet
    val language = currentLanguage
    if (!sheet.progression.configured) {
        SheetBox(words.srdCreationTitle, Modifier.fillMaxWidth()) {
            Text(
                words.srdCreationBody,
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            GameButton(
                words.startGuidedCreation,
                accent = Palette.Gold,
                onClick = onOpenProgression,
            )
        }
        return
    }

    var showWildShapeReplacement by remember(sheet.id) { mutableStateOf(false) }
    var replacementRest by remember(sheet.id) { mutableStateOf<RecoveryPeriod?>(null) }
    val shortRestChoices = viewModel.restReplaceableChoices(RecoveryPeriod.SHORT_REST)
    val longRestChoices = viewModel.restReplaceableChoices(RecoveryPeriod.LONG_REST)

    SheetBox(words.srdProgressionTitle, Modifier.fillMaxWidth()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            sheet.progression.classLevels.forEach {
                Chip(words.classAndLevel(viewModel.displayedClassLabel(it.classId, sheet), it.level), Palette.Party)
            }
            Chip(words.proficiencyBonusIs(signed(sheet.proficiencyBonus)), Palette.Gold)
            Chip(words.experiencePoints(sheet.experiencePoints), Palette.Temporary)
        }

        if (!viewModel.characterRulesetAvailable) {
            Text(
                language.pick(
                    "La revisione esatta del regolamento non è installata. La scheda resta leggibile, " +
                        "ma progressione e modificatori sono bloccati per evitare di applicare lo SRD per errore.",
                    "The exact ruleset revision is not installed. The sheet remains readable, but progression " +
                        "and modifiers are locked so SRD rules cannot be applied by mistake.",
                ),
                color = Palette.Critical,
                style = MaterialTheme.typography.bodySmall,
            )
        } else if (sheet.canLevelUp) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Palette.Gold.copy(alpha = 0.12f), RoundedCornerShape(7.dp))
                    .border(1.dp, Palette.Gold.copy(alpha = 0.65f), RoundedCornerShape(7.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    words.levelUpAvailable(sheet.effectiveLevel + 1),
                    color = Palette.GoldBright,
                    style = OnfallTheme.typography.bodyEmphasis,
                )
                GameButton(
                    words.levelUpTo(sheet.effectiveLevel + 1),
                    accent = Palette.Gold,
                    onClick = onOpenProgression,
                )
            }
        } else {
            sheet.nextLevelExperienceThreshold?.let { threshold ->
                Text(
                    words.nextLevelAt(threshold, sheet.experienceToNextLevel),
                    color = Palette.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        val hasPactSlots = sheet.spellcasting?.pactSlots?.total?.let { it > 0 } == true
        val hasRecoverableSpellSlots = sheet.spellcasting?.let { casting ->
            casting.slots.any { it.total > 0 } || casting.pactSlots?.total?.let { it > 0 } == true
        } == true
        val canReplaceWildShape =
            viewModel.knownWildShapeForms().isNotEmpty() &&
                viewModel.wildShapeReplacementOptions().isNotEmpty()
        val visibleResourcePools = sheet.progression.resourcePools
            .filter { it.maximum > 0 }
            .filterNot { hasPactSlots && it.resourceId.isPactSlotMirrorResourceId() }
        if (visibleResourcePools.isNotEmpty()) {
            Eyebrow(words.classResourcesCaps)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                visibleResourcePools.forEach { pool ->
                    Column(
                        Modifier
                            .width(if (compact) 150.dp else 180.dp)
                            .background(Palette.Night, RoundedCornerShape(6.dp))
                            .padding(7.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            buildString {
                                append(pool.name)
                                if (pool.dieSides > 0) append(words.dieSuffix(pool.dieSides))
                            },
                            color = Palette.Text,
                            style = OnfallTheme.typography.supportingEmphasis,
                        )
                        Text(
                            words.resourcePool(pool.remaining, pool.maximum, pool.recovery.label(language)),
                            color = Palette.TextMuted,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        if (pool.maximum <= MAX_EDITABLE_RESOURCE_PIPS) {
                            PipRow(pool.maximum, pool.spent, color = Palette.Gold) {
                                viewModel.setCharacterResourceSpent(pool.resourceId, it)
                            }
                        } else {
                            LargeResourceEditor(
                                remaining = pool.remaining,
                                maximum = pool.maximum,
                                onSetRemaining = { remaining ->
                                    viewModel.setCharacterResourceSpent(
                                        pool.resourceId,
                                        pool.maximum - remaining,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
        if (
            visibleResourcePools.isNotEmpty() || hasRecoverableSpellSlots ||
            shortRestChoices.isNotEmpty() || longRestChoices.isNotEmpty() || canReplaceWildShape
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                GameButton(
                    words.shortRest,
                    accent = Palette.Temporary,
                    dense = true,
                    onClick = { viewModel.recoverCharacterResources(RecoveryPeriod.SHORT_REST) },
                )
                GameButton(
                    words.longRest,
                    accent = Palette.Heal,
                    dense = true,
                    onClick = { viewModel.recoverCharacterResources(RecoveryPeriod.LONG_REST) },
                )
                if (shortRestChoices.isNotEmpty()) {
                    GameButton(
                        language.pick("Riposo breve + modifica scelte", "Short rest + change choices"),
                        accent = Palette.Gold,
                        dense = true,
                        onClick = { replacementRest = RecoveryPeriod.SHORT_REST },
                    )
                }
                if (longRestChoices.isNotEmpty()) {
                    GameButton(
                        language.pick("Riposo lungo + modifica scelte", "Long rest + change choices"),
                        accent = Palette.Gold,
                        dense = true,
                        onClick = { replacementRest = RecoveryPeriod.LONG_REST },
                    )
                }
                if (canReplaceWildShape) {
                    GameButton(
                        words.longRestAndSwapForm,
                        accent = Palette.Gold,
                        dense = true,
                        onClick = { showWildShapeReplacement = true },
                    )
                }
            }
        }
    }

    if (showWildShapeReplacement) {
        WildShapeReplacementDialog(
            knownForms = viewModel.knownWildShapeForms(),
            availableForms = viewModel.wildShapeReplacementOptions(),
            onConfirm = { oldId, newId ->
                if (viewModel.longRestAndReplaceWildShapeForm(oldId, newId)) {
                    showWildShapeReplacement = false
                }
            },
            onDismiss = { showWildShapeReplacement = false },
        )
    }
    replacementRest?.let { period ->
        RestChoiceReplacementDialog(
            period = period,
            choices = if (period == RecoveryPeriod.LONG_REST) longRestChoices else shortRestChoices,
            onConfirm = { replacements ->
                if (viewModel.restAndReplaceProgressionChoices(period, replacements)) {
                    replacementRest = null
                }
            },
            onDismiss = { replacementRest = null },
        )
    }
}

@Composable
private fun LargeResourceEditor(
    remaining: Int,
    maximum: Int,
    onSetRemaining: (Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        GameButton(
            label = "−1",
            dense = true,
            accent = Palette.TextMuted,
            enabled = remaining > 0,
            onClick = { onSetRemaining(remaining - 1) },
        )
        Text(
            text = "$remaining/$maximum",
            color = Palette.Gold,
            style = OnfallTheme.typography.numberSmall,
        )
        GameButton(
            label = "+1",
            dense = true,
            accent = Palette.TextMuted,
            enabled = remaining < maximum,
            onClick = { onSetRemaining(remaining + 1) },
        )
    }
}

private const val MAX_EDITABLE_RESOURCE_PIPS = 9

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RestChoiceReplacementDialog(
    period: RecoveryPeriod,
    choices: List<RestReplaceableChoice>,
    onConfirm: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val language = currentLanguage
    var replacements by remember(period, choices) {
        mutableStateOf<Map<String, String>>(emptyMap())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = {
            DialogTitle(
                if (period == RecoveryPeriod.LONG_REST) {
                    language.pick("Scelte del riposo lungo", "Long-rest choices")
                } else {
                    language.pick("Scelte del riposo breve", "Short-rest choices")
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    language.pick(
                        "Puoi modificare una o più scelte consentite e poi completare un solo riposo.",
                        "You can change one or more eligible choices, then finish a single rest.",
                    ),
                    color = Palette.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                choices.forEach { acquired ->
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Eyebrow(acquired.choice.title.uppercase())
                        Text(
                            language.pick("Attuale: ", "Current: ") +
                                acquired.selectedOptions.joinToString { it.label },
                            color = Palette.Text,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            acquired.replacementOptions.forEach { option ->
                                val selected = replacements[acquired.choice.id] == option.id
                                GameButton(
                                    option.label,
                                    accent = if (selected) Palette.Heal else Palette.TextMuted,
                                    selected = selected,
                                    dense = true,
                                    onClick = {
                                        replacements = if (selected) {
                                            replacements - acquired.choice.id
                                        } else {
                                            replacements + (acquired.choice.id to option.id)
                                        }
                                    },
                                )
                            }
                        }
                        replacements[acquired.choice.id]
                            ?.let { selectedId -> acquired.replacementOptions.firstOrNull { it.id == selectedId } }
                            ?.description
                            ?.takeIf(String::isNotBlank)
                            ?.let { description ->
                                Text(
                                    description,
                                    color = Palette.TextMuted,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                    }
                }
            }
        },
        confirmButton = {
            GameButton(
                language.pick("Applica e completa il riposo", "Apply and finish rest"),
                accent = Palette.Heal,
                enabled = replacements.isNotEmpty(),
                onClick = { onConfirm(replacements) },
            )
        },
        dismissButton = { GameButton(strings.common.cancel, onClick = onDismiss) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WildShapeReplacementDialog(
    knownForms: List<SrdBeastForm>,
    availableForms: List<SrdBeastForm>,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val words = strings.sheet
    var oldFormId by remember(knownForms) { mutableStateOf(knownForms.firstOrNull()?.id.orEmpty()) }
    var newFormId by remember(availableForms) {
        mutableStateOf(availableForms.firstOrNull()?.id.orEmpty())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { DialogTitle(words.swapKnownFormTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    words.swapKnownFormBody,
                    color = Palette.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Eyebrow(words.formToForgetCaps)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    knownForms.forEach { form ->
                        GameButton(
                            form.name,
                            accent = if (form.id == oldFormId) Palette.Gold else Palette.TextMuted,
                            selected = form.id == oldFormId,
                            dense = true,
                            onClick = { oldFormId = form.id },
                        )
                    }
                }
                Eyebrow(words.newFormCaps)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    availableForms.forEach { form ->
                        GameButton(
                            words.formSummary(form.name, form.summary),
                            accent = if (form.id == newFormId) Palette.Heal else Palette.TextMuted,
                            selected = form.id == newFormId,
                            dense = true,
                            onClick = { newFormId = form.id },
                        )
                    }
                }
            }
        },
        confirmButton = {
            GameButton(
                words.finishRestAndSwap,
                accent = Palette.Heal,
                onClick = {
                    if (oldFormId.isNotBlank() && newFormId.isNotBlank()) {
                        onConfirm(oldFormId, newFormId)
                    }
                },
            )
        },
        dismissButton = { GameButton(strings.common.cancel, onClick = onDismiss) },
    )
}
