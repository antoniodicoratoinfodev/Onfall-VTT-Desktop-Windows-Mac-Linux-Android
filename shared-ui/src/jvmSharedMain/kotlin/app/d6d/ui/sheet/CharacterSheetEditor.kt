package app.d6d.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.d6d.i18n.abbreviationIn
import app.d6d.i18n.label
import app.d6d.i18n.pick
import app.d6d.sheet.i18n.damageText
import app.d6d.sheet.i18n.distanceLabel
import app.d6d.sheet.i18n.label as sheetLabel
import app.d6d.ui.i18n.currentLanguage
import app.d6d.ui.i18n.strings
import app.d6d.domain.combat.ActivationCost
import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.CharacterSkillDefinition
import app.d6d.rules.character.CharacterStatDefinition
import app.d6d.rules.character.RuleElementKind
import app.d6d.sheet.Ability
import app.d6d.sheet.CatalogAbility
import app.d6d.sheet.CharacterSheet
import app.d6d.sheet.CreatureSize
import app.d6d.sheet.ModularSheetField
import app.d6d.sheet.ModularSheetFieldKind
import app.d6d.sheet.ModularSheetValue
import app.d6d.sheet.Proficiency
import app.d6d.sheet.SpellSlot
import app.d6d.sheet.Spellcasting
import app.d6d.sheet.WeaponEntry
import app.d6d.ui.battle.GameButton
import app.d6d.ui.battle.label
import app.d6d.ui.components.Chip
import app.d6d.ui.components.ClassIcon
import app.d6d.ui.images.PortraitPicker
import app.d6d.ui.images.PortraitRepository
import app.d6d.ui.runDiskIo
import app.d6d.ui.theme.Palette
import kotlinx.coroutines.launch

/**
 * Scheda del personaggio, disposta come la scheda ufficiale italiana 2024.
 *
 * I valori che sulla carta si calcolano a mano — modificatori, tiri salvezza,
 * bonus di abilita', iniziativa, Percezione passiva, CD degli incantesimi — qui
 * sono derivati e non modificabili: e' il vantaggio della versione digitale, e
 * evita che la scheda contraddica se stessa.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CharacterSheetEditor(
    viewModel: SheetViewModel,
    portraits: PortraitRepository,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val strings = strings
    val words = strings.sheet
    val scope = rememberCoroutineScope()
    val sheet = viewModel.character
    val statDefinitions = viewModel.statDefinitionsFor(sheet)
    val skillDefinitions = viewModel.skillDefinitionsFor(sheet)
    val displayedClassName = viewModel.displayedClassName(sheet)
    val displayedSubclassName = viewModel.displayedSubclassName(sheet)
    val update: (CharacterSheet) -> Unit = { viewModel.character = it }
    var deleteId by remember(viewModel.selectedId) { mutableStateOf<String?>(null) }
    var showProgressionDialog by remember(viewModel.selectedId) { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            HeaderSection(
                sheet,
                portraits,
                compact,
                displayedClassName,
                displayedSubclassName,
                update,
            )
            ProgressionOverview(
                viewModel = viewModel,
                sheet = sheet,
                compact = compact,
                onOpenProgression = { showProgressionDialog = true },
            )
            ArmorClassSection(sheet, compact, update)

            if (compact) {
                AbilitiesColumn(sheet, statDefinitions, skillDefinitions, update, Modifier.fillMaxWidth())
                CombatColumn(
                    viewModel,
                    sheet,
                    update,
                    availableAbilities = viewModel.abilityCatalog,
                    statDefinitions = statDefinitions,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    AbilitiesColumn(sheet, statDefinitions, skillDefinitions, update, Modifier.width(292.dp))
                    CombatColumn(
                        viewModel,
                        sheet,
                        update,
                        availableAbilities = viewModel.abilityCatalog,
                        statDefinitions = statDefinitions,
                        compact = false,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            SpellcastingSection(viewModel, sheet, compact, update)
            CharacterNotesSection(sheet, compact, update)
            ModularSheetSections(viewModel, sheet, compact)
        }

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(Palette.Surface)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            GameButton(words.saveSheet, accent = Palette.Heal, onClick = {
                scope.launch { runDiskIo { viewModel.save() } }
            })
            viewModel.selectedId?.let { id ->
                GameButton(strings.common.delete, accent = Palette.Enemy, onClick = { deleteId = id })
            }
            if (viewModel.isDirty) {
                Text(
                    words.unsavedChanges,
                    color = Palette.Bloodied,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }
    }

    deleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            containerColor = Palette.Surface,
            title = { Text(words.deleteSheetTitle, color = Palette.Text) },
            text = {
                Text(
                    words.deleteSheetBody(sheet.characterName.ifBlank { strings.common.unnamed }),
                    color = Palette.TextMuted,
                )
            },
            confirmButton = {
                GameButton(strings.common.delete, accent = Palette.Enemy, onClick = {
                    scope.launch {
                        runDiskIo { viewModel.delete(id) }
                        deleteId = null
                    }
                })
            },
            dismissButton = {
                GameButton(strings.common.cancel, accent = Palette.TextMuted, onClick = { deleteId = null })
            },
        )
    }

    if (showProgressionDialog) {
        SrdProgressionDialog(viewModel) { showProgressionDialog = false }
    }
}

// --- intestazione -----------------------------------------------------------------


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeaderSection(
    sheet: CharacterSheet,
    portraits: PortraitRepository,
    compact: Boolean,
    displayedClassName: String,
    displayedSubclassName: String,
    update: (CharacterSheet) -> Unit,
) {
    val strings = strings
    val words = strings.sheet
    val language = strings.language
    if (compact) {
        CompactHeaderSection(
            sheet,
            portraits,
            displayedClassName,
            displayedSubclassName,
            update,
        )
        return
    }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        SheetBox(language.pick("Ritratto", "Portrait"), Modifier.width(150.dp)) {
            PortraitPicker(portraits, sheet.id, sheet.characterName)
        }

        SheetBox(strings.compendium.characterLabel, Modifier.weight(2.4f)) {
            sheet.progression.classLevels.firstOrNull()?.classId?.let {
                ClassIcon(it, size = 36.dp)
            }
            SheetField(words.characterName, sheet.characterName) {
                update(sheet.copy(characterName = it))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SheetField("Background", sheet.background, Modifier.weight(1f)) {
                    update(sheet.copy(background = it))
                }
                if (sheet.progression.configured) {
                    DerivedValue(
                        strings.compendium.classLabel,
                        displayedClassName,
                        Modifier.weight(1f),
                        accent = Palette.Party,
                    )
                } else {
                    SheetField(strings.compendium.classLabel, sheet.className, Modifier.weight(1f)) {
                        update(sheet.copy(className = it))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SheetField(language.pick("Specie", "Species"), sheet.species, Modifier.weight(1f)) {
                    update(sheet.copy(species = it))
                }
                if (sheet.progression.configured) {
                    DerivedValue(
                        language.pick("Sottoclasse", "Subclass"),
                        displayedSubclassName.ifBlank { "—" },
                        Modifier.weight(1f),
                        accent = Palette.Party,
                    )
                } else {
                    SheetField(language.pick("Sottoclasse", "Subclass"), sheet.subclass, Modifier.weight(1f)) {
                        update(sheet.copy(subclass = it))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                if (sheet.progression.configured) {
                    DerivedValue(strings.common.level, sheet.effectiveLevel.toString(), Modifier.weight(1f))
                } else {
                    SheetNumberField(strings.common.level, sheet.level, Modifier.weight(1f)) {
                        update(sheet.copy(level = it.coerceAtLeast(1)))
                    }
                }
                SheetNumberField(language.pick("PE", "XP"), sheet.experiencePoints, Modifier.weight(1f)) {
                    update(sheet.copy(experiencePoints = it.coerceAtLeast(0)))
                }
            }
        }

        DerivedValue(
            words.currentArmorClass,
            sheet.effectiveArmorClass.toString(),
            Modifier.width(120.dp),
            accent = Palette.Party,
        )

        SheetBox(words.hitPoints, Modifier.weight(1.2f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SheetNumberField(language.pick("Attuali", "Current"), sheet.currentHitPoints, Modifier.weight(1f)) {
                    update(sheet.copy(currentHitPoints = it.coerceIn(0, sheet.maxHitPoints)))
                }
                SheetNumberField("Max", sheet.maxHitPoints, Modifier.weight(1f)) {
                    update(sheet.copy(maxHitPoints = it.coerceAtLeast(1)))
                }
            }
            SheetNumberField(language.pick("Temporanei", "Temporary"), sheet.temporaryHitPoints) {
                update(sheet.copy(temporaryHitPoints = it.coerceAtLeast(0)))
            }
        }

        SheetBox(words.hitDice, Modifier.width(122.dp)) {
            SheetNumberField(language.pick("Spesi", "Spent"), sheet.hitDiceSpent) {
                update(sheet.copy(hitDiceSpent = it.coerceIn(0, sheet.hitDiceMax)))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SheetNumberField("Max", sheet.hitDiceMax, Modifier.weight(1f)) {
                    update(sheet.copy(hitDiceMax = it.coerceAtLeast(0)))
                }
                SheetNumberField("d", sheet.hitDieSides, Modifier.weight(1f)) {
                    update(sheet.copy(hitDieSides = it.coerceAtLeast(2)))
                }
            }
        }

        SheetBox(words.deathSaves, Modifier.width(136.dp)) {
            Text(language.pick("Successi", "Successes"), color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
            PipRow(3, sheet.deathSaveSuccesses, color = Palette.Heal) {
                update(sheet.copy(deathSaveSuccesses = it))
            }
            Text(strings.sheet.failures, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
            PipRow(3, sheet.deathSaveFailures, color = Palette.Critical) {
                update(sheet.copy(deathSaveFailures = it))
            }
        }
    }
}

@Composable
private fun CompactHeaderSection(
    sheet: CharacterSheet,
    portraits: PortraitRepository,
    displayedClassName: String,
    displayedSubclassName: String,
    update: (CharacterSheet) -> Unit,
) {
    val strings = strings
    val words = strings.sheet
    val language = strings.language
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        SheetBox(language.pick("Ritratto", "Portrait"), Modifier.fillMaxWidth()) {
            PortraitPicker(
                repository = portraits,
                definitionId = sheet.id,
                name = sheet.characterName,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        SheetBox(strings.compendium.characterLabel, Modifier.fillMaxWidth()) {
            sheet.progression.classLevels.firstOrNull()?.classId?.let {
                ClassIcon(it, size = 36.dp)
            }
            SheetField(words.characterName, sheet.characterName) {
                update(sheet.copy(characterName = it))
            }
            AdaptiveFormRow(
                compact = true,
                compactColumns = 2,
                items = arrayOf(
                    adaptiveFormItem { fieldModifier ->
                        SheetField("Background", sheet.background, fieldModifier) {
                            update(sheet.copy(background = it))
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        if (sheet.progression.configured) {
                            DerivedValue(
                                strings.compendium.classLabel,
                                displayedClassName,
                                fieldModifier,
                                accent = Palette.Party,
                            )
                        } else {
                            SheetField(strings.compendium.classLabel, sheet.className, fieldModifier) {
                                update(sheet.copy(className = it))
                            }
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetField(language.pick("Specie", "Species"), sheet.species, fieldModifier) {
                            update(sheet.copy(species = it))
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        if (sheet.progression.configured) {
                            DerivedValue(
                                language.pick("Sottoclasse", "Subclass"),
                                displayedSubclassName.ifBlank { "—" },
                                fieldModifier,
                                accent = Palette.Party,
                            )
                        } else {
                            SheetField(language.pick("Sottoclasse", "Subclass"), sheet.subclass, fieldModifier) {
                                update(sheet.copy(subclass = it))
                            }
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        if (sheet.progression.configured) {
                            DerivedValue(strings.common.level, sheet.effectiveLevel.toString(), fieldModifier)
                        } else {
                            SheetNumberField(strings.common.level, sheet.level, fieldModifier) {
                                update(sheet.copy(level = it.coerceAtLeast(1)))
                            }
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetNumberField(language.pick("PE", "XP"), sheet.experiencePoints, fieldModifier) {
                            update(sheet.copy(experiencePoints = it.coerceAtLeast(0)))
                        }
                    },
                ),
            )
        }

        DerivedValue(
            words.currentArmorClass,
            sheet.effectiveArmorClass.toString(),
            Modifier.fillMaxWidth(),
            accent = Palette.Party,
        )

        SheetBox(words.hitPoints, Modifier.fillMaxWidth()) {
            AdaptiveFormRow(
                compact = true,
                compactColumns = 2,
                items = arrayOf(
                    adaptiveFormItem { fieldModifier ->
                        SheetNumberField(language.pick("Attuali", "Current"), sheet.currentHitPoints, fieldModifier) {
                            update(sheet.copy(currentHitPoints = it.coerceIn(0, sheet.maxHitPoints)))
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetNumberField("Max", sheet.maxHitPoints, fieldModifier) {
                            update(sheet.copy(maxHitPoints = it.coerceAtLeast(1)))
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetNumberField(language.pick("Temporanei", "Temporary"), sheet.temporaryHitPoints, fieldModifier) {
                            update(sheet.copy(temporaryHitPoints = it.coerceAtLeast(0)))
                        }
                    },
                ),
            )
        }

        SheetBox(words.hitDice, Modifier.fillMaxWidth()) {
            AdaptiveFormRow(
                compact = true,
                compactColumns = 2,
                items = arrayOf(
                    adaptiveFormItem { fieldModifier ->
                        SheetNumberField(language.pick("Spesi", "Spent"), sheet.hitDiceSpent, fieldModifier) {
                            update(sheet.copy(hitDiceSpent = it.coerceIn(0, sheet.hitDiceMax)))
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetNumberField("Max", sheet.hitDiceMax, fieldModifier) {
                            update(sheet.copy(hitDiceMax = it.coerceAtLeast(0)))
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetNumberField("d", sheet.hitDieSides, fieldModifier) {
                            update(sheet.copy(hitDieSides = it.coerceAtLeast(2)))
                        }
                    },
                ),
            )
        }

        SheetBox(words.deathSaves, Modifier.fillMaxWidth()) {
            Text(language.pick("Successi", "Successes"), color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
            PipRow(3, sheet.deathSaveSuccesses, color = Palette.Heal) {
                update(sheet.copy(deathSaveSuccesses = it))
            }
            Text(strings.sheet.failures, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
            PipRow(3, sheet.deathSaveFailures, color = Palette.Critical) {
                update(sheet.copy(deathSaveFailures = it))
            }
        }
    }
}


// --- colonna sinistra: caratteristiche ---------------------------------------------

@Composable
private fun AbilitiesColumn(
    sheet: CharacterSheet,
    statDefinitions: List<CharacterStatDefinition>,
    skillDefinitions: List<CharacterSkillDefinition>,
    update: (CharacterSheet) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = strings
    val words = strings.sheet
    val language = strings.language
    Column(modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        DerivedValue(
            words.proficiencyBonus,
            signed(sheet.proficiencyBonus),
            Modifier.fillMaxWidth(),
        )

        statDefinitions.forEach { definition ->
            AbilityBlock(definition, skillDefinitions, sheet, update)
        }

        SheetBox(words.heroicInspiration) {
            SheetCheck(
                if (sheet.heroicInspiration) words.available else words.notAvailable,
                sheet.heroicInspiration,
            ) { update(sheet.copy(heroicInspiration = it)) }
        }

        SheetBox(words.trainingAndProficiencies) {
            Text(
                words.armorTraining,
                color = Palette.TextMuted,
                style = MaterialTheme.typography.labelSmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SheetCheck(language.pick("Leggera", "Light"), sheet.armorTraining.light) {
                    update(sheet.copy(armorTraining = sheet.armorTraining.copy(light = it)))
                }
                SheetCheck(language.pick("Media", "Medium"), sheet.armorTraining.medium) {
                    update(sheet.copy(armorTraining = sheet.armorTraining.copy(medium = it)))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SheetCheck(language.pick("Pesante", "Heavy"), sheet.armorTraining.heavy) {
                    update(sheet.copy(armorTraining = sheet.armorTraining.copy(heavy = it)))
                }
                SheetCheck(language.pick("Scudi", "Shields"), sheet.armorTraining.shields) {
                    update(sheet.copy(armorTraining = sheet.armorTraining.copy(shields = it)))
                }
            }
            SheetField(strings.sheet.weapons, sheet.weaponProficiencies) {
                update(sheet.copy(weaponProficiencies = it))
            }
            SheetField(words.tools, sheet.toolProficiencies) {
                update(sheet.copy(toolProficiencies = it))
            }
        }
    }
}

/**
 * Blocco di una caratteristica: punteggio scritto, modificatore derivato, tiro
 * salvezza e abilita' governate.
 */
@Composable
private fun AbilityBlock(
    definition: CharacterStatDefinition,
    skillDefinitions: List<CharacterSkillDefinition>,
    sheet: CharacterSheet,
    update: (CharacterSheet) -> Unit,
) {
    val strings = strings
    val words = strings.sheet
    val ability = definition.id
    val skills = skillDefinitions.filter { it.statId == ability }

    SheetBox(definition.name) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SheetNumberField(strings.sheet.score, sheet.score(ability), Modifier.weight(1f)) { score ->
                update(
                    sheet.copy(
                        abilityScores = sheet.abilityScores +
                            (ability to score.coerceIn(definition.minimumScore, definition.maximumScore)),
                    ),
                )
            }
            DerivedValue(words.modifier, signed(sheet.modifier(ability)))
        }

        ProficiencyLine(
            label = words.savingThrow,
            bonus = sheet.saveBonus(ability),
            level = sheet.saveProficiencies[ability] ?: Proficiency.NONE,
            bold = true,
            disadvantage = sheet.hasDisadvantageOnSave(ability),
        ) { next ->
            update(sheet.copy(saveProficiencies = sheet.saveProficiencies + (ability to next)))
        }

        skills.forEach { skillDefinition ->
            val skill = skillDefinition.id
            ProficiencyLine(
                label = skillDefinition.name,
                bonus = sheet.skillBonus(skill),
                level = sheet.skillProficiencies[skill] ?: Proficiency.NONE,
                disadvantage = sheet.hasDisadvantageOnSkill(skill),
            ) { next ->
                update(sheet.copy(skillProficiencies = sheet.skillProficiencies + (skill to next)))
            }
        }
    }
}

/** Riga "pallino — bonus — nome", il pattern ripetuto della colonna sinistra. */
@Composable
private fun ProficiencyLine(
    label: String,
    bonus: Int,
    level: Proficiency,
    bold: Boolean = false,
    disadvantage: Boolean = false,
    onCycle: (Proficiency) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProficiencyDot(
            filled = level == Proficiency.PROFICIENT,
            expertise = level == Proficiency.EXPERTISE,
            onClick = {
                // Ciclo fra i tre stati: nessuna competenza, competente, maestria.
                onCycle(
                    when (level) {
                        Proficiency.NONE -> Proficiency.PROFICIENT
                        Proficiency.PROFICIENT -> Proficiency.EXPERTISE
                        Proficiency.EXPERTISE -> Proficiency.NONE
                    },
                )
            },
        )
        Text(
            text = signed(bonus),
            color = Palette.Text,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(26.dp),
        )
        Text(
            text = label,
            color = if (bold) Palette.Text else Palette.TextMuted,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.bodySmall,
        )
        if (disadvantage) {
            Text(
                text = currentLanguage.pick("SVANT.", "DISADV."),
                color = Palette.Bloodied,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

// --- colonna destra: combattimento --------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CombatColumn(
    viewModel: SheetViewModel,
    sheet: CharacterSheet,
    update: (CharacterSheet) -> Unit,
    availableAbilities: List<CatalogAbility>,
    statDefinitions: List<CharacterStatDefinition>,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val strings = strings
    val words = strings.sheet
    val language = currentLanguage
    var abilityPickerOpen by remember(sheet.id) { mutableStateOf(false) }
    var traitPickerSection by remember(sheet.id) {
        mutableStateOf<CharacterTraitSection?>(null)
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        AdaptiveFormRow(
            compact = compact,
            compactColumns = 2,
            items = arrayOf(
                adaptiveFormItem { itemModifier ->
                    DerivedValue(
                        strings.sheet.initiative,
                        signed(sheet.initiativeModifier) +
                            if (sheet.strengthDexterityD20Disadvantage) words.disadvantageShort else "",
                        itemModifier,
                    )
                },
                adaptiveFormItem { itemModifier ->
                    SheetBox(language.pick("Velocita'", "Speed"), itemModifier) {
                        SheetMetreField(language.pick("Base", "Base"), sheet.speedFeet) {
                            update(sheet.copy(speedFeet = it.coerceAtLeast(0)))
                        }
                        if (sheet.armorSpeedPenaltyFeet > 0) {
                            Text(
                                words.effectiveSpeed(distanceLabel(sheet.effectiveSpeedFeet, language)),
                                color = Palette.Bloodied,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                },
                adaptiveFormItem(1.3f) { itemModifier ->
                    SheetBox(language.pick("Taglia", "Size"), itemModifier) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            CreatureSize.entries.forEach { size ->
                                SheetCheck(size.sheetLabel(language), sheet.size == size) {
                                    if (it) update(sheet.copy(size = size))
                                }
                            }
                        }
                    }
                },
                adaptiveFormItem { itemModifier ->
                    DerivedValue(words.passivePerception, sheet.passivePerception.toString(), itemModifier)
                },
            ),
        )

        SheetBox(words.weaponsAndCombatAbilities) {
            if (!compact) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ColumnHeader(strings.common.nameLabel, Modifier.weight(2f))
                    ColumnHeader(words.attackBonusOrDc, Modifier.weight(1f))
                    ColumnHeader(words.damageAndType, Modifier.weight(1.6f))
                    ColumnHeader(language.pick("Note", "Notes"), Modifier.weight(1.6f))
                }
            }
            sheet.weapons.forEachIndexed { index, weapon ->
                WeaponRow(weapon, compact, statDefinitions) { updated ->
                    update(
                        sheet.copy(
                            weapons = sheet.weapons.toMutableList().also { it[index] = updated },
                        ),
                    )
                }
            }
            val traitKinds = CharacterTraitSection.entries
                .flatMapTo(mutableSetOf()) { it.catalogKinds }
            sheet.abilityIds
                .distinct()
                .filter { abilityId ->
                    availableAbilities.firstOrNull { it.id == abilityId }?.category !in traitKinds
                }
                .forEach { abilityId ->
                    val ability = availableAbilities.firstOrNull { it.id == abilityId }
                    if (ability != null) {
                        CharacterAbilityRow(ability) {
                            update(sheet.copy(abilityIds = sheet.abilityIds.filterNot { it == abilityId }))
                        }
                    } else {
                        MissingAbilityRow(abilityId) {
                            update(sheet.copy(abilityIds = sheet.abilityIds.filterNot { it == abilityId }))
                        }
                    }
                }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GameButton(words.addWeapon, accent = Palette.Party, onClick = {
                    update(
                        sheet.copy(
                            weapons = sheet.weapons + WeaponEntry(
                                attackAbility = statDefinitions.firstOrNull()?.id ?: Ability.STRENGTH,
                            ),
                        ),
                    )
                })
                GameButton(words.addAbility, accent = Palette.Gold, onClick = {
                    abilityPickerOpen = true
                })
            }
        }

        SheetBox(words.classFeatures) {
            ProgressionEntries(
                ids = viewModel.characterTraitIds(CharacterTraitSection.FEATURE),
                catalog = availableAbilities,
                emptyNote = words.noFeaturesRecorded,
                onRemove = { id ->
                    viewModel.setCharacterTraitSelected(CharacterTraitSection.FEATURE, id, false)
                },
            )
            GameButton(
                words.manageFeatures,
                accent = Palette.Gold,
                onClick = { traitPickerSection = CharacterTraitSection.FEATURE },
            )
            Text(words.yourNotes, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
            SheetTextArea(sheet.classFeatures, minLines = 3) { update(sheet.copy(classFeatures = it)) }
        }

        AdaptiveFormRow(
            compact = compact,
            items = arrayOf(
                adaptiveFormItem { itemModifier ->
                    SheetBox(words.speciesTraits, itemModifier) {
                        SheetTextArea(sheet.speciesTraits) { update(sheet.copy(speciesTraits = it)) }
                    }
                },
                adaptiveFormItem { itemModifier ->
                    SheetBox(strings.sheet.feats, itemModifier) {
                        ProgressionEntries(
                            ids = viewModel.characterTraitIds(CharacterTraitSection.FEAT),
                            catalog = availableAbilities,
                            emptyNote = words.noFeatsRecorded,
                            onRemove = { id ->
                                viewModel.setCharacterTraitSelected(CharacterTraitSection.FEAT, id, false)
                            },
                        )
                        GameButton(
                            words.manageFeats,
                            accent = Palette.Gold,
                            onClick = { traitPickerSection = CharacterTraitSection.FEAT },
                        )
                        Text(words.yourNotes, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
                        SheetTextArea(sheet.feats) { update(sheet.copy(feats = it)) }
                    }
                },
            ),
        )
    }

    if (abilityPickerOpen) {
        val guidedIds = buildSet {
            addAll(sheet.progression.selectedFeatureIds)
            addAll(sheet.progression.featIds)
            addAll(sheet.progression.knownCantripIds)
            addAll(sheet.progression.preparedSpellIds)
        }
        val pickerAbilities = availableAbilities.filter { ability ->
            val isTrait = CharacterTraitSection.entries.any { ability.category in it.catalogKinds }
            if (isTrait) {
                false
            } else if (!sheet.progression.configured) {
                true
            } else {
                ability.category == app.d6d.rules.character.RuleElementKind.CUSTOM ||
                    ability.category == app.d6d.rules.character.RuleElementKind.COMMON_ACTION ||
                    ability.id in guidedIds
            }
        }
        AbilityPickerDialog(
            abilities = pickerAbilities,
            selectedIds = sheet.abilityIds.toSet(),
            onSelect = { ability ->
                update(sheet.copy(abilityIds = (sheet.abilityIds + ability.id).distinct()))
                abilityPickerOpen = false
            },
            onDismiss = { abilityPickerOpen = false },
        )
    }

    traitPickerSection?.let { section ->
        TraitPickerDialog(
            section = section,
            sheet = sheet,
            abilities = viewModel.characterTraitCandidates(section),
            selectedIds = viewModel.characterTraitIds(section).toSet(),
            isCompatible = viewModel::characterTraitIsCompatible,
            classLabel = { viewModel.displayedClassLabel(it, sheet) },
            onToggle = { ability, selected ->
                viewModel.setCharacterTraitSelected(section, ability.id, selected)
            },
            onDismiss = { traitPickerSection = null },
        )
    }
}

@Composable
private fun ColumnHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Palette.TextMuted,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier,
    )
}

@Composable
private fun WeaponRow(
    weapon: WeaponEntry,
    compact: Boolean,
    statDefinitions: List<CharacterStatDefinition>,
    onChange: (WeaponEntry) -> Unit,
) {
    val strings = strings
    val words = strings.sheet
    val language = strings.language
    if (compact) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Palette.Night, RoundedCornerShape(7.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            SheetField(strings.common.nameLabel, weapon.name) { onChange(weapon.copy(name = it)) }
            LegacyWeaponClassificationWarning(weapon)
            AdaptiveFormRow(
                compact = true,
                compactColumns = 2,
                items = arrayOf(
                    adaptiveFormItem { fieldModifier ->
                        SheetNumberField(words.attackBonusOrDc, weapon.attackBonus, fieldModifier) {
                            onChange(weapon.copy(attackBonus = it))
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        if (weapon.fixedDamage > 0) {
                            SheetNumberField(words.fixedDamage, weapon.fixedDamage, fieldModifier) {
                                onChange(weapon.copy(fixedDamage = it.coerceAtLeast(0)))
                            }
                        } else {
                            SheetNumberField(language.pick("Dadi", "Dice"), weapon.diceCount, fieldModifier) {
                                onChange(weapon.copy(diceCount = it.coerceAtLeast(1)))
                            }
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        if (weapon.fixedDamage > 0) {
                            SheetBox(language.pick("Tipo", "Type"), fieldModifier) {
                                Text(weapon.damageType.label(language), color = Palette.Text)
                            }
                        } else {
                            SheetNumberField(language.pick("Facce", "Sides"), weapon.diceSides, fieldModifier) {
                                onChange(weapon.copy(diceSides = it.coerceAtLeast(2)))
                            }
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetNumberField(words.modifier, weapon.damageModifier, fieldModifier) {
                            onChange(weapon.copy(damageModifier = it))
                        }
                    },
                ),
            )
            SheetField(language.pick("Note", "Notes"), weapon.note) { onChange(weapon.copy(note = it)) }
            SheetCheck(words.bonusAction, weapon.bonusAction) {
                onChange(weapon.copy(bonusAction = it))
            }
            if (!weapon.isArea) {
                WeaponAttackAbilitySelector(weapon, statDefinitions, onChange)
            }
            SheetCheck(words.spellOrCantrip, weapon.isSpellOrCantrip) {
                onChange(weapon.withSpellClassification(it, statDefinitions.firstOrNull()?.id ?: Ability.STRENGTH))
            }
            WeaponAreaSection(weapon, statDefinitions, onChange)
        }
        return
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        LegacyWeaponClassificationWarning(weapon)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SheetField("", weapon.name, Modifier.weight(2f)) { onChange(weapon.copy(name = it)) }
            SheetNumberField("", weapon.attackBonus, Modifier.weight(1f)) {
                onChange(weapon.copy(attackBonus = it))
            }
            Row(Modifier.weight(1.6f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                if (weapon.fixedDamage > 0) {
                    SheetNumberField(language.pick("fisso", "fixed"), weapon.fixedDamage, Modifier.weight(2f)) {
                        onChange(weapon.copy(fixedDamage = it.coerceAtLeast(0)))
                    }
                } else {
                    SheetNumberField("d", weapon.diceCount, Modifier.weight(1f)) {
                        onChange(weapon.copy(diceCount = it.coerceAtLeast(1)))
                    }
                    SheetNumberField(language.pick("facce", "sides"), weapon.diceSides, Modifier.weight(1f)) {
                        onChange(weapon.copy(diceSides = it.coerceAtLeast(2)))
                    }
                }
                SheetNumberField("mod", weapon.damageModifier, Modifier.weight(1f)) {
                    onChange(weapon.copy(damageModifier = it))
                }
            }
            SheetField("", weapon.note, Modifier.weight(1.6f)) { onChange(weapon.copy(note = it)) }
        }
        SheetCheck(words.bonusAction, weapon.bonusAction) {
            onChange(weapon.copy(bonusAction = it))
        }
        if (!weapon.isArea) {
            WeaponAttackAbilitySelector(weapon, statDefinitions, onChange)
        }
        SheetCheck(words.spellOrCantrip, weapon.isSpellOrCantrip) {
            onChange(weapon.withSpellClassification(it, statDefinitions.firstOrNull()?.id ?: Ability.STRENGTH))
        }
        WeaponAreaSection(weapon, statDefinitions, onChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeaponAttackAbilitySelector(
    weapon: WeaponEntry,
    statDefinitions: List<CharacterStatDefinition>,
    onChange: (WeaponEntry) -> Unit,
) {
    val strings = strings
    val words = strings.sheet
    Text(
        words.attackAbilityCaps,
        color = Palette.TextMuted,
        style = MaterialTheme.typography.labelSmall,
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        GameButton(
            label = if (weapon.isSpellOrCantrip) words.spellcastingAbility else words.toClassify,
            accent = if (weapon.attackAbility == null) Palette.Gold else Palette.TextMuted,
            selected = weapon.attackAbility == null,
            dense = true,
            onClick = {
                onChange(
                    weapon.copy(
                        attackAbility = null,
                        legacyClassificationRequired = !weapon.isSpellOrCantrip,
                    ),
                )
            },
        )
        statDefinitions.forEach { definition ->
            val ability = definition.id
            GameButton(
                label = definition.abbreviation,
                accent = if (weapon.attackAbility == ability) Palette.Gold else Palette.TextMuted,
                selected = weapon.attackAbility == ability,
                dense = true,
                onClick = {
                    onChange(
                        weapon.copy(
                            attackAbility = ability,
                            legacyClassificationRequired = false,
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun LegacyWeaponClassificationWarning(weapon: WeaponEntry) {
    val strings = strings
    val words = strings.sheet
    if (!weapon.legacyClassificationRequired || weapon.name.isBlank()) return
    Text(
        words.unclassifiedEntryHint,
        color = Palette.Bloodied,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.bodySmall,
    )
}

private fun WeaponEntry.withSpellClassification(
    isSpellOrCantrip: Boolean,
    defaultAbility: Ability,
): WeaponEntry =
    copy(
        spellOrCantrip = isSpellOrCantrip,
        attackAbility = if (!isSpellOrCantrip && attackAbility == null) {
            defaultAbility
        } else {
            attackAbility
        },
        legacyClassificationRequired = false,
    )

/**
 * Sezione «danno ad area» di una capacità: la trasforma in incantesimo con tiro
 * salvezza (raggio, gittata, caratteristica del TS, metà danni). Chiusa finché non
 * si spunta la casella, così le armi ordinarie restano compatte.
 */
@Composable
private fun WeaponAreaSection(
    weapon: WeaponEntry,
    statDefinitions: List<CharacterStatDefinition>,
    onChange: (WeaponEntry) -> Unit,
) {
    val strings = strings
    val words = strings.sheet
    SheetCheck(words.areaDamageWithSave, weapon.isArea) { on ->
        onChange(
            weapon.copy(
                areaRadiusFeet = if (on) weapon.areaRadiusFeet.takeIf { it > 0 } ?: 20 else 0,
                saveAbility = if (on) weapon.saveAbility ?: statDefinitions.firstOrNull()?.id else null,
                spellOrCantrip = weapon.spellOrCantrip || on,
                legacyClassificationRequired = if (on) false else weapon.legacyClassificationRequired,
            ),
        )
    }
    if (!weapon.isArea) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SheetMetreField(strings.abilities.radius, weapon.areaRadiusFeet, Modifier.weight(1f)) {
            onChange(weapon.copy(areaRadiusFeet = it.coerceAtLeast(1)))
        }
        SheetMetreField(currentLanguage.pick("Gittata", "Range"), weapon.rangeFeet, Modifier.weight(1f)) {
            onChange(weapon.copy(rangeFeet = it.coerceAtLeast(0)))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(words.savingThrowCaps, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            statDefinitions.forEach { definition ->
                val ability = definition.id
                GameButton(
                    label = definition.abbreviation,
                    accent = if (weapon.saveAbility == ability) Palette.Gold else Palette.TextMuted,
                    selected = weapon.saveAbility == ability,
                    dense = true,
                    onClick = { onChange(weapon.copy(saveAbility = ability)) },
                )
            }
        }
    }
    SheetCheck(words.halfDamageOnSave, weapon.halfOnSave) {
        onChange(weapon.copy(halfOnSave = it))
    }
}

@Composable
private fun CharacterAbilityRow(ability: CatalogAbility, onRemove: () -> Unit) {
    val strings = strings
    val words = strings.sheet
    val language = currentLanguage
    val shape = RoundedCornerShape(7.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(Palette.Night, shape)
            .border(1.dp, Palette.Gold.copy(alpha = 0.45f), shape)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = ability.name,
                    color = Palette.Text,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (ability.rulesText.isNotBlank()) {
                    Text(
                        text = ability.rulesText,
                        color = Palette.TextMuted,
                        maxLines = 2,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            GameButton(strings.common.remove, accent = Palette.Enemy, dense = true, onClick = onRemove)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Chip(ability.activationCost.label(language), Palette.Gold)
            if (ability.dealsDamage) Chip(ability.damageText(language), Palette.Enemy)
            if (ability.isArea) Chip(words.areaOf(distanceLabel(ability.areaRadiusFeet, language)), Palette.Crit)
            Chip(words.fromAbilityCatalog, Palette.Party)
        }
    }
}

@Composable
private fun MissingAbilityRow(abilityId: String, onRemove: () -> Unit) {
    val strings = strings
    val words = strings.sheet
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.Enemy.copy(alpha = 0.08f), RoundedCornerShape(7.dp))
            .border(1.dp, Palette.Enemy.copy(alpha = 0.45f), RoundedCornerShape(7.dp))
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = words.abilityMissingFromCatalog(abilityId),
            color = Palette.TextMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        GameButton(strings.common.remove, accent = Palette.Enemy, dense = true, onClick = onRemove)
    }
}

@Composable
private fun AbilityPickerDialog(
    abilities: List<CatalogAbility>,
    selectedIds: Set<String>,
    onSelect: (CatalogAbility) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = strings
    val words = strings.sheet
    val language = currentLanguage
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text(words.addAbilityTitle, color = Palette.Text) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                if (abilities.isEmpty()) {
                    Text(
                        words.emptyAbilityCatalog,
                        color = Palette.TextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    abilities.sortedBy { it.name.lowercase() }.forEach { ability ->
                        val alreadySelected = ability.id in selectedIds
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(Palette.Night, RoundedCornerShape(7.dp))
                                .border(1.dp, Palette.Line, RoundedCornerShape(7.dp))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    ability.name,
                                    color = Palette.Text,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    buildString {
                                        append(ability.activationCost.label(language))
                                        if (ability.dealsDamage) append(" · ${ability.damageText(language)}")
                                        if (ability.isArea) append(" · ${words.areaOf(distanceLabel(ability.areaRadiusFeet, language)).lowercase()}")
                                    },
                                    color = Palette.TextMuted,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            GameButton(
                                label = if (alreadySelected) {
                                    language.pick("Aggiunta", "Added")
                                } else {
                                    strings.common.add
                                },
                                accent = if (alreadySelected) Palette.TextFaint else Palette.Party,
                                dense = true,
                                selected = alreadySelected,
                                onClick = { if (!alreadySelected) onSelect(ability) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            GameButton(strings.common.close, accent = Palette.TextMuted, onClick = onDismiss)
        },
    )
}

/**
 * Selettore multi-voce per talenti e privilegi.
 *
 * Il filtro di compatibilità aiuta a trovare le scelte ordinarie, ma può essere
 * disattivato: la scheda resta utilizzabile anche per concessioni del GM,
 * conversioni da altri regolamenti e personaggi manuali.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TraitPickerDialog(
    section: CharacterTraitSection,
    sheet: CharacterSheet,
    abilities: List<CatalogAbility>,
    selectedIds: Set<String>,
    isCompatible: (CatalogAbility) -> Boolean,
    classLabel: (CharacterClassId) -> String,
    onToggle: (CatalogAbility, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = strings
    val words = strings.sheet
    val language = currentLanguage
    var query by remember(section, sheet.id) { mutableStateOf("") }
    var categoryFilter by remember(section, sheet.id) {
        mutableStateOf<RuleElementKind?>(null)
    }
    var onlyCompatible by remember(section, sheet.id) {
        mutableStateOf(sheet.progression.configured)
    }
    val categories = abilities.map { it.category }.distinct()
    val normalizedQuery = query.trim().lowercase()
    val filtered = abilities.filter { ability ->
        val matchesQuery =
            normalizedQuery.isBlank() ||
                normalizedQuery in ability.name.lowercase() ||
                normalizedQuery in ability.rulesText.lowercase() ||
                normalizedQuery in ability.prerequisite.lowercase()
        val matchesCategory = categoryFilter == null || ability.category == categoryFilter
        val matchesCompatibility = !onlyCompatible || isCompatible(ability)
        matchesQuery && matchesCategory && matchesCompatibility
    }
    val title = when (section) {
        CharacterTraitSection.FEATURE -> words.manageFeaturesTitle
        CharacterTraitSection.FEAT -> words.manageFeatsTitle
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = Palette.Text)
                Text(
                    words.chooseFromCompendium(filtered.size),
                    color = Palette.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 540.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SheetField(words.searchByNameRuleOrPrerequisite, query) { query = it }
                Text(language.pick("CATEGORIA", "CATEGORY"), color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    GameButton(
                        words.allFeminine,
                        accent = if (categoryFilter == null) Palette.Gold else Palette.TextMuted,
                        selected = categoryFilter == null,
                        dense = true,
                        onClick = { categoryFilter = null },
                    )
                    categories.forEach { category ->
                        GameButton(
                            category.label(language),
                            accent = if (categoryFilter == category) Palette.Gold else Palette.TextMuted,
                            selected = categoryFilter == category,
                            dense = true,
                            onClick = { categoryFilter = category },
                        )
                    }
                }
                if (sheet.progression.configured) {
                    SheetCheck(words.onlyCompatibleWithClassAndLevel, onlyCompatible) {
                        onlyCompatible = it
                    }
                }

                if (filtered.isEmpty()) {
                    Text(
                        words.noEntryMatchesFilters,
                        color = Palette.TextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (filtered.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        items(filtered, key = { it.id }) { ability ->
                            val selected = ability.id in selectedIds
                            val compatible = isCompatible(ability)
                            val shape = RoundedCornerShape(7.dp)
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (selected) Palette.Gold.copy(alpha = 0.10f) else Palette.Night,
                                        shape,
                                    )
                                    .border(
                                        1.dp,
                                        if (selected) Palette.Gold else Palette.Line,
                                        shape,
                                    )
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(
                                        Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp),
                                    ) {
                                        Text(
                                            ability.name,
                                            color = Palette.Text,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Chip(ability.category.label(language), Palette.Crit)
                                            if (!compatible) Chip(words.outOfRequirements, Palette.Enemy)
                                            if (ability.sourcePage > 0) {
                                                Chip(words.sourcePage(ability.sourcePage), Palette.TextMuted)
                                            }
                                        }
                                    }
                                    GameButton(
                                        label = if (selected) strings.common.remove else strings.common.add,
                                        accent = if (selected) Palette.Enemy else Palette.Party,
                                        dense = true,
                                        selected = selected,
                                        onClick = { onToggle(ability, !selected) },
                                    )
                                }
                                if (ability.classEligibility.isNotEmpty()) {
                                    Text(
                                        ability.classEligibility.joinToString(" · ") {
                                            words.classAndMinimumLevel(classLabel(it.classId), it.minimumLevel)
                                        },
                                        color = Palette.TextMuted,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                                if (ability.prerequisite.isNotBlank()) {
                                    Text(
                                        words.prerequisite(ability.prerequisite),
                                        color = Palette.GoldBright,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                if (ability.rulesText.isNotBlank()) {
                                    Text(
                                        ability.rulesText,
                                        color = Palette.TextMuted,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            GameButton(strings.common.close, accent = Palette.TextMuted, onClick = onDismiss)
        },
    )
}

// --- incantesimi --------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpellcastingSection(
    viewModel: SheetViewModel,
    sheet: CharacterSheet,
    compact: Boolean,
    update: (CharacterSheet) -> Unit,
) {
    val strings = strings
    val words = strings.sheet
    val language = currentLanguage
    val casting = sheet.spellcasting

    SheetBox(strings.sheet.spells) {
        if (casting == null) {
            GameButton(words.castsSpells, accent = Palette.Party, onClick = {
                update(sheet.copy(spellcasting = Spellcasting()))
            })
            return@SheetBox
        }

        if (sheet.spellcastingBlockedByArmor) {
            Text(
                words.castingBlockedByArmor,
                color = Palette.Bloodied,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Palette.Bloodied.copy(alpha = 0.10f), RoundedCornerShape(7.dp))
                    .border(1.dp, Palette.Bloodied.copy(alpha = 0.50f), RoundedCornerShape(7.dp))
                    .padding(8.dp),
            )
        }

        AdaptiveFormRow(
            compact = compact,
            compactColumns = 2,
            items = arrayOf(
                adaptiveFormItem(1.4f) { itemModifier ->
                    Column(itemModifier) {
                        Text(
                            words.spellcastingAbility,
                            color = Palette.TextMuted,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        if (sheet.progression.configured && casting.abilitiesByClass.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                casting.abilitiesByClass.forEach { (classId, ability) ->
                                    Chip(
                                        words.spellcastingAbilityOf(
                                            viewModel.displayedClassLabel(classId, sheet),
                                            ability.abbreviationIn(language),
                                        ),
                                        Palette.Party,
                                    )
                                }
                            }
                        } else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                viewModel.statDefinitionsFor(sheet).forEach { definition ->
                                    val ability = definition.id
                                    SheetCheck(definition.abbreviation, casting.ability == ability) {
                                        if (it) update(sheet.copy(spellcasting = casting.copy(ability = ability)))
                                    }
                                }
                            }
                        }
                    }
                },
                adaptiveFormItem { itemModifier ->
                    DerivedValue(words.modifier, signed(sheet.modifier(casting.ability)), itemModifier)
                },
                adaptiveFormItem { itemModifier ->
                    DerivedValue(words.saveDc, sheet.spellSaveDc?.toString() ?: "—", itemModifier)
                },
                adaptiveFormItem { itemModifier ->
                    DerivedValue(
                        words.attackBonus,
                        sheet.spellAttackBonus?.let { signed(it) } ?: "—",
                        itemModifier,
                    )
                },
            ),
        )

        Text(words.spellSlots, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            casting.slots.forEach { slot ->
                SlotBlock(slot, editableTotal = !sheet.progression.configured) { updated ->
                    update(
                        sheet.copy(
                            spellcasting = casting.copy(
                                slots = casting.slots.map { if (it.level == updated.level) updated else it },
                            ),
                        ),
                    )
                }
            }
            casting.pactSlots?.let { pact ->
                PactSlotBlock(pact) { updated ->
                    update(sheet.copy(spellcasting = casting.copy(pactSlots = updated)))
                }
            }
        }

        if (casting.spells.isNotEmpty()) {
            Text(
                words.selectedCantripsAndSpells,
                color = Palette.TextMuted,
                style = MaterialTheme.typography.labelSmall,
            )
            casting.spells
                .sortedWith(compareBy({ it.level }, { it.name.lowercase() }))
                .groupBy { it.level }
                .forEach { (level, spells) ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            if (level == 0) words.cantripsHeading else words.levelHeading(level),
                            color = Palette.Gold,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            spells.forEach { spell ->
                                Chip(
                                    buildString {
                                        append(spell.name)
                                        if (spell.concentration) append(words.concentrationInitial)
                                        if (spell.ritual) append(words.ritualInitial)
                                    },
                                    if (level == 0) Palette.Party else Palette.Temporary,
                                )
                            }
                        }
                    }
                }
        }
    }
}

@Composable
private fun SlotBlock(
    slot: SpellSlot,
    editableTotal: Boolean,
    onChange: (SpellSlot) -> Unit,
) {
    val strings = strings
    val words = strings.sheet
    Column(
        Modifier
            .width(112.dp)
            .background(Palette.Night, RoundedCornerShape(6.dp))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            words.slotLevel(slot.level),
            color = Palette.Gold,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall,
        )
        if (editableTotal) {
            SheetNumberField(currentLanguage.pick("Totali", "Total"), slot.total) {
                onChange(slot.copy(total = it.coerceIn(0, 9)))
            }
        } else {
            Text(
                words.slotTotal(slot.total),
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        // Le caselle mostrano gli slot spesi sul totale disponibile.
        PipRow(slot.total.coerceAtMost(9), slot.spent, color = Palette.Temporary) {
            onChange(slot.copy(spent = it.coerceIn(0, slot.total)))
        }
    }
}

@Composable
private fun PactSlotBlock(slot: SpellSlot, onChange: (SpellSlot) -> Unit) {
    val strings = strings
    val words = strings.sheet
    Column(
        Modifier
            .width(150.dp)
            .background(Palette.Night, RoundedCornerShape(6.dp))
            .border(1.dp, Palette.Gold.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            words.pactSlotLevel(slot.level),
            color = Palette.Gold,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall,
        )
        Text(words.shortOrLongRest, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
        PipRow(slot.total, slot.spent, color = Palette.Gold) {
            onChange(slot.copy(spent = it.coerceIn(0, slot.total)))
        }
    }
}

/**
 * Privilegi e talenti che il personaggio ha davvero, col testo del documento.
 *
 * Non c'e' niente da ricopiare a mano: la progressione guidata sa quali sono, e
 * il Compendio sa cosa dicono. Le padronanze d'arme finiscono nello stesso
 * elenco della progressione ma non sono privilegi: restano a parte, come
 * targhette, per non spezzare la lettura.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgressionEntries(
    ids: List<String>,
    catalog: List<CatalogAbility>,
    emptyNote: String,
    onRemove: (String) -> Unit,
) {
    val strings = strings
    val words = strings.sheet
    val byId = catalog.associateBy { it.id }
    val entries = ids.mapNotNull { byId[it] }
    // Le padronanze d'arme sono armi, non voci di catalogo: si riconoscono
    // dall'identificatore. Tutto cio' che non e' ne' l'una ne' l'altra cosa e'
    // un riferimento che il pacchetto non conosce piu', e non si inventa.
    val masteries = ids
        .filter { it !in byId && it.contains(":weapon:") }
        .map { it.substringAfterLast(':').replace('-', ' ') }

    if (entries.isEmpty() && masteries.isEmpty()) {
        Text(emptyNote, color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
    }
    entries.forEach { entry ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = entry.name,
                        color = Palette.GoldBright,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (entry.sourcePage > 0) {
                        Text(
                            text = words.sourcePage(entry.sourcePage),
                            color = Palette.TextFaint,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                if (entry.rulesText.isNotBlank()) {
                    Text(
                        text = entry.rulesText,
                        color = Palette.Text,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            GameButton(
                strings.common.remove,
                accent = Palette.Enemy,
                dense = true,
                onClick = { onRemove(entry.id) },
            )
        }
    }
    if (masteries.isNotEmpty()) {
        Text(words.weaponMasteries, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            masteries.forEach { Chip(it.replaceFirstChar { first -> first.uppercase() }, Palette.Party) }
        }
    }
}

// --- sezioni generate dal regolamento ------------------------------------------------

@Composable
private fun ModularSheetSections(
    viewModel: SheetViewModel,
    sheet: CharacterSheet,
    compact: Boolean,
) {
    val language = currentLanguage
    sheet.modularSheet.sections.forEach { section ->
        SheetBox(section.title, Modifier.fillMaxWidth()) {
            if (section.description.isNotBlank()) {
                Text(
                    section.description,
                    color = Palette.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = if (compact) 1 else section.columns.coerceIn(1, 12),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                section.fields.forEach { field ->
                    ModularSheetFieldEditor(
                        field = field,
                        value = sheet.modularSheet.values[field.id]
                            ?: ModularSheetValue(field.kind),
                        trueLabel = language.pick("Sì", "Yes"),
                        falseLabel = language.pick("No", "No"),
                        currentLabel = language.pick("Attuale", "Current"),
                        maximumLabel = language.pick("Massimo", "Maximum"),
                        modifier = Modifier.weight(1f),
                        onCommit = { current, maximum ->
                            viewModel.updateModularField(field.id, current, maximum)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ModularSheetFieldEditor(
    field: ModularSheetField,
    value: ModularSheetValue,
    trueLabel: String,
    falseLabel: String,
    currentLabel: String,
    maximumLabel: String,
    modifier: Modifier = Modifier,
    onCommit: (String, String) -> Boolean,
) {
    val unit = field.canonicalUnit.takeIf(String::isNotBlank)?.let { " ($it)" }.orEmpty()
    val label = field.label + unit
    var currentDraft by remember(field.id, value.current) { mutableStateOf(value.current) }
    var maximumDraft by remember(field.id, value.maximum) { mutableStateOf(value.maximum) }

    fun normalized(text: String): String = text.trim().replace(',', '.')
    fun commitNumberPair() {
        val current = normalized(currentDraft)
        val maximum = normalized(maximumDraft)
        val valid = current.toBigDecimalOrNull() != null &&
            (field.kind != ModularSheetFieldKind.RESOURCE || maximum.toBigDecimalOrNull() != null)
        if (valid) {
            onCommit(current, maximum)
        } else {
            currentDraft = value.current
            maximumDraft = value.maximum
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        when (field.kind) {
            ModularSheetFieldKind.RULE_TEXT -> Text(
                value.current.ifBlank { field.description },
                color = Palette.Text,
                style = MaterialTheme.typography.bodyMedium,
            )
            ModularSheetFieldKind.BOOLEAN -> GameButton(
                label = "$label: ${if (value.current.equals("true", true)) trueLabel else falseLabel}",
                selected = value.current.equals("true", true),
                enabled = field.mutable,
                dense = true,
                onClick = { onCommit((!value.current.equals("true", true)).toString(), "") },
            )
            ModularSheetFieldKind.RESOURCE -> {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    SheetField(
                        label = "$label · $currentLabel",
                        value = currentDraft,
                        modifier = Modifier.weight(1f),
                        decimal = true,
                        onFocusLost = ::commitNumberPair,
                        onChange = { currentDraft = it },
                    )
                    SheetField(
                        label = maximumLabel,
                        value = maximumDraft,
                        modifier = Modifier.weight(1f),
                        decimal = true,
                        onFocusLost = ::commitNumberPair,
                        onChange = { maximumDraft = it },
                    )
                }
            }
            ModularSheetFieldKind.NUMBER,
            ModularSheetFieldKind.CONDITION,
            -> if (field.mutable) {
                SheetField(
                    label = label,
                    value = currentDraft,
                    decimal = field.kind == ModularSheetFieldKind.NUMBER,
                    numeric = field.kind == ModularSheetFieldKind.CONDITION,
                    onFocusLost = ::commitNumberPair,
                    onChange = { currentDraft = it },
                )
            } else {
                DerivedValue(label, value.current, Modifier.fillMaxWidth())
            }
            ModularSheetFieldKind.TEXT,
            ModularSheetFieldKind.REFERENCE,
            -> if (field.mutable) {
                SheetField(
                    label = label,
                    value = currentDraft,
                    onFocusLost = { onCommit(currentDraft, "") },
                    onChange = { currentDraft = it },
                )
            } else {
                DerivedValue(label, value.current, Modifier.fillMaxWidth())
            }
        }
        if (field.description.isNotBlank() && field.kind != ModularSheetFieldKind.RULE_TEXT) {
            Text(field.description, color = Palette.TextFaint, style = MaterialTheme.typography.labelSmall)
        }
    }
}

// --- note narrative -----------------------------------------------------------------

@Composable
private fun CharacterNotesSection(
    sheet: CharacterSheet,
    compact: Boolean,
    update: (CharacterSheet) -> Unit,
) {
    val strings = strings
    val words = strings.sheet
    val language = strings.language
    AdaptiveFormRow(
        compact = compact,
        items = arrayOf(
            adaptiveFormItem { itemModifier ->
                SheetBox(strings.sheet.appearance, itemModifier) {
                    SheetTextArea(sheet.appearance, minLines = 3) { update(sheet.copy(appearance = it)) }
                }
            },
            adaptiveFormItem(1.4f) { itemModifier ->
                SheetBox(words.backgroundAndTraits, itemModifier) {
                    SheetTextArea(sheet.backstory, minLines = 3) { update(sheet.copy(backstory = it)) }
                    SheetField(strings.sheet.alignment, sheet.alignment) { update(sheet.copy(alignment = it)) }
                }
            },
            adaptiveFormItem { itemModifier ->
                Column(itemModifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    SheetBox(language.pick("Lingue", "Languages")) {
                        SheetTextArea(sheet.languages, minLines = 2) { update(sheet.copy(languages = it)) }
                    }
                    SheetBox(language.pick("Denari", "Coins")) {
                        AdaptiveFormRow(
                            compact = compact,
                            compactColumns = 2,
                            items = arrayOf(
                                adaptiveFormItem { fieldModifier ->
                                    SheetNumberField(language.pick("MR", "CP"), sheet.money.copper, fieldModifier) {
                                        update(sheet.copy(money = sheet.money.copy(copper = it)))
                                    }
                                },
                                adaptiveFormItem { fieldModifier ->
                                    SheetNumberField(language.pick("MA", "SP"), sheet.money.silver, fieldModifier) {
                                        update(sheet.copy(money = sheet.money.copy(silver = it)))
                                    }
                                },
                                adaptiveFormItem { fieldModifier ->
                                    SheetNumberField(language.pick("ME", "EP"), sheet.money.electrum, fieldModifier) {
                                        update(sheet.copy(money = sheet.money.copy(electrum = it)))
                                    }
                                },
                                adaptiveFormItem { fieldModifier ->
                                    SheetNumberField(language.pick("MO", "GP"), sheet.money.gold, fieldModifier) {
                                        update(sheet.copy(money = sheet.money.copy(gold = it)))
                                    }
                                },
                                adaptiveFormItem { fieldModifier ->
                                    SheetNumberField(language.pick("MP", "PP"), sheet.money.platinum, fieldModifier) {
                                        update(sheet.copy(money = sheet.money.copy(platinum = it)))
                                    }
                                },
                            ),
                        )
                    }
                }
            },
            adaptiveFormItem(1.2f) { itemModifier ->
                SheetBox(strings.sheet.equipment, itemModifier) {
                    SheetTextArea(sheet.equipment, minLines = 4) { update(sheet.copy(equipment = it)) }
                    Text(
                        language.pick("Inventario strutturato", "Structured inventory"),
                        color = Palette.TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    if (sheet.inventory.isEmpty()) {
                        Text(
                            language.pick("Nessun oggetto raccolto.", "No collected items."),
                            color = Palette.TextFaint,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        sheet.inventory.forEach { item ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (item.quantity > 1) "${item.quantity}× ${item.name}" else item.name,
                                        color = Palette.Text,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        item.category.label(strings),
                                        color = Palette.TextMuted,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                                GameButton(
                                    strings.common.remove,
                                    accent = Palette.Enemy,
                                    dense = true,
                                    onClick = {
                                        update(sheet.copy(inventory = sheet.inventory.filterNot { it.id == item.id }))
                                    },
                                )
                            }
                        }
                    }
                    Text(
                        words.magicItemAttunement,
                        color = Palette.TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    sheet.attunements.forEachIndexed { index, item ->
                        SheetField("", item) { value ->
                            update(
                                sheet.copy(
                                    attunements = sheet.attunements.toMutableList().also { it[index] = value },
                                ),
                            )
                        }
                    }
                }
            },
        ),
    )
}
