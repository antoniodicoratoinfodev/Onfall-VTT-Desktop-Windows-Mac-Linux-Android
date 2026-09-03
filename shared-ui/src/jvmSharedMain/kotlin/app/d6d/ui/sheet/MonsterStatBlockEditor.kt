package app.d6d.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import app.d6d.domain.combat.ConditionType
import app.d6d.domain.combat.DamageType
import app.d6d.sheet.Ability
import app.d6d.sheet.CreatureSize
import app.d6d.sheet.MonsterStatBlock
import app.d6d.sheet.NpcDisposition
import app.d6d.sheet.Proficiency
import app.d6d.sheet.StatBlockActorKind
import app.d6d.sheet.Skill
import app.d6d.sheet.StatBlockEntry
import app.d6d.sheet.WeaponEntry
import app.d6d.sheet.abilityModifier
import app.d6d.sheet.i18n.label as sheetLabel
import app.d6d.sheet.i18n.subtitle
import app.d6d.sheet.suggestedProficiencyBonus
import app.d6d.ui.battle.GameButton
import app.d6d.ui.images.PortraitPicker
import app.d6d.ui.images.PortraitRepository
import app.d6d.ui.runDiskIo
import app.d6d.ui.components.Chip
import app.d6d.ui.components.ClassIcon
import app.d6d.ui.components.DialogTitle
import app.d6d.rules.character.CharacterClassId
import app.d6d.i18n.label
import app.d6d.i18n.pick
import app.d6d.ui.i18n.currentLanguage
import app.d6d.ui.i18n.strings
import app.d6d.ui.theme.Palette
import app.d6d.ui.theme.OnfallTheme
import kotlinx.coroutines.launch

/**
 * Stat block del mostro nel formato 2024/2025.
 *
 * E' la versione ridotta della scheda del personaggio: niente background,
 * talenti, denari o Dadi Vita spendibili. In compenso ha cio' che un personaggio
 * non ha — Grado di Sfida, XP anche alternativi in tana, e le cinque sezioni
 * operative separate.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonsterStatBlockEditor(
    viewModel: SheetViewModel,
    portraits: PortraitRepository,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val words = strings.sheet
    val language = currentLanguage
    val scope = rememberCoroutineScope()
    val block = viewModel.monster
    val damageTypes = (viewModel.damageTypesFor() +
        block.resistances + block.vulnerabilities + block.damageImmunities).distinct()
    val conditionTypes = (viewModel.conditionTypesFor() + block.conditionImmunities).distinct()
    val update: (MonsterStatBlock) -> Unit = { viewModel.monster = it }
    var deleteId by remember(viewModel.selectedId) { mutableStateOf<String?>(null) }

    Column(modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
        // Anteprima come apparirebbe stampato, sopra i campi modificabili.
        Column(
            Modifier
                .fillMaxWidth()
                .background(Palette.Abyss, RoundedCornerShape(9.dp))
                .border(1.dp, Palette.Gold.copy(alpha = 0.4f), RoundedCornerShape(9.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                block.characterClassId?.let { ClassIcon(it, size = 44.dp) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = block.name.ifBlank { words.unnamedCreature },
                        color = Palette.GoldBright,
                        style = MaterialTheme.typography.displaySmall,
                    )
                    Text(block.subtitle(language), color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
                    if (block.actorKind == StatBlockActorKind.NPC) {
                        Text(
                            listOfNotNull(
                                language.pick("PNG", "NPC"),
                                block.npcDisposition.visibleLabel(language),
                                block.characterClassId?.let { classId ->
                                    val className = viewModel.availableCharacterClasses
                                        .firstOrNull { it.id == classId }
                                        ?.name
                                        ?: classId.label(language)
                                    "$className ${block.classLevel.coerceAtLeast(1)}"
                                },
                            ).joinToString(" · "),
                            color = Palette.Gold,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            StatLine(language.pick("CA", "AC"), "${block.armorClass}")
            StatLine(
                words.initiativeLabel,
                words.initiativeSummary(signed(block.initiativeModifier), block.initiativeScore),
            )
            StatLine(language.pick("PF", "HP"), block.hitPointsText)
            StatLine(language.pick("Velocita'", "Speed"), block.speeds.sheetLabel(language))
            StatLine(words.perceptionLabel, words.passive(block.passivePerception))
            StatLine(
                words.challengeRatingShort,
                words.challengeRatingSummary(
                    rating = block.challengeRating,
                    xp = block.baseXp,
                    lairXp = block.lairXp,
                    proficiency = signed(block.proficiencyBonus),
                ),
            )
        }

        SheetBox(language.pick("Ritratto", "Portrait")) {
            PortraitPicker(portraits, block.id, block.name)
        }

        SheetBox(strings.sheet.header) {
            AdaptiveFormRow(
                compact = compact,
                items = arrayOf(
                    adaptiveFormItem(2f) { fieldModifier ->
                        SheetField(strings.common.nameLabel, block.name, fieldModifier) { update(block.copy(name = it)) }
                    },
                    adaptiveFormItem(1.4f) { fieldModifier ->
                        SheetField(language.pick("Identificatore", "Identifier"), block.id, fieldModifier) {
                            update(block.copy(id = it))
                        }
                    },
                ),
            )
            Text(language.pick("Tipo di attore", "Actor type"), color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                StatBlockActorKind.entries.forEach { actorKind ->
                    val selected = block.actorKind == actorKind
                    SheetCheck(
                        if (actorKind == StatBlockActorKind.NPC) language.pick("PNG", "NPC") else words.monsters,
                        selected,
                    ) { checked ->
                        if (checked) {
                            update(
                                block.copy(
                                    actorKind = actorKind,
                                    npcDisposition = if (actorKind == StatBlockActorKind.NPC) {
                                        block.npcDisposition
                                    } else {
                                        NpcDisposition.HOSTILE
                                    },
                                    characterClassId = block.characterClassId.takeIf {
                                        actorKind == StatBlockActorKind.NPC
                                    },
                                ),
                            )
                        }
                    }
                }
            }
            if (block.actorKind == StatBlockActorKind.NPC) {
                Text(language.pick("Affiliazione abituale", "Usual affiliation"), color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    NpcDisposition.entries.forEach { disposition ->
                        SheetCheck(disposition.visibleLabel(language), block.npcDisposition == disposition) {
                            if (it) update(block.copy(npcDisposition = disposition))
                        }
                    }
                }
                Text(language.pick("Classe", "Class"), color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    SheetCheck(
                        language.pick("Senza classe", "No class"),
                        block.characterClassId == null,
                    ) { if (it) update(block.copy(characterClassId = null)) }
                    viewModel.availableCharacterClasses.forEach { definition ->
                        SheetCheck(definition.name, block.characterClassId == definition.id) {
                            if (it) update(block.copy(characterClassId = definition.id))
                        }
                    }
                }
                block.characterClassId?.let {
                    SheetNumberField(
                        language.pick("Livello di classe", "Class level"),
                        block.classLevel.coerceAtLeast(1),
                        Modifier.width(150.dp),
                    ) { update(block.copy(classLevel = it.coerceAtLeast(1))) }
                }
            }
            AdaptiveFormRow(
                compact = compact,
                compactColumns = if (compact) 2 else 3,
                items = arrayOf(
                    adaptiveFormItem { fieldModifier ->
                        SheetField(language.pick("Tipo", "Type"), block.type, fieldModifier) {
                            update(block.copy(type = it))
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetField("Tag", block.tags, fieldModifier) { update(block.copy(tags = it)) }
                    },
                    adaptiveFormItem(1.2f) { fieldModifier ->
                        SheetField(words.alignment, block.alignment, fieldModifier) {
                            update(block.copy(alignment = it))
                        }
                    },
                ),
            )
            Text(language.pick("Taglia", "Size"), color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                CreatureSize.entries.forEach { size ->
                    SheetCheck(size.sheetLabel(language), block.size == size) {
                        if (it) update(block.copy(size = size))
                    }
                }
            }
        }

        SheetBox(words.defenceInitiativeHitPoints) {
            AdaptiveFormRow(
                compact = compact,
                compactColumns = 2,
                items = arrayOf(
                    adaptiveFormItem { fieldModifier ->
                        SheetNumberField(words.armorClass, block.armorClass, fieldModifier) {
                            update(block.copy(armorClass = it))
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetNumberField(words.initiativeModifier, block.initiativeModifier, fieldModifier) {
                            // Il punteggio statico segue il modificatore, ma resta un campo
                            // distinto e modificabile a parte.
                            update(block.copy(initiativeModifier = it, initiativeScore = 10 + it))
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetNumberField(words.staticScore, block.initiativeScore, fieldModifier) {
                            update(block.copy(initiativeScore = it))
                        }
                    },
                ),
            )
            AdaptiveFormRow(
                compact = compact,
                compactColumns = 2,
                items = arrayOf(
                    adaptiveFormItem(1.2f) { fieldModifier ->
                        SheetNumberField(words.averageHitPoints, block.averageHitPoints, fieldModifier) {
                            update(block.copy(averageHitPoints = it.coerceAtLeast(1)))
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetNumberField(words.diceCount, block.hitDiceCount, fieldModifier) {
                            update(block.copy(hitDiceCount = it.coerceAtLeast(1)))
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetNumberField(language.pick("Facce", "Sides"), block.hitDiceSides, fieldModifier) {
                            update(block.copy(hitDiceSides = it.coerceAtLeast(2)))
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetNumberField(words.modifier, block.hitDiceModifier, fieldModifier) {
                            update(block.copy(hitDiceModifier = it))
                        }
                    },
                ),
            )
        }

        SheetBox(language.pick("Velocita'", "Speed")) {
            AdaptiveFormRow(
                compact = compact,
                compactColumns = 2,
                items = arrayOf(
                    adaptiveFormItem { fieldModifier ->
                        SheetMetreField(words.onFoot, block.speeds.walk, fieldModifier) {
                            update(block.copy(speeds = block.speeds.copy(walk = it.coerceAtLeast(0))))
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetMetreField(language.pick("Volo", "Fly"), block.speeds.fly, fieldModifier) {
                            update(block.copy(speeds = block.speeds.copy(fly = it.coerceAtLeast(0))))
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetMetreField(language.pick("Nuoto", "Swim"), block.speeds.swim, fieldModifier) {
                            update(block.copy(speeds = block.speeds.copy(swim = it.coerceAtLeast(0))))
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetMetreField(language.pick("Scalata", "Climb"), block.speeds.climb, fieldModifier) {
                            update(block.copy(speeds = block.speeds.copy(climb = it.coerceAtLeast(0))))
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetMetreField(language.pick("Scavo", "Burrow"), block.speeds.burrow, fieldModifier) {
                            update(block.copy(speeds = block.speeds.copy(burrow = it.coerceAtLeast(0))))
                        }
                    },
                ),
            )
            SheetCheck(words.canHover, block.speeds.hover) {
                update(block.copy(speeds = block.speeds.copy(hover = it)))
            }
        }

        SheetBox(language.pick("Caratteristiche", "Abilities")) {
            AdaptiveFormRow(
                compact = compact,
                compactColumns = 2,
                items = Ability.entries.map { ability ->
                    adaptiveFormItem { itemModifier ->
                        Column(
                            itemModifier,
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                ability.abbreviation,
                                color = Palette.Gold,
                                style = MaterialTheme.typography.labelSmall,
                            )
                            SheetNumberField("", block.score(ability)) { score ->
                                update(
                                    block.copy(
                                        abilityScores = block.abilityScores + (ability to score.coerceIn(1, 30)),
                                    ),
                                )
                            }
                            Text(
                                signed(abilityModifier(block.score(ability))),
                                color = Palette.Text,
                                style = OnfallTheme.typography.numberSmall,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ProficiencyDot(
                                    filled = block.saveProficiencies[ability] == Proficiency.PROFICIENT,
                                    expertise = false,
                                    onClick = {
                                        val next = if (
                                            block.saveProficiencies[ability] == Proficiency.PROFICIENT
                                        ) {
                                            Proficiency.NONE
                                        } else {
                                            Proficiency.PROFICIENT
                                        }
                                        update(
                                            block.copy(
                                                saveProficiencies = block.saveProficiencies + (ability to next),
                                            ),
                                        )
                                    },
                                )
                                Text(
                                    signed(block.saveBonus(ability)),
                                    color = Palette.TextMuted,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }.toTypedArray(),
            )
        }

        SheetBox(strings.sheet.abilitiesLabel) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Skill.entries.forEach { skill ->
                    val level = block.skillProficiencies[skill] ?: Proficiency.NONE
                    val color = when (level) {
                        Proficiency.NONE -> Palette.TextFaint
                        Proficiency.PROFICIENT -> Palette.Gold
                        Proficiency.EXPERTISE -> Palette.GoldBright
                    }
                    Box(
                        Modifier.clickable {
                            val next = when (level) {
                                Proficiency.NONE -> Proficiency.PROFICIENT
                                Proficiency.PROFICIENT -> Proficiency.EXPERTISE
                                Proficiency.EXPERTISE -> Proficiency.NONE
                            }
                            update(block.copy(skillProficiencies = block.skillProficiencies + (skill to next)))
                        },
                    ) {
                        val suffix = if (level != Proficiency.NONE) " ${signed(block.skillBonus(skill))}" else ""
                        Chip("${skill.label(language)}$suffix", color)
                    }
                }
            }
        }

        SheetBox(words.typedDefences) {
            DamageToggleRow(language.pick("Resistenze", "Resistances"), block.resistances, damageTypes) {
                update(block.copy(resistances = it))
            }
            DamageToggleRow(words.vulnerabilities, block.vulnerabilities, damageTypes) {
                update(block.copy(vulnerabilities = it))
            }
            DamageToggleRow(words.damageImmunities, block.damageImmunities, damageTypes) {
                update(block.copy(damageImmunities = it))
            }
            val language = currentLanguage
            Text(
                words.conditionImmunities,
                color = Palette.TextMuted,
                style = MaterialTheme.typography.labelSmall,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                conditionTypes.forEach { condition ->
                    val on = condition in block.conditionImmunities
                    Box(
                        Modifier.clickable {
                            update(
                                block.copy(
                                    conditionImmunities = if (on) {
                                        block.conditionImmunities - condition
                                    } else {
                                        block.conditionImmunities + condition
                                    },
                                ),
                            )
                        },
                    ) {
                        Chip(condition.label(language), if (on) Palette.Bloodied else Palette.TextFaint)
                    }
                }
            }
        }

        SheetBox(words.sensesLanguagesGear) {
            AdaptiveFormRow(
                compact = compact,
                items = arrayOf(
                    adaptiveFormItem(1.4f) { fieldModifier ->
                        SheetField(language.pick("Sensi", "Senses"), block.senses, fieldModifier) {
                            update(block.copy(senses = it))
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetField(language.pick("Lingue", "Languages"), block.languages, fieldModifier) {
                            update(block.copy(languages = it))
                        }
                    },
                ),
            )
            // Gear elenca solo gli oggetti recuperabili: non e' tutto cio' che indossa.
            SheetField(words.gear, block.gear) { update(block.copy(gear = it)) }
        }

        SheetBox(language.pick("Sfida", "Challenge")) {
            AdaptiveFormRow(
                compact = compact,
                compactColumns = 2,
                items = arrayOf(
                    adaptiveFormItem { fieldModifier ->
                        SheetField(words.challengeRating, block.challengeRating, fieldModifier) { rating ->
                            val suggested = rating.trim().toDoubleOrNull()
                                ?.let { suggestedProficiencyBonus(it) } ?: block.proficiencyBonus
                            update(block.copy(challengeRating = rating, proficiencyBonus = suggested))
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetNumberField(words.baseXp, block.baseXp.toInt(), fieldModifier) {
                            update(block.copy(baseXp = it.toLong().coerceAtLeast(0)))
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetNumberField(words.lairXp, (block.lairXp ?: 0L).toInt(), fieldModifier) {
                            update(block.copy(lairXp = if (it <= 0) null else it.toLong()))
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetNumberField(words.proficiencyBonus, block.proficiencyBonus, fieldModifier) {
                            update(block.copy(proficiencyBonus = it))
                        }
                    },
                ),
            )
            AdaptiveFormRow(
                compact = compact,
                items = arrayOf(
                    adaptiveFormItem { fieldModifier ->
                        SheetField(language.pick("Habitat", "Habitat"), block.habitat, fieldModifier) {
                            update(block.copy(habitat = it))
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetField(words.treasureTheme, block.treasureTheme, fieldModifier) {
                            update(block.copy(treasureTheme = it))
                        }
                    },
                ),
            )
        }

        EntrySection(language.pick("Tratti", "Traits"), block.traits, compact) {
            update(block.copy(traits = it))
        }
        EntrySection(strings.sheet.actions, block.actions, compact) { update(block.copy(actions = it)) }
        EntrySection(words.bonusActions, block.bonusActions, compact) { update(block.copy(bonusActions = it)) }
        EntrySection(strings.sheet.reactions, block.reactions, compact) { update(block.copy(reactions = it)) }
        EntrySection(words.legendaryActions, block.legendaryActions, compact) {
            update(block.copy(legendaryActions = it))
        }

        }

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(Palette.Surface)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            GameButton(words.saveStatBlock, accent = Palette.Heal, onClick = {
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
            title = { DialogTitle(words.deleteStatBlockTitle) },
            text = {
                Text(
                    words.deleteStatBlockBody(block.name.ifBlank { words.unnamedCreature }),
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
}

private fun NpcDisposition.visibleLabel(language: app.d6d.i18n.AppLanguage): String = when (this) {
    NpcDisposition.FRIENDLY -> language.pick("Amichevole", "Friendly")
    NpcDisposition.HOSTILE -> language.pick("Ostile", "Hostile")
    NpcDisposition.NEUTRAL -> language.pick("Neutrale", "Neutral")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DamageToggleRow(
    label: String,
    selected: Set<DamageType>,
    available: List<DamageType>,
    onChange: (Set<DamageType>) -> Unit,
) {
    val language = currentLanguage
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            available.forEach { type ->
                val on = type in selected
                Box(Modifier.clickable { onChange(if (on) selected - type else selected + type) }) {
                    Chip(type.label(language), if (on) Palette.Bloodied else Palette.TextFaint)
                }
            }
        }
    }
}

/**
 * Sezione operativa dello stat block.
 *
 * Una voce con dati d'attacco compilati diventa eseguibile dal motore; senza
 * quei dati resta testo che il DM applica a mano, e viene marcata come tale.
 */
@Composable
private fun EntrySection(
    title: String,
    entries: List<StatBlockEntry>,
    compact: Boolean,
    onChange: (List<StatBlockEntry>) -> Unit,
) {
    val words = strings.sheet
    val language = currentLanguage
    SheetBox(words.sectionCount(title, entries.size)) {
        entries.forEachIndexed { index, entry ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Palette.Night, RoundedCornerShape(7.dp))
                    .padding(9.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (compact) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        SheetField(strings.common.nameLabel, entry.name) {
                            onChange(entries.toMutableList().also { l -> l[index] = entry.copy(name = it) })
                        }
                        Chip(
                            if (entry.automated) {
                                language.pick("Automatica", "Automatic")
                            } else {
                                language.pick("Manuale", "Manual")
                            },
                            if (entry.automated) Palette.Heal else Palette.Bloodied,
                        )
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SheetField(strings.common.nameLabel, entry.name, Modifier.weight(1f)) {
                            onChange(entries.toMutableList().also { l -> l[index] = entry.copy(name = it) })
                        }
                        Chip(
                            if (entry.automated) {
                                language.pick("Automatica", "Automatic")
                            } else {
                                language.pick("Manuale", "Manual")
                            },
                            if (entry.automated) Palette.Heal else Palette.Bloodied,
                        )
                    }
                }
                SheetTextArea(entry.text, minLines = 2) {
                    onChange(entries.toMutableList().also { l -> l[index] = entry.copy(text = it) })
                }

                val attack = entry.attack
                if (attack == null) {
                    GameButton(words.makeAttackExecutable, accent = Palette.Party, onClick = {
                        onChange(
                            entries.toMutableList().also { l ->
                                l[index] = entry.copy(attack = WeaponEntry(name = entry.name))
                            },
                        )
                    })
                } else {
                    AdaptiveFormRow(
                        compact = compact,
                        compactColumns = 2,
                        items = arrayOf(
                            adaptiveFormItem { fieldModifier ->
                                SheetNumberField(words.attackBonusShort, attack.attackBonus, fieldModifier) {
                                    onChange(
                                        entries.toMutableList().also { l ->
                                            l[index] = entry.copy(attack = attack.copy(attackBonus = it))
                                        },
                                    )
                                }
                            },
                            adaptiveFormItem { fieldModifier ->
                                SheetMetreField(language.pick("Portata", "Reach"), attack.rangeFeet, fieldModifier) {
                                    onChange(
                                        entries.toMutableList().also { l ->
                                            l[index] = entry.copy(attack = attack.copy(rangeFeet = it))
                                        },
                                    )
                                }
                            },
                            adaptiveFormItem { fieldModifier ->
                                if (attack.fixedDamage > 0) {
                                    SheetNumberField(words.fixedDamage, attack.fixedDamage, fieldModifier) {
                                        onChange(
                                            entries.toMutableList().also { l ->
                                                l[index] = entry.copy(
                                                    attack = attack.copy(fixedDamage = it.coerceAtLeast(0)),
                                                )
                                            },
                                        )
                                    }
                                } else {
                                    SheetNumberField(language.pick("Dadi", "Dice"), attack.diceCount, fieldModifier) {
                                        onChange(
                                            entries.toMutableList().also { l ->
                                                l[index] = entry.copy(
                                                    attack = attack.copy(diceCount = it.coerceAtLeast(1)),
                                                )
                                            },
                                        )
                                    }
                                }
                            },
                            adaptiveFormItem { fieldModifier ->
                                if (attack.fixedDamage > 0) {
                                    SheetBox(language.pick("Tipo", "Type"), fieldModifier) {
                                        Text(attack.damageType.label(language), color = Palette.Text)
                                    }
                                } else {
                                    SheetNumberField(language.pick("Facce", "Sides"), attack.diceSides, fieldModifier) {
                                        onChange(
                                            entries.toMutableList().also { l ->
                                                l[index] = entry.copy(
                                                    attack = attack.copy(diceSides = it.coerceAtLeast(2)),
                                                )
                                            },
                                        )
                                    }
                                }
                            },
                            adaptiveFormItem { fieldModifier ->
                                SheetNumberField("Mod.", attack.damageModifier, fieldModifier) {
                                    onChange(
                                        entries.toMutableList().also { l ->
                                            l[index] = entry.copy(attack = attack.copy(damageModifier = it))
                                        },
                                    )
                                }
                            },
                        ),
                    )
                }

                GameButton(strings.common.remove, accent = Palette.Enemy, onClick = {
                    onChange(entries.filterIndexed { i, _ -> i != index })
                })
            }
        }
        GameButton(words.addEntry, accent = Palette.Party, onClick = {
            onChange(entries + StatBlockEntry())
        })
    }
}
