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
import app.d6d.domain.combat.ConditionType
import app.d6d.domain.combat.DamageType
import app.d6d.sheet.Ability
import app.d6d.sheet.CreatureSize
import app.d6d.sheet.MonsterStatBlock
import app.d6d.sheet.Proficiency
import app.d6d.sheet.Skill
import app.d6d.sheet.StatBlockEntry
import app.d6d.sheet.WeaponEntry
import app.d6d.sheet.abilityModifier
import app.d6d.sheet.suggestedProficiencyBonus
import app.d6d.ui.battle.GameButton
import app.d6d.ui.images.PortraitPicker
import app.d6d.ui.images.PortraitRepository
import app.d6d.ui.components.Chip
import app.d6d.ui.components.italianLabel as conditionLabel
import app.d6d.ui.compendium.italianLabel as damageLabel
import app.d6d.ui.theme.Palette

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
    val block = viewModel.monster
    val update: (MonsterStatBlock) -> Unit = { viewModel.monster = it }

    Column(
        modifier
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
            Text(
                text = block.name.ifBlank { "Creatura senza nome" },
                color = Palette.GoldBright,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.displaySmall,
            )
            Text(block.subtitle, color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
            StatLine("CA", "${block.armorClass}")
            StatLine("Iniziativa", "${signed(block.initiativeModifier)} (${block.initiativeScore})")
            StatLine("PF", block.hitPointsText)
            StatLine("Velocita'", block.speeds.text)
            StatLine("Percezione", "passiva ${block.passivePerception}")
            StatLine("GS", "${block.challengeRating} (PE ${block.baseXp}" +
                (block.lairXp?.let { "; in tana $it" } ?: "") + "; BC ${signed(block.proficiencyBonus)})")
        }

        SheetBox("Ritratto") {
            PortraitPicker(portraits, block.id, block.name)
        }

        SheetBox("Intestazione") {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SheetField("Nome", block.name, Modifier.weight(2f)) { update(block.copy(name = it)) }
                SheetField("Identificatore", block.id, Modifier.weight(1.4f)) { update(block.copy(id = it)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SheetField("Tipo", block.type, Modifier.weight(1f)) { update(block.copy(type = it)) }
                SheetField("Tag", block.tags, Modifier.weight(1f)) { update(block.copy(tags = it)) }
                SheetField("Allineamento", block.alignment, Modifier.weight(1.2f)) {
                    update(block.copy(alignment = it))
                }
            }
            Text("Taglia", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                CreatureSize.entries.forEach { size ->
                    SheetCheck(size.italianLabel, block.size == size) {
                        if (it) update(block.copy(size = size))
                    }
                }
            }
        }

        SheetBox("Difesa, iniziativa e punti ferita") {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SheetNumberField("Classe Armatura", block.armorClass, Modifier.weight(1f)) {
                    update(block.copy(armorClass = it))
                }
                SheetNumberField("Mod. iniziativa", block.initiativeModifier, Modifier.weight(1f)) {
                    // Il punteggio statico segue il modificatore, ma resta un campo
                    // distinto e modificabile a parte.
                    update(block.copy(initiativeModifier = it, initiativeScore = 10 + it))
                }
                SheetNumberField("Punteggio statico", block.initiativeScore, Modifier.weight(1f)) {
                    update(block.copy(initiativeScore = it))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SheetNumberField("PF medi", block.averageHitPoints, Modifier.weight(1.2f)) {
                    update(block.copy(averageHitPoints = it.coerceAtLeast(1)))
                }
                SheetNumberField("Numero dadi", block.hitDiceCount, Modifier.weight(1f)) {
                    update(block.copy(hitDiceCount = it.coerceAtLeast(1)))
                }
                SheetNumberField("Facce", block.hitDiceSides, Modifier.weight(1f)) {
                    update(block.copy(hitDiceSides = it.coerceAtLeast(2)))
                }
                SheetNumberField("Modificatore", block.hitDiceModifier, Modifier.weight(1f)) {
                    update(block.copy(hitDiceModifier = it))
                }
            }
        }

        SheetBox("Velocita'") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SheetNumberField("A piedi", block.speeds.walk, Modifier.weight(1f)) {
                    update(block.copy(speeds = block.speeds.copy(walk = it.coerceAtLeast(0))))
                }
                SheetNumberField("Volo", block.speeds.fly, Modifier.weight(1f)) {
                    update(block.copy(speeds = block.speeds.copy(fly = it.coerceAtLeast(0))))
                }
                SheetNumberField("Nuoto", block.speeds.swim, Modifier.weight(1f)) {
                    update(block.copy(speeds = block.speeds.copy(swim = it.coerceAtLeast(0))))
                }
                SheetNumberField("Scalata", block.speeds.climb, Modifier.weight(1f)) {
                    update(block.copy(speeds = block.speeds.copy(climb = it.coerceAtLeast(0))))
                }
                SheetNumberField("Scavo", block.speeds.burrow, Modifier.weight(1f)) {
                    update(block.copy(speeds = block.speeds.copy(burrow = it.coerceAtLeast(0))))
                }
            }
            SheetCheck("Puo' fluttuare", block.speeds.hover) {
                update(block.copy(speeds = block.speeds.copy(hover = it)))
            }
        }

        SheetBox("Caratteristiche") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Ability.entries.forEach { ability ->
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            ability.abbreviation,
                            color = Palette.Gold,
                            fontWeight = FontWeight.Black,
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
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ProficiencyDot(
                                filled = block.saveProficiencies[ability] == Proficiency.PROFICIENT,
                                expertise = false,
                                onClick = {
                                    val next = if (block.saveProficiencies[ability] == Proficiency.PROFICIENT) {
                                        Proficiency.NONE
                                    } else {
                                        Proficiency.PROFICIENT
                                    }
                                    update(
                                        block.copy(saveProficiencies = block.saveProficiencies + (ability to next)),
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
            }
        }

        SheetBox("Abilita'") {
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
                        Chip("${skill.italianLabel}$suffix", color)
                    }
                }
            }
        }

        SheetBox("Difese tipizzate") {
            DamageToggleRow("Resistenze", block.resistances) { update(block.copy(resistances = it)) }
            DamageToggleRow("Vulnerabilita'", block.vulnerabilities) { update(block.copy(vulnerabilities = it)) }
            DamageToggleRow("Immunita' ai danni", block.damageImmunities) {
                update(block.copy(damageImmunities = it))
            }
            Text(
                "Immunita' alle condizioni",
                color = Palette.TextMuted,
                style = MaterialTheme.typography.labelSmall,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ConditionType.entries.forEach { condition ->
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
                        Chip(condition.conditionLabel, if (on) Palette.Bloodied else Palette.TextFaint)
                    }
                }
            }
        }

        SheetBox("Sensi, lingue ed equipaggiamento") {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SheetField("Sensi", block.senses, Modifier.weight(1.4f)) { update(block.copy(senses = it)) }
                SheetField("Lingue", block.languages, Modifier.weight(1f)) { update(block.copy(languages = it)) }
            }
            // Gear elenca solo gli oggetti recuperabili: non e' tutto cio' che indossa.
            SheetField("Gear (oggetti recuperabili)", block.gear) { update(block.copy(gear = it)) }
        }

        SheetBox("Sfida") {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SheetField("Grado di Sfida", block.challengeRating, Modifier.weight(1f)) { rating ->
                    val suggested = rating.trim().toDoubleOrNull()
                        ?.let { suggestedProficiencyBonus(it) } ?: block.proficiencyBonus
                    update(block.copy(challengeRating = rating, proficiencyBonus = suggested))
                }
                SheetNumberField("PE base", block.baseXp.toInt(), Modifier.weight(1f)) {
                    update(block.copy(baseXp = it.toLong().coerceAtLeast(0)))
                }
                SheetNumberField("PE in tana", (block.lairXp ?: 0L).toInt(), Modifier.weight(1f)) {
                    update(block.copy(lairXp = if (it <= 0) null else it.toLong()))
                }
                SheetNumberField("Bonus competenza", block.proficiencyBonus, Modifier.weight(1f)) {
                    update(block.copy(proficiencyBonus = it))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SheetField("Habitat", block.habitat, Modifier.weight(1f)) { update(block.copy(habitat = it)) }
                SheetField("Tema del tesoro", block.treasureTheme, Modifier.weight(1f)) {
                    update(block.copy(treasureTheme = it))
                }
            }
        }

        EntrySection("Tratti", block.traits) { update(block.copy(traits = it)) }
        EntrySection("Azioni", block.actions) { update(block.copy(actions = it)) }
        EntrySection("Azioni Bonus", block.bonusActions) { update(block.copy(bonusActions = it)) }
        EntrySection("Reazioni", block.reactions) { update(block.copy(reactions = it)) }
        EntrySection("Azioni Leggendarie", block.legendaryActions) {
            update(block.copy(legendaryActions = it))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            GameButton("Salva stat block", accent = Palette.Heal, onClick = { viewModel.save() })
            viewModel.selectedId?.let { id ->
                GameButton("Elimina", accent = Palette.Enemy, onClick = { viewModel.delete(id) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DamageToggleRow(
    label: String,
    selected: Set<DamageType>,
    onChange: (Set<DamageType>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            DamageType.entries.forEach { type ->
                val on = type in selected
                Box(Modifier.clickable { onChange(if (on) selected - type else selected + type) }) {
                    Chip(type.damageLabel, if (on) Palette.Bloodied else Palette.TextFaint)
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
    onChange: (List<StatBlockEntry>) -> Unit,
) {
    SheetBox("$title (${entries.size})") {
        entries.forEachIndexed { index, entry ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Palette.Night, RoundedCornerShape(7.dp))
                    .padding(9.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SheetField("Nome", entry.name, Modifier.weight(1f)) {
                        onChange(entries.toMutableList().also { l -> l[index] = entry.copy(name = it) })
                    }
                    Chip(
                        if (entry.automated) "Automatica" else "Manuale",
                        if (entry.automated) Palette.Heal else Palette.Bloodied,
                    )
                }
                SheetTextArea(entry.text, minLines = 2) {
                    onChange(entries.toMutableList().also { l -> l[index] = entry.copy(text = it) })
                }

                val attack = entry.attack
                if (attack == null) {
                    GameButton("Rendi attacco eseguibile", accent = Palette.Party, onClick = {
                        onChange(
                            entries.toMutableList().also { l ->
                                l[index] = entry.copy(attack = WeaponEntry(name = entry.name))
                            },
                        )
                    })
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        SheetNumberField("Bonus att.", attack.attackBonus, Modifier.weight(1f)) {
                            onChange(
                                entries.toMutableList().also { l ->
                                    l[index] = entry.copy(attack = attack.copy(attackBonus = it))
                                },
                            )
                        }
                        SheetNumberField("Portata", attack.rangeFeet, Modifier.weight(1f)) {
                            onChange(
                                entries.toMutableList().also { l ->
                                    l[index] = entry.copy(attack = attack.copy(rangeFeet = it))
                                },
                            )
                        }
                        SheetNumberField("Dadi", attack.diceCount, Modifier.weight(1f)) {
                            onChange(
                                entries.toMutableList().also { l ->
                                    l[index] = entry.copy(attack = attack.copy(diceCount = it.coerceAtLeast(1)))
                                },
                            )
                        }
                        SheetNumberField("Facce", attack.diceSides, Modifier.weight(1f)) {
                            onChange(
                                entries.toMutableList().also { l ->
                                    l[index] = entry.copy(attack = attack.copy(diceSides = it.coerceAtLeast(2)))
                                },
                            )
                        }
                        SheetNumberField("Mod.", attack.damageModifier, Modifier.weight(1f)) {
                            onChange(
                                entries.toMutableList().also { l ->
                                    l[index] = entry.copy(attack = attack.copy(damageModifier = it))
                                },
                            )
                        }
                    }
                }

                GameButton("Rimuovi", accent = Palette.Enemy, onClick = {
                    onChange(entries.filterIndexed { i, _ -> i != index })
                })
            }
        }
        GameButton("+ Aggiungi voce", accent = Palette.Party, onClick = {
            onChange(entries + StatBlockEntry())
        })
    }
}
