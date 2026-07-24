package app.d6d.sheet

import app.d6d.domain.combat.ActivationCost
import app.d6d.domain.combat.DamageType
import app.d6d.domain.combat.ResolutionMethod
import app.d6d.domain.combat.SaveAbility
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Valori derivati della scheda.
 *
 * Sono regole, non presentazione: qui si verifica che la scheda calcoli come il
 * regolamento e non come farebbe l'aritmetica ingenua.
 */
class SheetDerivationsTest {

    @Test
    fun `il modificatore si arrotonda per difetto anche sotto dieci`() {
        // La divisione intera di Kotlin tronca verso lo zero e darebbe -1 per 7.
        assertEquals(-2, abilityModifier(7))
        assertEquals(-1, abilityModifier(8))
        assertEquals(-1, abilityModifier(9))
        assertEquals(0, abilityModifier(10))
        assertEquals(0, abilityModifier(11))
        assertEquals(3, abilityModifier(17))
        assertEquals(-5, abilityModifier(1))
    }

    @Test
    fun `il bonus di competenza segue le soglie di livello`() {
        assertEquals(2, proficiencyBonusForLevel(1))
        assertEquals(2, proficiencyBonusForLevel(4))
        assertEquals(3, proficiencyBonusForLevel(5))
        assertEquals(4, proficiencyBonusForLevel(9))
        assertEquals(5, proficiencyBonusForLevel(13))
        assertEquals(6, proficiencyBonusForLevel(17))
        assertEquals(6, proficiencyBonusForLevel(20))
    }

    @Test
    fun `il tiro salvezza somma il bonus solo se competente`() {
        val sheet = CharacterSheet(
            level = 5,
            abilityScores = mapOf(Ability.STRENGTH to 16, Ability.DEXTERITY to 14),
            saveProficiencies = mapOf(Ability.STRENGTH to Proficiency.PROFICIENT),
        )

        assertEquals(3, sheet.modifier(Ability.STRENGTH))
        // +3 di caratteristica piu' +3 di competenza al livello 5.
        assertEquals(6, sheet.saveBonus(Ability.STRENGTH))
        // Senza competenza resta il solo modificatore.
        assertEquals(2, sheet.saveBonus(Ability.DEXTERITY))
    }

    @Test
    fun `la maestria raddoppia il bonus di competenza`() {
        val sheet = CharacterSheet(
            level = 5,
            abilityScores = mapOf(Ability.DEXTERITY to 16),
            skillProficiencies = mapOf(
                Skill.FURTIVITA to Proficiency.EXPERTISE,
                Skill.ACROBAZIA to Proficiency.PROFICIENT,
            ),
        )

        assertEquals(9, sheet.skillBonus(Skill.FURTIVITA))
        assertEquals(6, sheet.skillBonus(Skill.ACROBAZIA))
        assertEquals(3, sheet.skillBonus(Skill.RAPIDITA_DI_MANO))
    }

    @Test
    fun `l'iniziativa e' una prova di Destrezza e il punteggio statico e' dieci piu' il modificatore`() {
        val sheet = CharacterSheet(abilityScores = mapOf(Ability.DEXTERITY to 18))

        assertEquals(4, sheet.initiativeModifier)
        assertEquals(14, sheet.initiativeScore)
    }

    @Test
    fun `la Percezione passiva e' dieci piu' il bonus di Percezione`() {
        val sheet = CharacterSheet(
            level = 1,
            abilityScores = mapOf(Ability.WISDOM to 14),
            skillProficiencies = mapOf(Skill.PERCEZIONE to Proficiency.PROFICIENT),
        )

        // 10 + 2 di Saggezza + 2 di competenza.
        assertEquals(14, sheet.passivePerception)
    }

    @Test
    fun `senza incantesimi non ci sono CD ne bonus d'attacco`() {
        val sheet = CharacterSheet()

        assertNull(sheet.spellSaveDc)
        assertNull(sheet.spellAttackBonus)
    }

    @Test
    fun `la CD dell'incantesimo e' otto piu' competenza piu' caratteristica`() {
        val sheet = CharacterSheet(
            level = 5,
            abilityScores = mapOf(Ability.INTELLIGENCE to 18),
            spellcasting = Spellcasting(ability = Ability.INTELLIGENCE),
        )

        // 8 + 3 di competenza + 4 di Intelligenza.
        assertEquals(15, sheet.spellSaveDc)
        assertEquals(7, sheet.spellAttackBonus)
    }

    @Test
    fun `un personaggio a zero PF e' privo di sensi ma non morto`() {
        val sheet = CharacterSheet(currentHitPoints = 0, maxHitPoints = 20)

        assertTrue(sheet.unconscious)
        assertFalse(sheet.dead)
    }

    @Test
    fun `tre fallimenti contro morte lo dichiarano morto`() {
        val sheet = CharacterSheet(currentHitPoints = 0, maxHitPoints = 20, deathSaveFailures = 3)

        assertTrue(sheet.dead)
        assertFalse(sheet.unconscious)
    }

    @Test
    fun `i Dadi Vita residui non scendono sotto zero`() {
        val sheet = CharacterSheet(hitDiceMax = 3, hitDiceSpent = 5)

        assertEquals(0, sheet.hitDiceRemaining)
    }

    @Test
    fun `la scheda produce la definizione da combattimento`() {
        val sheet = CharacterSheet(
            id = "pg-prova",
            characterName = "Prova",
            armorClass = 17,
            maxHitPoints = 30,
            currentHitPoints = 22,
            abilityScores = mapOf(Ability.DEXTERITY to 14, Ability.CONSTITUTION to 16),
            saveProficiencies = mapOf(Ability.CONSTITUTION to Proficiency.PROFICIENT),
            weapons = listOf(WeaponEntry("Spada", 5, 1, 8, 3, DamageType.SLASHING, 5)),
        )

        val definition = sheet.toActorDefinition()

        assertEquals("Prova", definition.name())
        assertEquals(17, definition.armorClass())
        assertEquals(30, definition.maxHitPoints())
        assertEquals(22, definition.currentHitPoints())
        assertEquals(2, definition.initiativeModifier())
        // +3 di Costituzione piu' +2 di competenza al livello 1.
        assertEquals(5, definition.constitutionSaveBonus())
        assertEquals(1, definition.abilities().size)
    }

    @Test
    fun `le armi senza nome non diventano capacita'`() {
        val sheet = CharacterSheet(weapons = listOf(WeaponEntry(), WeaponEntry("Arco", 6)))

        // Una riga vuota della tabella non deve generare un attacco fantasma.
        assertEquals(1, sheet.toActorDefinition().abilities().size)
    }

    @Test
    fun `un'arma configurata come azione bonus mantiene il costo nel combattimento`() {
        val sheet = CharacterSheet(
            weapons = listOf(WeaponEntry(name = "Pugnale rapido", bonusAction = true)),
        )

        assertEquals(
            ActivationCost.BONUS_ACTION,
            sheet.toActorDefinition().abilities().single().activationCost(),
        )
    }

    @Test
    fun `una capacita' ad area diventa un incantesimo con tiro salvezza e CD dell'incantatore`() {
        val sheet = CharacterSheet(
            id = "pg-mago",
            characterName = "Mago",
            level = 5,
            abilityScores = mapOf(Ability.INTELLIGENCE to 16, Ability.DEXTERITY to 14),
            spellcasting = Spellcasting(ability = Ability.INTELLIGENCE),
            weapons = listOf(
                WeaponEntry(
                    name = "Palla di Fuoco",
                    diceCount = 8,
                    diceSides = 6,
                    damageType = DamageType.FIRE,
                    rangeFeet = 150,
                    areaRadiusFeet = 20,
                    saveAbility = Ability.DEXTERITY,
                    halfOnSave = true,
                ),
            ),
        )

        val actor = sheet.toActorDefinition()
        val ability = actor.abilities().single()

        assertTrue(ability.isArea)
        assertEquals(20, ability.areaRadiusFeet())
        assertEquals(SaveAbility.DEXTERITY, ability.saveAbility())
        assertTrue(ability.halfOnSave())
        assertEquals(ResolutionMethod.SAVING_THROW, ability.resolutionMethod())
        // CD incantesimi = 8 + competenza(3 al 5° livello) + Intelligenza(+3) = 14.
        assertEquals(14, actor.spellSaveDc())
        // Il bonus al TS di Destrezza del mago (+2) è proiettato per i suoi tiri salvezza.
        assertEquals(2, actor.saveBonus(SaveAbility.DEXTERITY))
    }

    @Test
    fun `le abilita scelte dal catalogo entrano nel combattimento senza essere copiate nella scheda`() {
        val fireball = defaultAbilityCatalog().first { it.id == "inc-palla-di-fuoco" }
        val sheet = CharacterSheet(
            id = "pg-catalogo",
            characterName = "Incantatrice",
            level = 5,
            abilityScores = mapOf(Ability.INTELLIGENCE to 16),
            spellcasting = Spellcasting(ability = Ability.INTELLIGENCE),
            abilityIds = listOf(fireball.id),
        )

        val actor = sheet.toActorDefinition(abilityCatalog = listOf(fireball))
        val selected = actor.abilities().single()

        assertEquals(fireball.id, selected.id())
        assertEquals("Palla di Fuoco", selected.name())
        assertEquals(8, selected.damage().single().dice().count())
        assertEquals(20, selected.areaRadiusFeet())
        assertEquals(SaveAbility.DEXTERITY, selected.saveAbility())
    }

    @Test
    fun `un riferimento assente dal catalogo non crea una capacita fantasma`() {
        val sheet = CharacterSheet(abilityIds = listOf("abilita-rimossa"))

        assertTrue(sheet.toActorDefinition(abilityCatalog = emptyList()).abilities().isEmpty())
    }

    @Test
    fun `morso gelido conserva entrambe le componenti di danno`() {
        val bite = defaultAbilityCatalog().first { it.id == "nem-morso" }.toDefinition()

        assertEquals(2, bite.damage().size)
        assertEquals(DamageType.PIERCING, bite.damage()[0].type())
        assertEquals(DamageType.NECROTIC, bite.damage()[1].type())
        assertEquals(4, bite.damage()[1].dice().sides())
    }

    @Test
    fun `tutte le abilita iniziali hanno identificatori unici e producono definizioni valide`() {
        val catalog = defaultAbilityCatalog()

        assertEquals(catalog.size, catalog.map { it.id }.distinct().size)
        catalog.forEach { it.toDefinition() }
    }
}
