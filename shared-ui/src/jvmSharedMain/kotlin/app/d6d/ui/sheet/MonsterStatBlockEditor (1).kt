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
import androidx.compose.runtime.setValue
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
            AdaptiveFormRow(
                compact = compact,
                items = arrayOf(
                    adaptiveFormItem(2f) { fieldModifier ->
                        SheetField("Nome", block.name, fieldModifier) { update(block.copy(name = it)) }
                    },
                    adaptiveFormItem(1.4f) { fieldModifier ->
                        SheetField("Identificatore", block.id, fieldModifier) { update(block.copy(id = it)) }
                    },
                ),
            )
            AdaptiveFormRow(
                compact = compact,
                compactColumns = if (compact) 2 else 3,
                items = arrayOf(
                    adaptiveFormItem { fieldModifier ->
                        SheetField("Tipo", block.type, fieldModifier) { update(block.copy(type = it)) }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetField("Tag", block.tags, fieldModifier) { update(block.copy(tags = it)) }
                    },
                    adaptiveFormItem(1.2f) { fieldModifier ->
                        SheetField("Allineamento", block.alignment, fieldModifier) {
                            update(block.copy(alignment = it))
                        }
                    },
                ),
            )
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
            AdaptiveFormRow(
                compact = compact,
                compactColumns = 2,
                items = arrayOf(
                    adaptiveFormItem { fieldModifier ->
                        SheetNumberField("Classe Armatura", block.armorClass, fieldModifier) {
                            update(block.copy(armorClass = it))
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetNumberField("Mod. iniziativa", block.initiativeModifier, fieldModifier) {
                            // Il punteggio statico segue il modificatore, ma resta un campo
                            // distinto e modificabile a parte.
                            update(block.copy(initiativeModifier = it, initiativeScore = 10 + it))
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetNumberField("Punteggio statico", block.initiativeScore, fieldModifier) {
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
                        SheetNumberField("PF medi", block.averageHitPoints, fieldModifier) {
                            update(block.copy(averageHitPoints = it.coerceAtLeast(1)))
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetNumberField("Numero dadi", block.hitDiceCount, fieldModifier) {
                            update(block.copy(hitDiceCount = it.coerceAtLeast(1)))
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetNumberField("Facce", block.hitDiceSides, fieldModifier) {
                            update(block.copy(hitDiceSides = it.coerceAtLeast(2)))
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetNumberField("Modificatore", block.hitDiceModifier, fieldModifier) {
                            update(block.copy(hitDiceModifier = it))
                        }
                    },
                ),
            )
        }

        SheetBox("Velocita'") {
            AdaptiveFormRow(
                compact = compact,
                compactColumns = 2,
                items = arrayOf(
                    adaptiveFormItem { fieldModifier ->
                        SheetFeetField("A piedi", block.speeds.walk, fieldModifier) {
                            update(block.copy(speeds = block.speeds.copy(walk = it.coerceAtLeast(0))))
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetFeetField("Volo", block.speeds.fly, fieldModifier) {
                            update(block.copy(speeds = block.speeds.copy(fly = it.coerceAtLeast(0))))
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetFeetField("Nuoto", block.speeds.swim, fieldModifier) {
                            update(block.copy(speeds = block.speeds.copy(swim = it.coerceAtLeast(0))))
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetFeetField("Scalata", block.speeds.climb, fieldModifier) {
                            update(block.copy(speeds = block.speeds.copy(climb = it.coerceAtLeast(0))))
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetFeetField("Scavo", block.speeds.burrow, fieldModifier) {
                            update(block.copy(speeds = block.speeds.copy(burrow = it.coerceAtLeast(0))))
                        }
                    },
                ),
            )
            SheetCheck("Puo' fluttuare", block.speeds.hover) {
                update(block.copy(speeds = block.speeds.copy(hover = it)))
            }
        }

        SheetBox("Caratteristiche") {
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
            AdaptiveFormRow(
                compact = compact,
                items = arrayOf(
                    adaptiveFormItem(1.4f) { fieldModifier ->
                        SheetField("Sensi", block.senses, fieldModifier) { update(block.copy(senses = it)) }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetField("Lingue", block.languages, fieldModifier) {
                            update(block.copy(languages = it))
                        }
                    },
                ),
            )
            // Gear elenca solo gli oggetti recuperabili: non e' tutto cio' che indossa.
            SheetField("Gear (oggetti recuperabili)", block.gear) { update(block.copy(gear = it)) }
        }

        SheetBox("Sfida") {
            AdaptiveFormRow(
                compact = compact,
                compactColumns = 2,
                items = arrayOf(
                    adaptiveFormItem { fieldModifier ->
                        SheetField("Grado di Sfida", block.challengeRating, fieldModifier) { rating ->
                            val suggested = rating.trim().toDoubleOrNull()
                                ?.let { suggestedProficiencyBonus(it) } ?: block.proficiencyBonus
                            update(block.copy(challengeRating = rating, proficiencyBonus = suggested))
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetNumberField("PE base", block.baseXp.toInt(), fieldModifier) {
                            update(block.copy(baseXp = it.toLong().coerceAtLeast(0)))
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetNumberField("PE in tana", (block.lairXp ?: 0L).toInt(), fieldModifier) {
                            update(block.copy(lairXp = if (it <= 0) null else it.toLong()))
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetNumberField("Bonus competenza", block.proficiencyBonus, fieldModifier) {
                            update(block.copy(proficiencyBonus = it))
                        }
                    },
                ),
            )
            AdaptiveFormRow(
                compact = compact,
                items = arrayOf(
                    adaptiveFormItem { fieldModifier ->
                        SheetField("Habitat", block.habitat, fieldModifier) {
                            update(block.copy(habitat = it))
                        }
                    },
                    adaptiveFormItem { fieldModifier ->
                        SheetField("Tema del tesoro", block.treasureTheme, fieldModifier) {
                            update(block.copy(treasureTheme = it))
                        }
                    },
                ),
            )
        }

        EntrySection("Tratti", block.traits, compact) { update(block.copy(traits = it)) }
        EntrySection("Azioni", block.actions, compact) { update(block.copy(actions = it)) }
        EntrySection("Azioni Bonus", block.bonusActions, compact) { update(block.copy(bonusActions = it)) }
        EntrySection("Reazioni", block.reactions, compact) { update(block.copy(reactions = it)) }
        EntrySection("Azioni Leggendarie", block.legendaryActions, compact) {
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
            GameButton("Salva stat block", accent = Palette.Heal, onClick = { viewModel.save() })
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
            title = { Text("Eliminare lo stat block?", color = Palette.Text) },
            text = {
                Text(
                    "«${block.name.ifBlank { "Creatura senza nome" }}» verrà eliminata definitivamente.",
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
    compact: Boolean,
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
                if (compact) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        SheetField("Nome", entry.name) {
                            onChange(entries.toMutableList().also { l -> l[index] = entry.copy(name = it) })
                        }
                        Chip(
                            if (entry.automated) "Automatica" else "Manuale",
                            if (entry.automated) Palette.Heal else Palette.Bloodied,
                        )
                    }
                } else {
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
                    AdaptiveFormRow(
                        compact = compact,
                        compactColumns = 2,
                        items = arrayOf(
                            adaptiveFormItem { fieldModifier ->
                                SheetNumberField("Bonus att.", attack.attackBonus, fieldModifier) {
                                    onChange(
                                        entries.toMutableList().also { l ->
                                            l[index] = entry.copy(attack = attack.copy(attackBonus = it))
                                        },
                                    )
                                }
                            },
                            adaptiveFormItem { fieldModifier ->
                                SheetFeetField("Portata", attack.rangeFeet, fieldModifier) {
                                    onChange(
                                        entries.toMutableList().also { l ->
                                            l[index] = entry.copy(attack = attack.copy(rangeFeet = it))
                                        },
                                    )
                                }
                            },
                            adaptiveFormItem { fieldModifier ->
                                SheetNumberField("Dadi", attack.diceCount, fieldModifier) {
                                    onChange(
                                        entries.toMutableList().also { l ->
                                            l[index] = entry.copy(
                                                attack = attack.copy(diceCount = it.coerceAtLeast(1)),
                                            )
                                        },
                                    )
                                }
                            },
                            adaptiveFormItem { fieldModifier ->
                                SheetNumberField("Facce", attack.diceSides, fieldModifier) {
                                    onChange(
                                        entries.toMutableList().also { l ->
                                            l[index] = entry.copy(
                                                attack = attack.copy(diceSides = it.coerceAtLeast(2)),
                                            )
                                        },
                                    )
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
