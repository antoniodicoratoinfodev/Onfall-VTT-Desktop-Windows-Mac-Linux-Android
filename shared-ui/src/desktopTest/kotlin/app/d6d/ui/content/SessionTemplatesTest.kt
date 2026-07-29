package app.d6d.ui.content

import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.ExperienceProgression
import app.d6d.sheet.Ability
import app.d6d.sheet.CharacterSheet
import app.d6d.sheet.CreatureSize
import app.d6d.sheet.MonsterStatBlock
import app.d6d.sheet.abilityModifier
import app.d6d.sheet.proficiencyBonusForLevel
import app.d6d.sheet.suggestedProficiencyBonus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Verifica che il contenuto incluso regga il confronto con il documento.
 *
 * I personaggi non vengono controllati valore per valore — li costruisce la
 * progressione guidata dal content pack, che e' gia' sotto test — ma si controlla
 * che siano davvero personaggi guidati e non schede scritte a mano, e che i
 * numeri che il tavolo usa (competenza, iniziativa, tiro per colpire) discendano
 * dalle regole. Delle creature si controlla la costruzione: dado vita dalla
 * taglia, PF medi dai dadi, competenza e PE dal Grado di Sfida.
 */
class SessionTemplatesTest {

    private val templates = SessionTemplates.all

    @Test
    fun `tre partite incluse, quattro personaggi ciascuna, ai tre gradi chiesti`() {
        assertEquals(3, templates.size)
        assertEquals(listOf(1, 4, 20), templates.map { it.partyLevel })
        templates.forEach { template ->
            assertEquals(4, template.party.size, "«${template.name}» non ha quattro personaggi.")
            assertTrue(template.opponents.isNotEmpty(), "«${template.name}» non ha avversari.")
        }
    }

    @Test
    fun `le tre squadre coprono tutte le dodici classi dello SRD`() {
        val classes = templates
            .flatMap { it.party }
            .map { it.className.substringBeforeLast(' ') }
            .toSet()
        assertEquals(CharacterClassId.entries.size, classes.size)
        CharacterClassId.entries.forEach { classId ->
            assertTrue(classId.italianLabel in classes, "manca la classe ${classId.italianLabel}")
        }
    }

    @Test
    fun `ogni personaggio incluso e' una progressione guidata al livello dichiarato`() {
        templates.forEach { template ->
            template.party.forEach { sheet ->
                assertTrue(sheet.progression.configured, "«${sheet.characterName}» non e' guidata.")
                assertEquals(template.partyLevel, sheet.effectiveLevel, sheet.characterName)
                assertEquals(template.partyLevel, sheet.progression.totalLevel, sheet.characterName)
                assertEquals(
                    template.partyLevel,
                    sheet.progression.advancementHistory.size,
                    "«${sheet.characterName}» non ha un avanzamento per livello.",
                )
                // I PE sono quelli che il livello richiede: la scheda deve poter
                // essere ripresa e fatta salire dall'app senza incoerenze.
                assertEquals(
                    ExperienceProgression.thresholdForLevel(template.partyLevel),
                    sheet.experiencePoints,
                    sheet.characterName,
                )
            }
        }
    }

    @Test
    fun `competenza, iniziativa e tiri per colpire seguono le regole`() {
        templates.flatMap { it.party }.forEach { sheet ->
            assertEquals(
                proficiencyBonusForLevel(sheet.effectiveLevel),
                sheet.proficiencyBonus,
                sheet.characterName,
            )
            assertEquals(
                abilityModifier(sheet.score(Ability.DEXTERITY)),
                sheet.initiativeModifier,
                sheet.characterName,
            )
            assertTrue(
                sheet.abilityScores.values.all { it in 1..20 },
                "«${sheet.characterName}» ha un punteggio fuori scala.",
            )
            sheet.weapons.forEach { weapon ->
                // Competenza piu' la caratteristica usata dall'arma: nessuna riga
                // deve essere rimasta al bonus del livello in cui fu scelta.
                val best = maxOf(
                    abilityModifier(sheet.score(Ability.STRENGTH)),
                    abilityModifier(sheet.score(Ability.DEXTERITY)),
                )
                assertEquals(
                    sheet.proficiencyBonus + weapon.damageModifier,
                    weapon.attackBonus,
                    "«${sheet.characterName}»: ${weapon.name}",
                )
                assertTrue(
                    weapon.damageModifier <= best,
                    "«${sheet.characterName}»: ${weapon.name} usa una caratteristica che non ha.",
                )
            }
        }
    }

    @Test
    fun `i privilegi con un effetto numerico arrivano fino alla scheda inclusa`() {
        val tarvos = SessionTemplates.ruins.party.first { it.characterName.startsWith("Tarvos") }
        assertTrue(
            tarvos.progression.selections.flatMap { it.optionIds }.any { it.endsWith(":difesa") },
            "Tarvos non ha lo Stile Difesa: l'esempio non verifica piu' nulla",
        )
        assertEquals(1, tarvos.armorClassEffectBonus)
        // Cotta di maglia 16, scudo +2, piu' il punto dello stile.
        assertEquals(19, tarvos.effectiveArmorClass)

        val shen = SessionTemplates.crown.party.first { it.characterName.startsWith("Shen") }
        // Monaco di 20º senza armatura: 30 piedi piu' i 30 del Movimento senza armatura.
        assertEquals(60, shen.effectiveSpeedFeet)
    }

    @Test
    fun `gli stat block sono costruiti come li descrive il documento`() {
        templates.flatMap { it.monsters }.forEach { creature ->
            assertEquals(hitDieFor(creature.size), creature.hitDiceSides, creature.name)
            assertEquals(
                abilityModifier(creature.score(Ability.CONSTITUTION)) * creature.hitDiceCount,
                creature.hitDiceModifier,
                "${creature.name}: il modificatore dei dadi vita non viene dalla Costituzione.",
            )
            assertEquals(
                creature.hitDiceCount * (creature.hitDiceSides + 1) / 2 + creature.hitDiceModifier,
                creature.averageHitPoints,
                "${creature.name}: i PF medi non corrispondono ai dadi vita.",
            )
            assertEquals(
                abilityModifier(creature.score(Ability.DEXTERITY)),
                creature.initiativeModifier,
                creature.name,
            )
            assertEquals(10 + creature.initiativeModifier, creature.initiativeScore, creature.name)
            assertEquals(
                suggestedProficiencyBonus(challengeRatingOf(creature)),
                creature.proficiencyBonus,
                "${creature.name}: competenza incoerente col Grado di Sfida.",
            )
            assertEquals(
                expectedXp(creature.challengeRating),
                creature.baseXp,
                "${creature.name}: PE fuori dalla tabella del Grado di Sfida.",
            )
        }
    }

    @Test
    fun `gli attacchi delle creature usano competenza e caratteristica`() {
        templates.flatMap { it.monsters }.forEach { creature ->
            val sections = creature.actions + creature.bonusActions +
                creature.reactions + creature.legendaryActions
            val strikes = sections.mapNotNull { it.attack }
            assertTrue(strikes.isNotEmpty(), "${creature.name} non ha nemmeno un attacco eseguibile.")
            strikes.forEach { attack ->
                val best = maxOf(
                    creature.modifier(Ability.STRENGTH),
                    creature.modifier(Ability.DEXTERITY),
                )
                assertEquals(
                    creature.proficiencyBonus + attack.damageModifier,
                    attack.attackBonus,
                    "${creature.name}: ${attack.name}",
                )
                assertEquals(
                    best,
                    attack.damageModifier,
                    "${creature.name}: ${attack.name} non usa la caratteristica migliore.",
                )
            }
        }
    }

    @Test
    fun `avversari tarati sul budget di PE della squadra`() {
        // Confronto con la soglia "difficile" del documento: quattro personaggi,
        // il budget del loro livello. Un template incluso non deve essere una
        // passeggiata ne' una condanna.
        val budgets = mapOf(1 to 400L, 4 to 2_000L, 20 to 34_000L)
        templates.forEach { template ->
            val total = template.opponents.sumOf { it.statBlock.baseXp * it.quantity }
            val budget = budgets.getValue(template.partyLevel)
            assertTrue(
                total in (budget / 2)..budget * 2,
                "«${template.name}»: $total PE contro un budget di $budget.",
            )
        }
    }

    @Test
    fun `la sessione parte con tutti i combattenti, la mappa e l'iniziativa`() {
        templates.forEach { template ->
            val state = template.startedSession().currentState()
            assertEquals(
                template.party.size + template.opponentCount,
                state.combatants().size,
                template.name,
            )
            assertEquals(template.party.size, state.partyCombatantIds().size, template.name)
            assertEquals(template.gridColumns, state.battleMap().grid().columns(), template.name)
            assertEquals(template.gridRows, state.battleMap().grid().rows(), template.name)
            assertEquals(
                state.combatants().size,
                state.initiativeScores().size,
                "«${template.name}»: qualcuno non ha iniziativa.",
            )
            // Schierata, non da schierare: si apre e si gioca.
            state.combatants().keys.forEach { id ->
                assertTrue(
                    state.battleMap().isPlaced(id),
                    "«${template.name}»: $id non è sulla mappa.",
                )
            }
        }
    }

    @Test
    fun `i personaggi portano in battaglia armi e capacita' della scheda`() {
        templates.forEach { template ->
            val state = template.startedSession().currentState()
            template.party.forEach { sheet ->
                val snapshot = state.combatants().getValue(sheet.id).snapshot()
                assertEquals(sheet.effectiveArmorClass, snapshot.armorClass(), sheet.characterName)
                assertEquals(sheet.maxHitPoints, snapshot.maxHitPoints(), sheet.characterName)
                assertTrue(
                    snapshot.abilities().isNotEmpty(),
                    "«${sheet.characterName}» arriva al tavolo senza nulla da fare.",
                )
            }
        }
    }

    private fun hitDieFor(size: CreatureSize): Int = when (size) {
        CreatureSize.TINY -> 4
        CreatureSize.SMALL -> 6
        CreatureSize.MEDIUM -> 8
        CreatureSize.LARGE -> 10
        CreatureSize.HUGE -> 12
        CreatureSize.GARGANTUAN -> 20
    }

    private fun challengeRatingOf(creature: MonsterStatBlock): Double =
        creature.challengeRating.split('/').let { parts ->
            if (parts.size == 2) parts[0].toDouble() / parts[1].toDouble() else parts[0].toDouble()
        }

    private fun expectedXp(challengeRating: String): Long = when (challengeRating) {
        "1/8" -> 25
        "1/4" -> 50
        "1/2" -> 100
        "1" -> 200
        "2" -> 450
        "3" -> 700
        "4" -> 1_100
        "5" -> 1_800
        "9" -> 5_000
        "20" -> 25_000
        else -> error("Grado di sfida non previsto dal test: $challengeRating")
    }

}
