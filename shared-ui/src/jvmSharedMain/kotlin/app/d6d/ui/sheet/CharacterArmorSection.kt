package app.d6d.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.d6d.rules.character.EffectTarget
import app.d6d.sheet.Ability
import app.d6d.sheet.ArmorCategory
import app.d6d.sheet.ArmorClassAdjustment
import app.d6d.sheet.ArmorClassDexterity
import app.d6d.sheet.ArmorClassMethod
import app.d6d.sheet.ArmorSpecialRule
import app.d6d.sheet.CharacterSheet
import app.d6d.ui.battle.GameButton
import app.d6d.ui.components.Chip
import app.d6d.ui.components.Eyebrow
import app.d6d.i18n.AppLanguage
import app.d6d.i18n.abbreviationIn
import app.d6d.i18n.pick
import app.d6d.sheet.i18n.distanceLabel
import app.d6d.sheet.i18n.label
import app.d6d.ui.i18n.SheetStrings
import app.d6d.ui.i18n.currentLanguage
import app.d6d.ui.i18n.strings
import app.d6d.ui.theme.Palette
import app.d6d.ui.theme.OnfallTheme

// --- classe armatura ---------------------------------------------------------------

/**
 * Calcolo trasparente della CA.
 *
 * La CA finale manuale resta disponibile per compatibilita' e casi eccezionali;
 * negli altri metodi l'armatura determina una sola base, alla quale vengono poi
 * sommati scudo e modificatori attivi.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ArmorClassSection(
    sheet: CharacterSheet,
    compact: Boolean,
    update: (CharacterSheet) -> Unit,
) {
    val language = currentLanguage
    val words = strings.sheet
    SheetBox(words.armorClassCalculation, Modifier.fillMaxWidth()) {
        sheet.armorClassOverride?.let { override ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Palette.Bloodied.copy(alpha = 0.10f), RoundedCornerShape(7.dp))
                    .border(1.dp, Palette.Bloodied.copy(alpha = 0.45f), RoundedCornerShape(7.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    words.overrideActive(override),
                    color = Palette.Text,
                    style = MaterialTheme.typography.bodySmall,
                )
                GameButton(
                    words.restoreCalculatedArmorClass(sheet.calculatedArmorClass),
                    accent = Palette.Bloodied,
                    dense = !compact,
                    onClick = { update(sheet.copy(armorClassOverride = null)) },
                )
            }
        }

        ArmorRuleWarnings(sheet)

        AdaptiveFormRow(
            compact = compact,
            items = arrayOf(
                adaptiveFormItem(1f) { itemModifier ->
                    ArmorClassBaseEditor(sheet, compact, update, itemModifier)
                },
                adaptiveFormItem(1.2f) { itemModifier ->
                    ArmorClassAdjustmentsEditor(sheet, compact, update, itemModifier)
                },
            ),
        )

        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Chip(words.baseArmorClass(sheet.baseArmorClass), Palette.Party)
            if (sheet.armorClassMethod != ArmorClassMethod.MANUAL_TOTAL) {
                Chip(words.armorClassModifiers(signed(sheet.armorClassAdjustmentTotal)), Palette.Gold)
                Chip(words.calculatedArmorClass(sheet.calculatedArmorClass), Palette.Heal)
            }
            sheet.armorClassOverride?.let { Chip(words.overrideValue(it), Palette.Bloodied) }
        }

        Text(
            armorClassFormula(sheet, words, language),
            color = Palette.TextMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ArmorRuleWarnings(sheet: CharacterSheet) {
    val words = strings.sheet
    val language = currentLanguage
    val missingTraining = sheet.wearingArmorWithoutTraining
    val insufficientStrength = sheet.armorStrengthRequirementNotMet
    val stealthDisadvantage = sheet.armorStealthDisadvantage
    if (!missingTraining && !insufficientStrength && !stealthDisadvantage) return

    Column(
        Modifier
            .fillMaxWidth()
            .background(Palette.Bloodied.copy(alpha = 0.10f), RoundedCornerShape(7.dp))
            .border(1.dp, Palette.Bloodied.copy(alpha = 0.55f), RoundedCornerShape(7.dp))
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (missingTraining) {
            Text(
                words.missingArmorProficiency(
                    sheet.wornArmorCategory?.label(language).orEmpty(),
                ),
                color = Palette.Bloodied,
                style = OnfallTheme.typography.supportingEmphasis,
            )
        }
        if (insufficientStrength) {
            Text(
                words.strengthBelowRequirement(
                    sheet.score(Ability.STRENGTH),
                    sheet.effectiveArmorMinimumStrength,
                    distanceLabel(sheet.armorSpeedPenaltyFeet, language),
                ),
                color = Palette.Bloodied,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (stealthDisadvantage) {
            Text(
                words.stealthDisadvantageWarning,
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ArmorClassBaseEditor(
    sheet: CharacterSheet,
    compact: Boolean,
    update: (CharacterSheet) -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = currentLanguage
    val words = strings.sheet
    Column(
        modifier
            .background(Palette.Night, RoundedCornerShape(7.dp))
            .border(1.dp, Palette.Line, RoundedCornerShape(7.dp))
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Eyebrow(words.baseMethodCaps)
        ArmorClassMethodSelector(
            selected = sheet.armorClassMethod,
            compact = compact,
        ) { method ->
            val previousCategory = sheet.wornArmorCategory
            val previousMinimumStrength = when (sheet.armorClassMethod) {
                ArmorClassMethod.MANUAL_TOTAL,
                ArmorClassMethod.CUSTOM_BASE -> sheet.manualArmorMinimumStrength
                else -> sheet.armorClassMethod.minimumStrength
            }
            val previousStealthDisadvantage = when (sheet.armorClassMethod) {
                ArmorClassMethod.MANUAL_TOTAL,
                ArmorClassMethod.CUSTOM_BASE -> sheet.manualArmorStealthDisadvantage
                else -> sheet.armorClassMethod.stealthDisadvantage
            }
            val updated = when {
                method == ArmorClassMethod.MANUAL_TOTAL &&
                    sheet.armorClassMethod != ArmorClassMethod.MANUAL_TOTAL ->
                    sheet.copy(
                        armorClass = sheet.effectiveArmorClass,
                        armorClassMethod = method,
                        manualArmorCategory = previousCategory,
                        manualArmorMinimumStrength = previousMinimumStrength,
                        manualArmorStealthDisadvantage = previousStealthDisadvantage,
                        armorClassOverride = null,
                    )

                method == ArmorClassMethod.CUSTOM_BASE &&
                    sheet.armorClassMethod != ArmorClassMethod.CUSTOM_BASE -> {
                    val candidate = sheet.copy(
                        armorClass = 0,
                        armorClassMethod = method,
                        customArmorClassDexterity = ArmorClassDexterity.NONE,
                        manualArmorCategory = previousCategory,
                        manualArmorMinimumStrength = previousMinimumStrength,
                        manualArmorStealthDisadvantage = previousStealthDisadvantage,
                        armorClassOverride = null,
                    ).withApplicableArmorSpecialRule()
                    candidate.copy(
                        armorClass = (
                            sheet.effectiveArmorClass - candidate.armorClassAdjustmentTotal
                            ).coerceAtLeast(0),
                    )
                }

                else -> sheet.copy(armorClassMethod = method, armorClassOverride = null)
            }
            update(updated.withApplicableArmorSpecialRule())
        }

        when (sheet.armorClassMethod) {
            ArmorClassMethod.MANUAL_TOTAL -> {
                SheetNumberField(words.manualFinalArmorClass, sheet.armorClass) {
                    update(
                        sheet.copy(
                            armorClass = it.coerceAtLeast(0),
                            armorClassOverride = null,
                        ),
                    )
                }
                Text(
                    words.manualFinalHint +
                        if (sheet.effectiveArmorSpecialRule == ArmorSpecialRule.ELVEN_CHAIN) {
                            words.elvenChainManualHint
                        } else {
                            ""
                        },
                    color = Palette.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            ArmorClassMethod.CUSTOM_BASE -> {
                SheetNumberField(words.baseStartingValue, sheet.armorClass) {
                    update(
                        sheet.copy(
                            armorClass = it.coerceAtLeast(0),
                            armorClassOverride = null,
                        ),
                    )
                }
                ArmorClassDexteritySelector(
                    selected = sheet.customArmorClassDexterity,
                    compact = compact,
                ) { rule ->
                    update(
                        sheet.copy(
                            customArmorClassDexterity = rule,
                            armorClassOverride = null,
                        ),
                    )
                }
            }

            else -> Unit
        }

        if (
            sheet.armorClassMethod == ArmorClassMethod.MANUAL_TOTAL ||
            sheet.armorClassMethod == ArmorClassMethod.CUSTOM_BASE
        ) {
            ArmorCategorySelector(
                selected = sheet.manualArmorCategory,
                compact = compact,
            ) { category ->
                update(
                    sheet.copy(
                        manualArmorCategory = category,
                        manualArmorMinimumStrength = if (category == ArmorCategory.HEAVY) {
                            sheet.manualArmorMinimumStrength
                        } else {
                            0
                        },
                        manualArmorStealthDisadvantage = if (category == null) {
                            false
                        } else {
                            sheet.manualArmorStealthDisadvantage
                        },
                        armorClassOverride = null,
                    ).withApplicableArmorSpecialRule(),
                )
            }
            sheet.manualArmorCategory?.let {
                if (sheet.manualArmorCategory == ArmorCategory.HEAVY) {
                    SheetNumberField(
                        words.armorStrengthRequirement,
                        sheet.manualArmorMinimumStrength,
                    ) {
                        update(
                            sheet.copy(
                                manualArmorMinimumStrength = it.coerceIn(0, 30),
                                armorClassOverride = null,
                            ),
                        )
                    }
                }
                SheetCheck(
                    words.stealthDisadvantage,
                    sheet.manualArmorStealthDisadvantage,
                ) {
                    update(
                        sheet.copy(
                            manualArmorStealthDisadvantage = it,
                            armorClassOverride = null,
                        ),
                    )
                }
            }
        }

        sheet.wornArmorCategory?.let {
            ArmorSpecialRuleSelector(sheet, compact) { specialRule ->
                update(sheet.copy(armorSpecialRule = specialRule, armorClassOverride = null))
            }
            Text(
                words.donDoffMinutes(sheet.armorDonMinutes, sheet.armorDoffMinutes),
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            when (sheet.effectiveArmorSpecialRule) {
                ArmorSpecialRule.MITHRAL -> Text(
                    words.mithralNote,
                    color = Palette.Heal,
                    style = MaterialTheme.typography.bodySmall,
                )
                ArmorSpecialRule.ELVEN_CHAIN -> Text(
                    words.elvenChainNote,
                    color = Palette.Heal,
                    style = MaterialTheme.typography.bodySmall,
                )
                ArmorSpecialRule.STANDARD -> Unit
            }
        }

        Text(
            armorClassBaseFormula(sheet, words, language),
            color = Palette.Party,
            style = OnfallTheme.typography.supportingEmphasis,
        )
    }
}

@Composable
private fun ArmorClassMethodSelector(
    selected: ArmorClassMethod,
    compact: Boolean,
    onSelect: (ArmorClassMethod) -> Unit,
) {
    val language = currentLanguage
    var expanded by remember { mutableStateOf(false) }
    Box {
        GameButton(
            label = selected.label(language),
            accent = Palette.Party,
            selected = true,
            dense = !compact,
            onClick = { expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .width(310.dp)
                .heightIn(max = 430.dp)
                .background(Palette.SurfaceHigh),
        ) {
            ArmorClassMethod.entries.forEach { method ->
                DropdownMenuItem(
                    text = {
                        Text(
                            method.label(language),
                            color = if (method == selected) Palette.GoldBright else Palette.Text,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(method)
                    },
                )
            }
        }
    }
}

@Composable
private fun ArmorClassDexteritySelector(
    selected: ArmorClassDexterity,
    compact: Boolean,
    onSelect: (ArmorClassDexterity) -> Unit,
) {
    val words = strings.sheet
    val language = currentLanguage
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(words.dexterityContributionCaps, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
        Box {
            GameButton(
                label = selected.label(language),
                accent = Palette.TextMuted,
                dense = !compact,
                onClick = { expanded = true },
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Palette.SurfaceHigh),
            ) {
                ArmorClassDexterity.entries.forEach { rule ->
                    DropdownMenuItem(
                        text = { Text(rule.label(language), color = Palette.Text) },
                        onClick = {
                            expanded = false
                            onSelect(rule)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ArmorCategorySelector(
    selected: ArmorCategory?,
    compact: Boolean,
    onSelect: (ArmorCategory?) -> Unit,
) {
    val words = strings.sheet
    val language = currentLanguage
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(words.wornArmorCaps, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
        Box {
            GameButton(
                label = selected?.label(language)?.replaceFirstChar { it.uppercase() } ?: words.noArmor,
                accent = Palette.Party,
                dense = !compact,
                onClick = { expanded = true },
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Palette.SurfaceHigh),
            ) {
                (listOf<ArmorCategory?>(null) + ArmorCategory.entries).forEach { category ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                category?.label(language)?.replaceFirstChar { it.uppercase() } ?: words.noArmor,
                                color = Palette.Text,
                            )
                        },
                        onClick = {
                            expanded = false
                            onSelect(category)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ArmorSpecialRuleSelector(
    sheet: CharacterSheet,
    compact: Boolean,
    onSelect: (ArmorSpecialRule) -> Unit,
) {
    val words = strings.sheet
    val language = currentLanguage
    val choices = ArmorSpecialRule.entries.filter { rule ->
        sheet.copy(armorSpecialRule = rule).effectiveArmorSpecialRule == rule
    }
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(words.armorVariantCaps, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
        Box {
            GameButton(
                label = sheet.effectiveArmorSpecialRule.label(language),
                accent = Palette.Heal,
                dense = !compact,
                onClick = { expanded = true },
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Palette.SurfaceHigh),
            ) {
                choices.forEach { rule ->
                    DropdownMenuItem(
                        text = { Text(rule.label(language), color = Palette.Text) },
                        onClick = {
                            expanded = false
                            onSelect(rule)
                        },
                    )
                }
            }
        }
    }
}

private fun CharacterSheet.withApplicableArmorSpecialRule(): CharacterSheet =
    if (effectiveArmorSpecialRule == armorSpecialRule) {
        this
    } else {
        copy(armorSpecialRule = ArmorSpecialRule.STANDARD)
    }

@Composable
private fun ArmorClassAdjustmentsEditor(
    sheet: CharacterSheet,
    compact: Boolean,
    update: (CharacterSheet) -> Unit,
    modifier: Modifier = Modifier,
) {
    val words = strings.sheet
    Column(
        modifier
            .background(Palette.Night, RoundedCornerShape(7.dp))
            .border(1.dp, Palette.Line, RoundedCornerShape(7.dp))
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Eyebrow(words.armorClassModifiersCaps)

        SheetCheck(
            label = when {
                !sheet.shieldEquipped -> words.shieldNotEquipped
                sheet.armorClassMethod == ArmorClassMethod.MANUAL_TOTAL ->
                    words.shieldAlreadyInManual
                sheet.armorTraining.shields -> words.shieldEquipped
                else -> words.shieldWithoutProficiency
            },
            checked = sheet.shieldEquipped,
        ) {
            update(sheet.copy(shieldEquipped = it, armorClassOverride = null))
        }
        Text(
            words.shieldActionNote,
            color = Palette.TextMuted,
            style = MaterialTheme.typography.labelSmall,
        )
        if (sheet.shieldEquipped && !sheet.armorTraining.shields) {
            Text(
                words.shieldNeedsProficiency,
                color = Palette.Bloodied,
                style = MaterialTheme.typography.labelSmall,
            )
        }

        if (sheet.armorClassMethod == ArmorClassMethod.MANUAL_TOTAL) {
            Text(
                words.manualTotalNote,
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            return@Column
        }

        // I bonus che arrivano dai privilegi non sono modificabili qui: si
        // mostrano perche' chi legge la CA possa risalire a chi la produce.
        sheet.activeEffects(EffectTarget.ARMOR_CLASS).forEach { effect ->
            Text(
                text = words.effectRow(effect.source, effect.readableText(strings)),
                color = Palette.Heal,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (sheet.armorClassAdjustments.isEmpty()) {
            Text(
                words.noOtherModifier,
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        sheet.armorClassAdjustments.forEachIndexed { index, adjustment ->
            key(adjustment.id.ifBlank { "legacy-$index-${adjustment.source}" }) {
                ArmorClassAdjustmentRow(
                    adjustment = adjustment,
                    compact = compact,
                    onChange = { changed ->
                        update(
                            sheet.copy(
                                armorClassAdjustments = sheet.armorClassAdjustments.mapIndexed { current, value ->
                                    if (current == index) changed else value
                                },
                                armorClassOverride = null,
                            ),
                        )
                    },
                    onRemove = {
                        update(
                            sheet.copy(
                                armorClassAdjustments = sheet.armorClassAdjustments.filterIndexed { current, _ ->
                                    current != index
                                },
                                armorClassOverride = null,
                            ),
                        )
                    },
                )
            }
        }

        GameButton(
            label = words.addModifier,
            accent = Palette.Party,
            dense = !compact,
            onClick = {
                val adjustment = ArmorClassAdjustment(
                    source = words.newModifier,
                    id = "ca-${System.nanoTime()}-${sheet.armorClassAdjustments.size}",
                )
                update(
                    sheet.copy(
                        armorClassAdjustments = sheet.armorClassAdjustments + adjustment,
                        armorClassOverride = null,
                    ),
                )
            },
        )
    }
}

@Composable
private fun ArmorClassAdjustmentRow(
    adjustment: ArmorClassAdjustment,
    compact: Boolean,
    onChange: (ArmorClassAdjustment) -> Unit,
    onRemove: () -> Unit,
) {
    val words = strings.sheet
    val language = currentLanguage
    val container = Modifier
        .fillMaxWidth()
        .background(Palette.Surface, RoundedCornerShape(6.dp))
        .border(
            1.dp,
            if (adjustment.active) Palette.Gold.copy(alpha = 0.35f) else Palette.Line,
            RoundedCornerShape(6.dp),
        )
        .padding(7.dp)

    if (compact) {
        Column(container, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SheetField(language.pick("Fonte", "Source"), adjustment.source) {
                onChange(adjustment.copy(source = it))
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SheetCheck(strings.sheet.activeLabel, adjustment.active, Modifier.weight(1f)) {
                    onChange(adjustment.copy(active = it))
                }
                SheetNumberField(words.bonusOrPenalty, adjustment.value, Modifier.width(92.dp)) {
                    onChange(adjustment.copy(value = it))
                }
                GameButton("−1", dense = false, accent = Palette.TextMuted, onClick = {
                    onChange(adjustment.copy(value = adjustment.value - 1))
                })
                GameButton("+1", dense = false, accent = Palette.TextMuted, onClick = {
                    onChange(adjustment.copy(value = adjustment.value + 1))
                })
            }
            GameButton(strings.common.remove, accent = Palette.Enemy, onClick = onRemove)
        }
    } else {
        Row(
            container,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SheetCheck(strings.sheet.activeLabel, adjustment.active) { onChange(adjustment.copy(active = it)) }
            SheetField(language.pick("Fonte", "Source"), adjustment.source, Modifier.weight(1f)) {
                onChange(adjustment.copy(source = it))
            }
            GameButton("−", dense = true, accent = Palette.TextMuted, onClick = {
                onChange(adjustment.copy(value = adjustment.value - 1))
            })
            SheetNumberField(words.bonusOrPenalty, adjustment.value, Modifier.width(90.dp)) {
                onChange(adjustment.copy(value = it))
            }
            GameButton("+", dense = true, accent = Palette.TextMuted, onClick = {
                onChange(adjustment.copy(value = adjustment.value + 1))
            })
            GameButton(strings.common.remove, dense = true, accent = Palette.Enemy, onClick = onRemove)
        }
    }
}

private fun armorClassBaseFormula(
    sheet: CharacterSheet,
    words: SheetStrings,
    language: AppLanguage,
): String {
    if (sheet.armorClassMethod == ArmorClassMethod.MANUAL_TOTAL) {
        return words.manualFinalArmorClassIs(sheet.armorClass)
    }
    val base = if (sheet.armorClassMethod == ArmorClassMethod.CUSTOM_BASE) {
        sheet.armorClass
    } else {
        sheet.armorClassMethod.baseValue
    }
    val rule = if (sheet.armorClassMethod == ArmorClassMethod.CUSTOM_BASE) {
        sheet.customArmorClassDexterity
    } else {
        sheet.armorClassMethod.dexterity
    }
    val dexterity = sheet.modifier(Ability.DEXTERITY)
    val contribution = rule.contribution(dexterity)
    val detail = when (rule) {
        ArmorClassDexterity.FULL -> words.baseWithFullDexterity(base, signed(dexterity))
        ArmorClassDexterity.MAX_TWO -> words.baseWithCappedDexterity(base, signed(contribution))
        ArmorClassDexterity.NONE -> words.baseWithoutDexterity(base)
    }
    val secondary = sheet.armorClassMethod.secondaryAbility?.let { ability ->
        words.plusSecondaryAbility(ability.abbreviationIn(language), signed(sheet.modifier(ability)))
    }.orEmpty()
    return words.equalsBaseArmorClass(detail, secondary, sheet.baseArmorClass)
}

private fun armorClassFormula(
    sheet: CharacterSheet,
    words: SheetStrings,
    language: AppLanguage,
): String {
    if (sheet.armorClassMethod == ArmorClassMethod.MANUAL_TOTAL) {
        return words.manualFinalArmorClassIs(sheet.armorClass) +
            (sheet.armorClassOverride?.let { words.overrideSuffix(it) } ?: "")
    }
    val pieces = buildList {
        add(words.baseArmorClass(sheet.baseArmorClass))
        if (sheet.shieldArmorClassBonus != 0) add(words.shieldRow(signed(sheet.shieldArmorClassBonus)))
        if (sheet.armorSpecialArmorClassBonus != 0) {
            add(
                words.armorRuleRow(
                    sheet.effectiveArmorSpecialRule.label(language),
                    signed(sheet.armorSpecialArmorClassBonus),
                ),
            )
        }
        sheet.armorClassAdjustments
            .filter { it.active && it.value != 0 }
            .forEach { add("${it.source.ifBlank { words.unnamedModifier }} ${signed(it.value)}") }
    }
    return pieces.joinToString(" · ") +
        words.equalsCalculatedArmorClass(sheet.calculatedArmorClass) +
        (sheet.armorClassOverride?.let { words.currentArmorClassOverride(it) } ?: "")
}
