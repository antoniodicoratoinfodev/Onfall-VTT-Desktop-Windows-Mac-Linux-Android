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
import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.CharacterProgression
import app.d6d.rules.character.ClassLevelState
import app.d6d.rules.character.RuleElementKind

/**
 * Valori derivati della scheda.
 *
 * Sono regole, non presentazione: qui si verifica che la scheda calcoli come il
 * regolamento e non come farebbe l'aritmetica ingenua.
 */
class SheetDerivationsTest {

    @Test
    fun `gli slot incantesimo diventano risorse leggibili nella tab di battaglia`() {
        val actor = CharacterSheet(
            spellcasting = Spellcasting(
                slots = (1..9).map { level ->
                    when (level) {
                        1 -> SpellSlot(level, total = 4, spent = 1)
                        2 -> SpellSlot(level, total = 3, spent = 3)
                        else -> SpellSlot(level)
                    }
                },
                pactSlots = SpellSlot(level = 2, total = 2, spent = 1),
            ),
        ).toActorDefinition()

        val standardFirst = actor.resources().single {
            it.id() == "${SPELL_SLOT_RESOURCE_PREFIX}1"
        }
        val standardSecond = actor.resources().single {
            it.id() == "${SPELL_SLOT_RESOURCE_PREFIX}2"
        }
        val pactSecond = actor.resources().single {
            it.id() == "${PACT_SLOT_RESOURCE_PREFIX}2"
        }
        assertEquals(3, standardFirst.remaining())
        assertEquals(0, standardSecond.remaining())
        assertEquals(1, pactSecond.remaining())
        assertEquals(3, actor.resources().size)
    }

    @Test
    fun `attacco extra segue il livello di classe senza sommarsi in multiclasse`() {
        val multiclass = CharacterSheet(
            progression = CharacterProgression(
                classLevels = listOf(
                    ClassLevelState(CharacterClassId.FIGHTER, 5),
                    ClassLevelState(CharacterClassId.BARBARIAN, 5),
                ),
            ),
        )
        assertEquals(2, multiclass.attacksPerAction)
        assertEquals(
            3,
            multiclass.copy(
                progression = CharacterProgression(
                    classLevels = listOf(ClassLevelState(CharacterClassId.FIGHTER, 11)),
                ),
            ).attacksPerAction,
        )
        assertEquals(
            4,
            multiclass.copy(
                progression = CharacterProgression(
                    classLevels = listOf(ClassLevelState(CharacterClassId.FIGHTER, 20)),
                ),
            ).attacksPerAction,
        )
    }

    @Test
    fun `escludere o aggiungere Attacco extra dalla scheda aggiorna gli attacchi`() {
        val extraAttackId = "srd521-it:feature:guerriero:attacco-extra"
        val fighter = CharacterSheet(
            progression = CharacterProgression(
                classLevels = listOf(ClassLevelState(CharacterClassId.FIGHTER, 5)),
                selectedFeatureIds = listOf(extraAttackId),
            ),
            abilityIds = listOf(extraAttackId),
        )

        assertEquals(2, fighter.attacksPerAction)
        assertEquals(
            1,
            fighter.copy(
                abilityIds = emptyList(),
                excludedTraitIds = setOf(extraAttackId),
            ).attacksPerAction,
        )
        assertEquals(
            2,
            CharacterSheet(
                abilityIds = listOf("srd521-it:feature:barbaro:attacco-extra"),
            ).attacksPerAction,
        )
    }

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
    fun `la CA base segue il solo metodo scelto e reagisce alla Destrezza`() {
        val dexterity = mapOf(Ability.DEXTERITY to 18)

        assertEquals(
            14,
            CharacterSheet(
                armorClassMethod = ArmorClassMethod.UNARMORED,
                abilityScores = dexterity,
            ).baseArmorClass,
        )
        assertEquals(
            16,
            CharacterSheet(
                armorClassMethod = ArmorClassMethod.STUDDED_LEATHER,
                abilityScores = dexterity,
            ).baseArmorClass,
        )
        // Le armature medie accettano al massimo +2, non tutto il +4.
        assertEquals(
            17,
            CharacterSheet(
                armorClassMethod = ArmorClassMethod.HALF_PLATE,
                abilityScores = dexterity,
            ).baseArmorClass,
        )
        // Le armature pesanti ignorano interamente la Destrezza.
        assertEquals(
            16,
            CharacterSheet(
                armorClassMethod = ArmorClassMethod.CHAIN_MAIL,
                abilityScores = dexterity,
            ).baseArmorClass,
        )
    }

    @Test
    fun `una CA media conserva una penalita di Destrezza`() {
        val sheet = CharacterSheet(
            armorClassMethod = ArmorClassMethod.HALF_PLATE,
            abilityScores = mapOf(Ability.DEXTERITY to 8),
        )

        // "Massimo +2" limita soltanto il bonus: il −1 continua ad applicarsi.
        assertEquals(14, sheet.baseArmorClass)
    }

    @Test
    fun `armatura senza competenza impone le limitazioni regolamentari`() {
        val sheet = CharacterSheet(
            armorClassMethod = ArmorClassMethod.PLATE,
            armorTraining = ArmorTraining(),
            abilityScores = mapOf(
                Ability.STRENGTH to 12,
                Ability.DEXTERITY to 14,
                Ability.INTELLIGENCE to 16,
            ),
            speedFeet = 30,
            spellcasting = Spellcasting(ability = Ability.INTELLIGENCE),
        )

        assertTrue(sheet.wearingArmorWithoutTraining)
        assertTrue(sheet.strengthDexterityD20Disadvantage)
        assertTrue(sheet.hasDisadvantageOnSave(Ability.STRENGTH))
        assertTrue(sheet.hasDisadvantageOnSave(Ability.DEXTERITY))
        assertFalse(sheet.hasDisadvantageOnSave(Ability.INTELLIGENCE))
        assertTrue(sheet.hasDisadvantageOnSkill(Skill.FURTIVITA))
        assertTrue(sheet.spellcastingBlockedByArmor)
        assertTrue(sheet.armorStrengthRequirementNotMet)
        assertEquals(10, sheet.armorSpeedPenaltyFeet)
        assertEquals(20, sheet.effectiveSpeedFeet)
        assertTrue(sheet.toActorDefinition().strengthDexterityD20Disadvantage())

        val trained = sheet.copy(armorTraining = ArmorTraining(heavy = true))
        assertFalse(trained.wearingArmorWithoutTraining)
        assertFalse(trained.spellcastingBlockedByArmor)
        // La competenza elimina le penalità da addestramento, non il requisito di Forza.
        assertEquals(20, trained.effectiveSpeedFeet)
        assertTrue(trained.hasDisadvantageOnSkill(Skill.FURTIVITA))
    }

    @Test
    fun `le armature applicano categoria requisito di Forza e Furtivita`() {
        assertEquals(ArmorCategory.LIGHT, ArmorClassMethod.LEATHER.armorCategory)
        assertEquals(ArmorCategory.MEDIUM, ArmorClassMethod.HALF_PLATE.armorCategory)
        assertEquals(ArmorCategory.HEAVY, ArmorClassMethod.CHAIN_MAIL.armorCategory)
        assertEquals(13, ArmorClassMethod.CHAIN_MAIL.minimumStrength)
        assertEquals(15, ArmorClassMethod.SPLINT.minimumStrength)
        assertTrue(ArmorClassMethod.PADDED.stealthDisadvantage)
        assertTrue(ArmorClassMethod.SCALE_MAIL.stealthDisadvantage)
        assertFalse(ArmorClassMethod.BREASTPLATE.stealthDisadvantage)
    }

    @Test
    fun `CA libera conserva categoria requisito Furtivita e tempi dell armatura`() {
        val sheet = CharacterSheet(
            armorClass = 17,
            armorClassMethod = ArmorClassMethod.MANUAL_TOTAL,
            manualArmorCategory = ArmorCategory.HEAVY,
            manualArmorMinimumStrength = 15,
            manualArmorStealthDisadvantage = true,
            abilityScores = mapOf(Ability.STRENGTH to 12),
        )

        assertEquals(ArmorCategory.HEAVY, sheet.wornArmorCategory)
        assertTrue(sheet.wearingArmorWithoutTraining)
        assertTrue(sheet.armorStrengthRequirementNotMet)
        assertTrue(sheet.armorStealthDisadvantage)
        assertEquals(10, sheet.armorDonMinutes)
        assertEquals(5, sheet.armorDoffMinutes)

        val noArmor = sheet.copy(manualArmorCategory = null)
        assertFalse(noArmor.wearingArmorWithoutTraining)
        assertFalse(noArmor.armorStrengthRequirementNotMet)
        assertFalse(noArmor.armorStealthDisadvantage)
    }

    @Test
    fun `mithral e giaco elfico applicano le eccezioni SRD`() {
        val mithral = CharacterSheet(
            armorClassMethod = ArmorClassMethod.PLATE,
            armorSpecialRule = ArmorSpecialRule.MITHRAL,
            abilityScores = mapOf(Ability.STRENGTH to 8),
            armorTraining = ArmorTraining(heavy = true),
        )
        assertEquals(0, mithral.effectiveArmorMinimumStrength)
        assertFalse(mithral.armorStrengthRequirementNotMet)
        assertFalse(mithral.armorStealthDisadvantage)
        assertEquals(30, mithral.effectiveSpeedFeet)

        val elvenChain = CharacterSheet(
            armorClassMethod = ArmorClassMethod.CHAIN_SHIRT,
            armorSpecialRule = ArmorSpecialRule.ELVEN_CHAIN,
            armorTraining = ArmorTraining(),
        )
        assertFalse(elvenChain.wearingArmorWithoutTraining)
        assertEquals(1, elvenChain.armorSpecialArmorClassBonus)
        assertEquals(14, elvenChain.calculatedArmorClass)

        val invalid = elvenChain.copy(armorClassMethod = ArmorClassMethod.LEATHER)
        assertEquals(ArmorSpecialRule.STANDARD, invalid.effectiveArmorSpecialRule)
        assertTrue(invalid.wearingArmorWithoutTraining)
        assertEquals(0, invalid.armorSpecialArmorClassBonus)
    }

    @Test
    fun `scudo e modificatori attivi compongono la CA calcolata`() {
        val adjustments = listOf(
            ArmorClassAdjustment("Stile di difesa", 1, active = true),
            ArmorClassAdjustment("Maledizione", -2, active = true),
            ArmorClassAdjustment("Scudo della fede", 5, active = false),
        )
        val trained = CharacterSheet(
            armorClassMethod = ArmorClassMethod.CHAIN_MAIL,
            shieldEquipped = true,
            armorTraining = ArmorTraining(shields = true),
            armorClassAdjustments = adjustments,
        )
        val untrained = trained.copy(armorTraining = ArmorTraining(shields = false))

        assertEquals(2, trained.shieldArmorClassBonus)
        assertEquals(17, trained.calculatedArmorClass)
        // Senza competenza lo scudo resta registrato come equipaggiato, ma non
        // concede il proprio +2.
        assertEquals(0, untrained.shieldArmorClassBonus)
        assertEquals(15, untrained.calculatedArmorClass)
    }

    @Test
    fun `la CA finale manuale non somma di nuovo scudo e modificatori`() {
        val sheet = CharacterSheet(
            armorClass = 18,
            armorClassMethod = ArmorClassMethod.MANUAL_TOTAL,
            shieldEquipped = true,
            armorTraining = ArmorTraining(shields = true),
            armorClassAdjustments = listOf(ArmorClassAdjustment("Anello", 2)),
        )

        assertEquals(18, sheet.baseArmorClass)
        assertEquals(0, sheet.armorClassAdjustmentTotal)
        assertEquals(18, sheet.effectiveArmorClass)
    }

    @Test
    fun `un override sostituisce il totale senza cancellare la formula`() {
        val calculated = CharacterSheet(
            armorClassMethod = ArmorClassMethod.CHAIN_MAIL,
            shieldEquipped = true,
            armorTraining = ArmorTraining(shields = true),
        )
        val overridden = calculated.copy(armorClassOverride = 21)

        assertEquals(18, overridden.calculatedArmorClass)
        assertEquals(21, overridden.effectiveArmorClass)
        assertEquals(18, overridden.copy(armorClassOverride = null).effectiveArmorClass)
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
    fun `la definizione da combattimento usa la CA derivata`() {
        val sheet = CharacterSheet(
            armorClassMethod = ArmorClassMethod.STUDDED_LEATHER,
            abilityScores = mapOf(Ability.DEXTERITY to 16),
            shieldEquipped = true,
            armorTraining = ArmorTraining(shields = true),
            armorClassAdjustments = listOf(ArmorClassAdjustment("Anello", 1)),
        )

        // 12 + 3 Des + 2 scudo + 1 anello.
        assertEquals(18, sheet.effectiveArmorClass)
        assertEquals(18, sheet.toActorDefinition().armorClass())
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
    fun `il blocco magico riconosce incantesimi SRD e personalizzati`() {
        val fireball = defaultAbilityCatalog()
            .first { it.id == "inc-palla-di-fuoco" }
            .copy(category = RuleElementKind.SPELL)
        val customSpell = CatalogAbility(
            id = "custom-spell",
            name = "Raggio personale",
            category = RuleElementKind.CUSTOM,
            spellOrCantrip = true,
            attackAbility = Ability.INTELLIGENCE,
        )
        val sword = defaultAbilityCatalog().first { it.id == "arma-spadone" }
        val sheet = CharacterSheet(
            armorClassMethod = ArmorClassMethod.CHAIN_MAIL,
            armorTraining = ArmorTraining(),
            abilityIds = listOf(fireball.id, customSpell.id, sword.id),
            spellcasting = Spellcasting(ability = Ability.INTELLIGENCE),
            weapons = listOf(
                WeaponEntry(
                    name = "Trucchetto privato",
                    spellOrCantrip = true,
                    attackAbility = Ability.CHARISMA,
                ),
            ),
        )

        val catalog = listOf(fireball, customSpell, sword)
        val blocked = sheet.toActorDefinition(abilityCatalog = catalog)
        assertEquals(listOf(sword.id), blocked.abilities().map { it.id() })

        val trained = sheet.copy(armorTraining = ArmorTraining(heavy = true))
            .toActorDefinition(abilityCatalog = catalog)
        assertEquals(
            setOf("${sheet.id}-arma-0", fireball.id, customSpell.id, sword.id),
            trained.abilities().map { it.id() }.toSet(),
        )
        assertTrue(trained.ability(customSpell.id).spellOrCantrip())
        assertEquals(SaveAbility.INTELLIGENCE, trained.ability(customSpell.id).attackAbility())
        assertEquals(
            SaveAbility.CHARISMA,
            trained.ability("${sheet.id}-arma-0").attackAbility(),
        )
    }

    @Test
    fun `una vecchia riga ambigua non aggira le restrizioni dell armatura`() {
        val legacyWeapon = WeaponEntry(
            name = "Voce importata",
            legacyClassificationRequired = true,
        )
        val sheet = CharacterSheet(
            armorClassMethod = ArmorClassMethod.CHAIN_MAIL,
            armorTraining = ArmorTraining(),
            weapons = listOf(legacyWeapon),
        )

        assertTrue(sheet.toActorDefinition().abilities().isEmpty())

        val trained = sheet.copy(armorTraining = ArmorTraining(heavy = true))
            .toActorDefinition()
        assertEquals(listOf("${sheet.id}-arma-0"), trained.abilities().map { it.id() })

        val classified = sheet.copy(
            weapons = listOf(
                legacyWeapon.copy(
                    attackAbility = Ability.STRENGTH,
                    legacyClassificationRequired = false,
                ),
            ),
        ).toActorDefinition()
        assertEquals(SaveAbility.STRENGTH, classified.abilities().single().attackAbility())
        assertTrue(classified.strengthDexterityD20Disadvantage())
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
