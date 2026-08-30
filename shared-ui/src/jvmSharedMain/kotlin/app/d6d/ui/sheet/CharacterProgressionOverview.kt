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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.d6d.rules.character.RecoveryPeriod
import app.d6d.content.srd521it.SrdBeastForm
import app.d6d.sheet.CharacterSheet
import app.d6d.sheet.isPactSlotMirrorResourceId
import app.d6d.ui.battle.GameButton
import app.d6d.ui.components.Chip
import app.d6d.i18n.label
import app.d6d.i18n.pick
import app.d6d.ui.i18n.currentLanguage
import app.d6d.ui.i18n.strings
import app.d6d.ui.theme.Palette

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
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
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
        val visibleResourcePools = sheet.progression.resourcePools
            .filter { it.maximum > 0 }
            .filterNot { hasPactSlots && it.resourceId.isPactSlotMirrorResourceId() }
        if (visibleResourcePools.isNotEmpty()) {
            Text(words.classResourcesCaps, color = Palette.Gold, style = MaterialTheme.typography.labelSmall)
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
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall,
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
                if (
                    viewModel.knownWildShapeForms().isNotEmpty() &&
                    viewModel.wildShapeReplacementOptions().isNotEmpty()
                ) {
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
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodySmall,
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
        title = { Text(words.swapKnownFormTitle, color = Palette.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    words.swapKnownFormBody,
                    color = Palette.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(words.formToForgetCaps, color = Palette.Gold, style = MaterialTheme.typography.labelSmall)
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
                Text(words.newFormCaps, color = Palette.Gold, style = MaterialTheme.typography.labelSmall)
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
