package app.d6d.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        HeaderSection(sheet, portraits, update)

        if (compact) {
            AbilitiesColumn(sheet, update, Modifier.fillMaxWidth())
            CombatColumn(sheet, update, Modifier.fillMaxWidth())
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                AbilitiesColumn(sheet, update, Modifier.width(292.dp))
                CombatColumn(sheet, update, Modifier.weight(1f))
            }
        }

        SpellcastingSection(sheet, update)
        CharacterNotesSection(sheet, update)

        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            GameButton("Salva scheda", accent = Palette.Heal, onClick = { viewModel.save() })
            viewModel.selectedId?.let { id ->
                GameButton("Elimina", accent = Palette.Enemy, onClick = { viewModel.delete(id) })
            }
        }
    }
}

// --- intestazione -----------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeaderSection(
    sheet: CharacterSheet,
    portraits: PortraitRepository,
    update: (CharacterSheet) -> Unit,
) {
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
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DerivedValue("Iniziativa", signed(sheet.initiativeModifier), Modifier.weight(1f))
            SheetBox("Velocita'", Modifier.weight(1f)) {
                SheetNumberField("Piedi", sheet.speedFeet) { update(sheet.copy(speedFeet = it.coerceAtLeast(0))) }
            }
            SheetBox("Taglia", Modifier.weight(1.3f)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CreatureSize.entries.forEach { size ->
                        SheetCheck(size.italianLabel, sheet.size == size) {
                            if (it) update(sheet.copy(size = size))
                        }
                    }
                }
            }
            DerivedValue("Percezione passiva", sheet.passivePerception.toString(), Modifier.weight(1f))
        }

        SheetBox("Armi e trucchetti da combattimento") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ColumnHeader("Nome", Modifier.weight(2f))
                ColumnHeader("Bonus att. / CD", Modifier.weight(1f))
                ColumnHeader("Danno e tipo", Modifier.weight(1.6f))
                ColumnHeader("Note", Modifier.weight(1.6f))
            }
            sheet.weapons.forEachIndexed { index, weapon ->
                WeaponRow(weapon) { updated ->
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

        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            SheetBox("Tratti della specie", Modifier.weight(1f)) {
                SheetTextArea(sheet.speciesTraits) { update(sheet.copy(speciesTraits = it)) }
            }
            SheetBox("Talenti", Modifier.weight(1f)) {
                SheetTextArea(sheet.feats) { update(sheet.copy(feats = it)) }
            }
        }
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
private fun WeaponRow(weapon: WeaponEntry, onChange: (WeaponEntry) -> Unit) {
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
}

// --- incantesimi --------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpellcastingSection(sheet: CharacterSheet, update: (CharacterSheet) -> Unit) {
    val casting = sheet.spellcasting

    SheetBox("Incantesimi") {
        if (casting == null) {
            GameButton("Questo personaggio lancia incantesimi", accent = Palette.Party, onClick = {
                update(sheet.copy(spellcasting = Spellcasting()))
            })
            return@SheetBox
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1.4f)) {
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
            DerivedValue("Modificatore", signed(sheet.modifier(casting.ability)))
            DerivedValue("CD tiro salvezza", sheet.spellSaveDc?.toString() ?: "—")
            DerivedValue("Bonus di attacco", sheet.spellAttackBonus?.let { signed(it) } ?: "—")
        }

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
private fun CharacterNotesSection(sheet: CharacterSheet, update: (CharacterSheet) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        SheetBox("Aspetto", Modifier.weight(1f)) {
            SheetTextArea(sheet.appearance, minLines = 3) { update(sheet.copy(appearance = it)) }
        }
        SheetBox("Storia e tratti caratteriali", Modifier.weight(1.4f)) {
            SheetTextArea(sheet.backstory, minLines = 3) { update(sheet.copy(backstory = it)) }
            SheetField("Allineamento", sheet.alignment) { update(sheet.copy(alignment = it)) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            SheetBox("Lingue") {
                SheetTextArea(sheet.languages, minLines = 2) { update(sheet.copy(languages = it)) }
            }
            SheetBox("Denari") {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SheetNumberField("MR", sheet.money.copper, Modifier.weight(1f)) {
                        update(sheet.copy(money = sheet.money.copy(copper = it)))
                    }
                    SheetNumberField("MA", sheet.money.silver, Modifier.weight(1f)) {
                        update(sheet.copy(money = sheet.money.copy(silver = it)))
                    }
                    SheetNumberField("ME", sheet.money.electrum, Modifier.weight(1f)) {
                        update(sheet.copy(money = sheet.money.copy(electrum = it)))
                    }
                    SheetNumberField("MO", sheet.money.gold, Modifier.weight(1f)) {
                        update(sheet.copy(money = sheet.money.copy(gold = it)))
                    }
                    SheetNumberField("MP", sheet.money.platinum, Modifier.weight(1f)) {
                        update(sheet.copy(money = sheet.money.copy(platinum = it)))
                    }
                }
            }
        }
        SheetBox("Equipaggiamento", Modifier.weight(1.2f)) {
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
    }
}
