package app.d6d.ui.sheet

import androidx.compose.foundation.background
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.d6d.sheet.Ability
import app.d6d.sheet.CharacterSheet
import app.d6d.sheet.CreatureSize
import app.d6d.sheet.Proficiency
import app.d6d.sheet.Skill
import app.d6d.sheet.SpellSlot
import app.d6d.sheet.Spellcasting
import app.d6d.sheet.WeaponEntry
import app.d6d.sheet.abilityModifier
import app.d6d.ui.battle.GameButton
import app.d6d.ui.images.PortraitPicker
import app.d6d.ui.images.PortraitRepository
import app.d6d.ui.theme.Palette

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
    val sheet = viewModel.character
    val update: (CharacterSheet) -> Unit = { viewModel.character = it }
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
            HeaderSection(sheet, portraits, compact, update)

            if (compact) {
                AbilitiesColumn(sheet, update, Modifier.fillMaxWidth())
                CombatColumn(sheet, update, compact = true, modifier = Modifier.fillMaxWidth())
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    AbilitiesColumn(sheet, update, Modifier.width(292.dp))
                    CombatColumn(sheet, update, compact = false, modifier = Modifier.weight(1f))
                }
            }

            SpellcastingSection(sheet, compact, update)
            CharacterNotesSection(sheet, compact, update)
        }

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(Palette.Surface)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            GameButton("Salva scheda", accent = Palette.Heal, onClick = { viewModel.save() })
            viewModel.selectedId?.let { id ->
                GameButton("Elimina", accent = Palette.Enemy, onClick = { deleteId = id })
            }
            if (viewModel.isDirty) {
                Text(
                    "Modifiche non salvate",
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
            title = { Text("Eliminare la scheda?", color = Palette.Text) },
            text = {
                Text(
                    "La scheda di «${sheet.characterName.ifBlank { "Senza nome" }}» verrà eliminata definitivamente.",
                    color = Palette.TextMuted,
                )
            },
            confirmButton = {
                GameButton("Elimina", accent = Palette.Enemy, onClick = {
                    viewModel.delete(id)
                    deleteId = null
                })
            },
            dismissButton = {
                GameButton("Annulla", accent = Palette.TextMuted, onClick = { deleteId = null })
            },
        )
    }
}

// --- intestazione -----------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeaderSection(
    sheet: CharacterSheet,
    portraits: PortraitRepository,
    compact: Boolean,
    update: (CharacterSheet) -> Unit,
) {
    if (compact) {
        CompactHeaderSection(sheet, portraits, update)
        return
    }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        SheetBox("Ritratto", Modifier.width(150.dp)) {
            PortraitPicker(portraits, sheet.id, sheet.characterName)
        }

        SheetBox("Personaggio", Modifier.weight(2.4f)) {
            SheetField("Nome del personaggio", sheet.characterName) {
                update(sheet.copy(characterName = it))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SheetField("Background", sheet.background, Modifier.weight(1f)) {
                    update(sheet.copy(background = it))
                }
                SheetField("Classe", sheet.className, Modifier.weight(1f)) {
                    update(sheet.copy(className = it))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SheetField("Specie", sheet.species, Modifier.weight(1f)) {
                    update(sheet.copy(species = it))
                }
                SheetField("Sottoclasse", sheet.subclass, Modifier.weight(1f)) {
                    update(sheet.copy(subclass = it))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SheetNumberField("Livello", sheet.level, Modifier.weight(1f)) {
                    update(sheet.copy(level = it.coerceIn(1, 20)))
                }
                SheetNumberField("PE", sheet.experiencePoints, Modifier.weight(1f)) {
                    update(sheet.copy(experiencePoints = it.coerceAtLeast(0)))
                }
            }
        }

        SheetBox("Classe armatura", Modifier.width(120.dp)) {
            SheetNumberField("CA", sheet.armorClass) { update(sheet.copy(armorClass = it)) }
            SheetCheck("Scudo", sheet.shieldEquipped) { update(sheet.copy(shieldEquipped = it)) }
        }

        SheetBox("Punti ferita", Modifier.weight(1.2f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SheetNumberField("Attuali", sheet.currentHitPoints, Modifier.weight(1f)) {
                    update(sheet.copy(currentHitPoints = it.coerceIn(0, sheet.maxHitPoints)))
                }
                SheetNumberField("Max", sheet.maxHitPoints, Modifier.weight(1f)) {
                    update(sheet.copy(maxHitPoints = it.coerceAtLeast(1)))
                }
            }
            SheetNumberField("Temporanei", sheet.temporaryHitPoints) {
                update(sheet.copy(temporaryHitPoints = it.coerceAtLeast(0)))
            }
        }

        SheetBox("Dadi vita", Modifier.width(122.dp)) {
            SheetNumberField("Spesi", sheet.hitDiceSpent) {
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

        SheetBox("TS contro morte", Modifier.width(136.dp)) {
            Text("Successi", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
            PipRow(3, sheet.deathSaveSuccesses, color = Palette.Heal) {
                update(sheet.copy(deathSaveSuccesses = it))
            }
            Text("Fallimenti", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
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
    update: (CharacterSheet) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        SheetBox("Ritratto", Modifier.fillMaxWidth()) {
            PortraitPicker(
                repository = portraits,
                definitionId = sheet.id,
                name = sheet.characterName,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        SheetBox("Personaggio", Modifier.fillMaxWidth()) {
            SheetField("Nome del personaggio", sheet.characterName) {
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
                        SheetField("Classe", sheet.className, fieldModifier) {
                            update(sheet.copy(className = it))
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetField("Specie", sheet.species, fieldModifier) {
                            update(sheet.copy(species = it))
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetField("Sottoclasse", sheet.subclass, fieldModifier) {
                            update(sheet.copy(subclass = it))
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetNumberField("Livello", sheet.level, fieldModifier) {
                            update(sheet.copy(level = it.coerceIn(1, 20)))
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetNumberField("PE", sheet.experiencePoints, fieldModifier) {
                            update(sheet.copy(experiencePoints = it.coerceAtLeast(0)))
                        }
                    },
                ),
            )
        }

        SheetBox("Classe armatura", Modifier.fillMaxWidth()) {
            SheetNumberField("CA", sheet.armorClass) { update(sheet.copy(armorClass = it)) }
            SheetCheck("Scudo", sheet.shieldEquipped) { update(sheet.copy(shieldEquipped = it)) }
        }

        SheetBox("Punti ferita", Modifier.fillMaxWidth()) {
            AdaptiveFormRow(
                compact = true,
                compactColumns = 2,
                items = arrayOf(
                    adaptiveFormItem { fieldModifier ->
                        SheetNumberField("Attuali", sheet.currentHitPoints, fieldModifier) {
                            update(sheet.copy(currentHitPoints = it.coerceIn(0, sheet.maxHitPoints)))
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetNumberField("Max", sheet.maxHitPoints, fieldModifier) {
                            update(sheet.copy(maxHitPoints = it.coerceAtLeast(1)))
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetNumberField("Temporanei", sheet.temporaryHitPoints, fieldModifier) {
                            update(sheet.copy(temporaryHitPoints = it.coerceAtLeast(0)))
                        }
                    },
                ),
            )
        }

        SheetBox("Dadi vita", Modifier.fillMaxWidth()) {
            AdaptiveFormRow(
                compact = true,
                compactColumns = 2,
                items = arrayOf(
                    adaptiveFormItem { fieldModifier ->
                        SheetNumberField("Spesi", sheet.hitDiceSpent, fieldModifier) {
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

        SheetBox("TS contro morte", Modifier.fillMaxWidth()) {
            Text("Successi", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
            PipRow(3, sheet.deathSaveSuccesses, color = Palette.Heal) {
                update(sheet.copy(deathSaveSuccesses = it))
            }
            Text("Fallimenti", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
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
    update: (CharacterSheet) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        DerivedValue(
            "Bonus di competenza",
            signed(sheet.proficiencyBonus),
            Modifier.fillMaxWidth(),
        )

        Ability.entries.forEach { ability ->
            AbilityBlock(ability, sheet, update)
        }

        SheetBox("Ispirazione eroica") {
            SheetCheck(
                if (sheet.heroicInspiration) "Disponibile" else "Non disponibile",
                sheet.heroicInspiration,
            ) { update(sheet.copy(heroicInspiration = it)) }
        }

        SheetBox("Addestramento e competenze") {
            Text(
                "Competenza nelle armature",
                color = Palette.TextMuted,
                style = MaterialTheme.typography.labelSmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SheetCheck("Leggera", sheet.armorTraining.light) {
                    update(sheet.copy(armorTraining = sheet.armorTraining.copy(light = it)))
                }
                SheetCheck("Media", sheet.armorTraining.medium) {
                    update(sheet.copy(armorTraining = sheet.armorTraining.copy(medium = it)))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SheetCheck("Pesante", sheet.armorTraining.heavy) {
                    update(sheet.copy(armorTraining = sheet.armorTraining.copy(heavy = it)))
                }
                SheetCheck("Scudi", sheet.armorTraining.shields) {
                    update(sheet.copy(armorTraining = sheet.armorTraining.copy(shields = it)))
                }
            }
            SheetField("Armi", sheet.weaponProficiencies) {
                update(sheet.copy(weaponProficiencies = it))
            }
            SheetField("Strumenti", sheet.toolProficiencies) {
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
    ability: Ability,
    sheet: CharacterSheet,
    update: (CharacterSheet) -> Unit,
) {
    val skills = Skill.of(ability)

    SheetBox(ability.italianLabel) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SheetNumberField("Punteggio", sheet.score(ability), Modifier.weight(1f)) { score ->
                update(sheet.copy(abilityScores = sheet.abilityScores + (ability to score.coerceIn(1, 30))))
            }
            DerivedValue("Modificatore", signed(abilityModifier(sheet.score(ability))))
        }

        ProficiencyLine(
            label = "Tiro salvezza",
            bonus = sheet.saveBonus(ability),
            level = sheet.saveProficiencies[ability] ?: Proficiency.NONE,
            bold = true,
        ) { next ->
            update(sheet.copy(saveProficiencies = sheet.saveProficiencies + (ability to next)))
        }

        skills.forEach { skill ->
            ProficiencyLine(
                label = skill.italianLabel,
                bonus = sheet.skillBonus(skill),
                level = sheet.skillProficiencies[skill] ?: Proficiency.NONE,
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
    }
}

// --- colonna destra: combattimento --------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CombatColumn(
    sheet: CharacterSheet,
    update: (CharacterSheet) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        AdaptiveFormRow(
            compact = compact,
            compactColumns = 2,
            items = arrayOf(
                adaptiveFormItem { itemModifier ->
                    DerivedValue("Iniziativa", signed(sheet.initiativeModifier), itemModifier)
                },
                adaptiveFormItem { itemModifier ->
                    SheetBox("Velocita'", itemModifier) {
                        SheetFeetField("Piedi", sheet.speedFeet) {
                            update(sheet.copy(speedFeet = it.coerceAtLeast(0)))
                        }
                    }
                },
                adaptiveFormItem(1.3f) { itemModifier ->
                    SheetBox("Taglia", itemModifier) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            CreatureSize.entries.forEach { size ->
                                SheetCheck(size.italianLabel, sheet.size == size) {
                                    if (it) update(sheet.copy(size = size))
                                }
                            }
                        }
                    }
                },
                adaptiveFormItem { itemModifier ->
                    DerivedValue("Percezione passiva", sheet.passivePerception.toString(), itemModifier)
                },
            ),
        )

        SheetBox("Armi e trucchetti da combattimento") {
            if (!compact) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ColumnHeader("Nome", Modifier.weight(2f))
                    ColumnHeader("Bonus att. / CD", Modifier.weight(1f))
                    ColumnHeader("Danno e tipo", Modifier.weight(1.6f))
                    ColumnHeader("Note", Modifier.weight(1.6f))
                }
            }
            sheet.weapons.forEachIndexed { index, weapon ->
                WeaponRow(weapon, compact) { updated ->
                    update(
                        sheet.copy(
                            weapons = sheet.weapons.toMutableList().also { it[index] = updated },
                        ),
                    )
                }
            }
            GameButton("+ Aggiungi arma", accent = Palette.Party, onClick = {
                update(sheet.copy(weapons = sheet.weapons + WeaponEntry()))
            })
        }

        SheetBox("Privilegi di classe") {
            SheetTextArea(sheet.classFeatures, minLines = 6) { update(sheet.copy(classFeatures = it)) }
        }

        AdaptiveFormRow(
            compact = compact,
            items = arrayOf(
                adaptiveFormItem { itemModifier ->
                    SheetBox("Tratti della specie", itemModifier) {
                        SheetTextArea(sheet.speciesTraits) { update(sheet.copy(speciesTraits = it)) }
                    }
                },
                adaptiveFormItem { itemModifier ->
                    SheetBox("Talenti", itemModifier) {
                        SheetTextArea(sheet.feats) { update(sheet.copy(feats = it)) }
                    }
                },
            ),
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
private fun WeaponRow(weapon: WeaponEntry, compact: Boolean, onChange: (WeaponEntry) -> Unit) {
    if (compact) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Palette.Night, RoundedCornerShape(7.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            SheetField("Nome", weapon.name) { onChange(weapon.copy(name = it)) }
            AdaptiveFormRow(
                compact = true,
                compactColumns = 2,
                items = arrayOf(
                    adaptiveFormItem { fieldModifier ->
                        SheetNumberField("Bonus att. / CD", weapon.attackBonus, fieldModifier) {
                            onChange(weapon.copy(attackBonus = it))
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetNumberField("Dadi", weapon.diceCount, fieldModifier) {
                            onChange(weapon.copy(diceCount = it.coerceAtLeast(1)))
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetNumberField("Facce", weapon.diceSides, fieldModifier) {
                            onChange(weapon.copy(diceSides = it.coerceAtLeast(2)))
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetNumberField("Modificatore", weapon.damageModifier, fieldModifier) {
                            onChange(weapon.copy(damageModifier = it))
                        }
                    },
                ),
            )
            SheetField("Note", weapon.note) { onChange(weapon.copy(note = it)) }
            SheetCheck("Azione bonus", weapon.bonusAction) {
                onChange(weapon.copy(bonusAction = it))
            }
        }
        return
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
                SheetNumberField("d", weapon.diceCount, Modifier.weight(1f)) {
                    onChange(weapon.copy(diceCount = it.coerceAtLeast(1)))
                }
                SheetNumberField("facce", weapon.diceSides, Modifier.weight(1f)) {
                    onChange(weapon.copy(diceSides = it.coerceAtLeast(2)))
                }
                SheetNumberField("mod", weapon.damageModifier, Modifier.weight(1f)) {
                    onChange(weapon.copy(damageModifier = it))
                }
            }
            SheetField("", weapon.note, Modifier.weight(1.6f)) { onChange(weapon.copy(note = it)) }
        }
        SheetCheck("Azione bonus", weapon.bonusAction) {
            onChange(weapon.copy(bonusAction = it))
        }
    }
}

// --- incantesimi --------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpellcastingSection(
    sheet: CharacterSheet,
    compact: Boolean,
    update: (CharacterSheet) -> Unit,
) {
    val casting = sheet.spellcasting

    SheetBox("Incantesimi") {
        if (casting == null) {
            GameButton("Questo personaggio lancia incantesimi", accent = Palette.Party, onClick = {
                update(sheet.copy(spellcasting = Spellcasting()))
            })
            return@SheetBox
        }

        AdaptiveFormRow(
            compact = compact,
            compactColumns = 2,
            items = arrayOf(
                adaptiveFormItem(1.4f) { itemModifier ->
                    Column(itemModifier) {
                        Text(
                            "Caratteristica da incantatore",
                            color = Palette.TextMuted,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Ability.entries.forEach { ability ->
                                SheetCheck(ability.abbreviation, casting.ability == ability) {
                                    if (it) update(sheet.copy(spellcasting = casting.copy(ability = ability)))
                                }
                            }
                        }
                    }
                },
                adaptiveFormItem { itemModifier ->
                    DerivedValue("Modificatore", signed(sheet.modifier(casting.ability)), itemModifier)
                },
                adaptiveFormItem { itemModifier ->
                    DerivedValue("CD tiro salvezza", sheet.spellSaveDc?.toString() ?: "—", itemModifier)
                },
                adaptiveFormItem { itemModifier ->
                    DerivedValue(
                        "Bonus di attacco",
                        sheet.spellAttackBonus?.let { signed(it) } ?: "—",
                        itemModifier,
                    )
                },
            ),
        )

        Text("Slot incantesimo", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            casting.slots.forEach { slot ->
                SlotBlock(slot) { updated ->
                    update(
                        sheet.copy(
                            spellcasting = casting.copy(
                                slots = casting.slots.map { if (it.level == updated.level) updated else it },
                            ),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SlotBlock(slot: SpellSlot, onChange: (SpellSlot) -> Unit) {
    Column(
        Modifier
            .width(112.dp)
            .background(Palette.Night, RoundedCornerShape(6.dp))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            "Livello ${slot.level}",
            color = Palette.Gold,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall,
        )
        SheetNumberField("Totali", slot.total) { onChange(slot.copy(total = it.coerceIn(0, 9))) }
        // Le caselle mostrano gli slot spesi sul totale disponibile.
        PipRow(slot.total.coerceAtMost(9), slot.spent, color = Palette.Temporary) {
            onChange(slot.copy(spent = it.coerceIn(0, slot.total)))
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
    AdaptiveFormRow(
        compact = compact,
        items = arrayOf(
            adaptiveFormItem { itemModifier ->
                SheetBox("Aspetto", itemModifier) {
                    SheetTextArea(sheet.appearance, minLines = 3) { update(sheet.copy(appearance = it)) }
                }
            },
            adaptiveFormItem(1.4f) { itemModifier ->
                SheetBox("Storia e tratti caratteriali", itemModifier) {
                    SheetTextArea(sheet.backstory, minLines = 3) { update(sheet.copy(backstory = it)) }
                    SheetField("Allineamento", sheet.alignment) { update(sheet.copy(alignment = it)) }
                }
            },
            adaptiveFormItem { itemModifier ->
                Column(itemModifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    SheetBox("Lingue") {
                        SheetTextArea(sheet.languages, minLines = 2) { update(sheet.copy(languages = it)) }
                    }
                    SheetBox("Denari") {
                        AdaptiveFormRow(
                            compact = compact,
                            compactColumns = 2,
                            items = arrayOf(
                                adaptiveFormItem { fieldModifier ->
                                    SheetNumberField("MR", sheet.money.copper, fieldModifier) {
                                        update(sheet.copy(money = sheet.money.copy(copper = it)))
                                    }
                                },
                                adaptiveFormItem { fieldModifier ->
                                    SheetNumberField("MA", sheet.money.silver, fieldModifier) {
                                        update(sheet.copy(money = sheet.money.copy(silver = it)))
                                    }
                                },
                                adaptiveFormItem { fieldModifier ->
                                    SheetNumberField("ME", sheet.money.electrum, fieldModifier) {
                                        update(sheet.copy(money = sheet.money.copy(electrum = it)))
                                    }
                                },
                                adaptiveFormItem { fieldModifier ->
                                    SheetNumberField("MO", sheet.money.gold, fieldModifier) {
                                        update(sheet.copy(money = sheet.money.copy(gold = it)))
                                    }
                                },
                                adaptiveFormItem { fieldModifier ->
                                    SheetNumberField("MP", sheet.money.platinum, fieldModifier) {
                                        update(sheet.copy(money = sheet.money.copy(platinum = it)))
                                    }
                                },
                            ),
                        )
                    }
                }
            },
            adaptiveFormItem(1.2f) { itemModifier ->
                SheetBox("Equipaggiamento", itemModifier) {
                    SheetTextArea(sheet.equipment, minLines = 4) { update(sheet.copy(equipment = it)) }
                    Text(
                        "Sintonia con oggetti magici",
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
