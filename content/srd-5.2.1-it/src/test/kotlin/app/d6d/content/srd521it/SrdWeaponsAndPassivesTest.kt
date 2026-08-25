package app.d6d.content.srd521it

import app.d6d.i18n.AppLanguage
import app.d6d.sheet.i18n.damageText
import app.d6d.rules.character.Ability
import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.ChoiceKind
import app.d6d.rules.character.ChoiceSelection
import app.d6d.rules.character.CharacterProgression
import app.d6d.rules.character.ClassLevelState
import app.d6d.rules.character.ExperienceProgression
import app.d6d.rules.character.LevelUpRequest
import app.d6d.rules.character.RecoveryPeriod
import app.d6d.rules.character.ResourcePoolState
import app.d6d.rules.character.RuleElementKind
import app.d6d.rules.character.WeaponCategory
import app.d6d.rules.character.WeaponProperty
import app.d6d.rules.character.WeaponReach
import app.d6d.domain.combat.AbilityEffect
import app.d6d.domain.combat.ActivationCost
import app.d6d.domain.combat.AutomationStatus
import app.d6d.domain.combat.ResolutionMethod
import app.d6d.sheet.ArmorClassMethod
import app.d6d.sheet.CharacterSheet
import app.d6d.sheet.GuidedCharacterService
import app.d6d.sheet.PACT_SLOT_RESOURCE_PREFIX
import app.d6d.sheet.SpellSlot
import app.d6d.sheet.Spellcasting
import app.d6d.sheet.toWeaponEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Copre la separazione fra tratti permanenti e capacità giocabili e la tabella
 * Armi che alimenta la creazione guidata.
 */
class SrdWeaponsAndPassivesTest {
    private val pack = Srd521ItContent.pack
    private val service = GuidedCharacterService(pack)
    private val catalog = Srd521ItContent.catalog.associateBy { it.id }

    @Test
    fun `la tabella armi rispetta i quattro gruppi dello SRD`() {
        val bucket = SrdWeapons.all().groupingBy { it.category to it.reach }.eachCount()

        assertEquals(38, SrdWeapons.all().size)
        assertEquals(10, bucket[WeaponCategory.SIMPLE to WeaponReach.MELEE])
        assertEquals(4, bucket[WeaponCategory.SIMPLE to WeaponReach.RANGED])
        assertEquals(18, bucket[WeaponCategory.MARTIAL to WeaponReach.MELEE])
        assertEquals(6, bucket[WeaponCategory.MARTIAL to WeaponReach.RANGED])
        assertEquals(
            SrdWeapons.all().size,
            SrdWeapons.all().mapTo(mutableSetOf()) { it.id }.size,
            "Gli identificatori delle armi devono essere unici.",
        )
    }

    @Test
    fun `il martello da guerra riporta i valori della tabella ufficiale`() {
        val warhammer = requireNotNull(SrdWeapons.byId("srd521-it:weapon:martello-da-guerra"))

        assertEquals("Martello da guerra", warhammer.name)
        assertEquals(1, warhammer.diceCount)
        assertEquals(8, warhammer.diceSides)
        assertEquals("Spinta", warhammer.mastery)
        assertEquals(setOf(WeaponProperty.VERSATILE), warhammer.properties)
        assertEquals(10, warhammer.versatileDiceSides)
        assertEquals(5, warhammer.attackRangeFeet)
    }

    @Test
    fun `anche le armi da lancio mostrano la gittata nelle due lingue`() {
        val spear = requireNotNull(SrdWeapons.byId("srd521-it:weapon:lancia"))
        val englishSpear = requireNotNull(
            SrdWeapons.byId("srd521-it:weapon:lancia", AppLanguage.ENGLISH),
        )

        assertTrue("gittata 6/18 m" in spear.summary(AppLanguage.ITALIAN))
        assertTrue("range 20/60 ft" in englishSpear.summary(AppLanguage.ENGLISH))
    }

    @Test
    fun `la cerbottana usa davvero un danno fisso e non un dado inesistente`() {
        val blowgun = requireNotNull(SrdWeapons.byId("srd521-it:weapon:cerbottana"))
        assertEquals(1, blowgun.fixedDamage)
        assertEquals("1 perforante", blowgun.damageText(AppLanguage.ITALIAN))

        val entry = blowgun.toWeaponEntry(
            abilityScores = Ability.entries.associateWith {
                if (it == Ability.DEXTERITY) 16 else 10
            },
            proficiencyBonus = 2,
        )
        assertEquals(1, entry.fixedDamage)
        assertEquals("1+3 perforante", entry.damageText(AppLanguage.ITALIAN))

        val formula = CharacterSheet(weapons = listOf(entry))
            .toActorDefinition()
            .abilities()
            .single()
            .damage()
            .single()
        assertFalse(formula.usesDice())
        assertEquals(4, formula.fixedAmount())
    }

    @Test
    fun `l'addestramento della classe filtra le armi proposte`() {
        fun trained(classId: CharacterClassId) =
            SrdWeapons.trainedBy(pack.classDefinition(classId).weaponTrainingGrant)

        // Il mago si ferma alle armi semplici; il guerriero le impugna tutte.
        assertEquals(14, trained(CharacterClassId.WIZARD).size)
        assertEquals(38, trained(CharacterClassId.FIGHTER).size)

        // Ladro e monaco aggiungono solo le armi da guerra con certe proprietà.
        val rogue = trained(CharacterClassId.ROGUE)
        assertTrue(
            rogue.filter { it.category == WeaponCategory.MARTIAL }.all {
                WeaponProperty.FINESSE in it.properties || WeaponProperty.LIGHT in it.properties
            },
            "Il ladro riceve solo armi da guerra accurate o leggere.",
        )
        assertTrue(rogue.any { it.name == "Stocco" })
        assertFalse(rogue.any { it.name == "Spadone" })

        val monk = trained(CharacterClassId.MONK)
        assertTrue(
            monk.filter { it.category == WeaponCategory.MARTIAL }.all {
                WeaponProperty.LIGHT in it.properties
            },
            "Il monaco riceve solo armi da guerra leggere.",
        )
        assertFalse(monk.any { it.name == "Stocco" })
    }

    @Test
    fun `padronanza d'armi e talenti sono tratti permanenti, gli incantesimi restano giocabili`() {
        fun passive(id: String) = requireNotNull(catalog[id]) { "Elemento assente: $id." }.passive

        assertTrue(passive("srd521-it:feat:origin:abile"), "Un talento è sempre passivo.")
        assertTrue(passive("srd521-it:feat:origin:allerta"), "Anche i talenti con innesco.")
        assertTrue(
            passive("srd521-it:feat:general:aumento-punteggi-caratteristica"),
            "L'aumento dei punteggi non si attiva nel turno.",
        )

        val weaponMastery = catalog.values.filter { it.name == "Padronanza d'armi" }
        assertTrue(weaponMastery.isNotEmpty(), "Il privilegio Padronanza d'armi deve esistere.")
        assertTrue(weaponMastery.all { it.passive }, "Padronanza d'armi è un tratto permanente.")

        val spellcasting = catalog.values.filter { it.name == "Incantesimi" }
        assertTrue(spellcasting.isNotEmpty(), "Il privilegio Incantesimi deve esistere.")
        assertTrue(spellcasting.all { it.passive }, "Il privilegio Incantesimi non si attiva.")

        // Il contraltare: quello che si gioca nel turno resta fra le capacità.
        assertTrue(
            catalog.values.filter { it.category == RuleElementKind.SPELL }.none { it.passive },
            "Nessun incantesimo può finire fra i tratti permanenti.",
        )
        assertTrue(
            catalog.values.filter { it.category == RuleElementKind.CANTRIP }.none { it.passive },
            "Nessun trucchetto può finire fra i tratti permanenti.",
        )
        // Magia e Attacco dichiarano soltanto che il personaggio sa lanciare o
        // combattere: a risolvere sono i singoli incantesimi e le singole armi,
        // che restano schede premibili. Le altre dieci azioni si giocano.
        val markers = setOf("srd521-it:action:magia", "srd521-it:action:attacco")
        val commonActions = catalog.values.filter { it.category == RuleElementKind.COMMON_ACTION }
        assertEquals(12, commonActions.size, "Lo SRD elenca dodici azioni comuni.")
        assertTrue(
            commonActions.filterNot { it.id in markers }.none { it.passive },
            "Le altre azioni comuni restano giocabili.",
        )
        markers.forEach { id ->
            assertTrue(
                requireNotNull(catalog[id]) { "Azione assente: $id." }.passive,
                "«$id» è un'indicazione, non un comando.",
            )
        }
        val rage = catalog.values.single { it.id == "srd521-it:feature:barbaro:ira" }
        assertFalse(rage.passive, "L'Ira costa un'azione bonus, quindi si gioca.")
    }

    @Test
    fun `Azione impetuosa e una capacita attiva automatica legata alla propria risorsa`() {
        val surge = requireNotNull(catalog["srd521-it:feature:guerriero:azione-impetuosa"])

        assertFalse(surge.passive)
        assertEquals(ActivationCost.NONE, surge.activationCost)
        assertEquals(ResolutionMethod.AUTOMATIC, surge.resolutionMethod)
        assertEquals(AutomationStatus.AUTOMATED, surge.automationStatus)
        assertEquals(AbilityEffect.GRANT_NON_MAGIC_ACTION, surge.effect)
        assertEquals("srd521-it:resource:guerriero:azione-impetuosa", surge.resourceId)
        assertEquals(1, surge.resourceCost)
    }

    @Test
    fun `Azione impetuosa arriva dalla progressione alla definizione di combattimento`() {
        val draft = CharacterSheet(abilityScores = Ability.entries.associateWith { 12 })
        val firstLevel = service.advance(
            draft,
            LevelUpRequest(
                CharacterClassId.FIGHTER,
                service.fixedHitPointIncrease(draft, CharacterClassId.FIGHTER),
                true,
                selectionsFor(draft, CharacterClassId.FIGHTER),
            ),
        ).copy(experiencePoints = ExperienceProgression.thresholdForLevel(2))
        val secondLevel = service.advance(
            firstLevel,
            LevelUpRequest(
                CharacterClassId.FIGHTER,
                service.fixedHitPointIncrease(firstLevel, CharacterClassId.FIGHTER),
                true,
                selectionsFor(firstLevel, CharacterClassId.FIGHTER),
            ),
        )

        val actor = secondLevel.toActorDefinition(abilityCatalog = Srd521ItContent.catalog)
        val surge = actor.ability("srd521-it:feature:guerriero:azione-impetuosa")
        val resource = actor.resources().single {
            it.id() == "srd521-it:resource:guerriero:azione-impetuosa"
        }

        assertEquals(AbilityEffect.GRANT_NON_MAGIC_ACTION, surge.effect())
        assertEquals(resource.id(), surge.resourceId())
        assertEquals(1, surge.resourceCost())
        assertEquals(1, resource.maximum())
        assertEquals(0, resource.spent())
    }

    @Test
    fun `le capacita del warlock consumano lo slot del patto mostrato in combattimento`() {
        val mirrorId = "srd521-it:resource:warlock:slot-magia-del-patto"
        val smiteId = "srd521-it:feature:warlock:punizione-occulta"
        val sheet = CharacterSheet(
            progression = CharacterProgression(
                classLevels = listOf(ClassLevelState(CharacterClassId.WARLOCK, 5)),
                selectedFeatureIds = listOf(smiteId),
                resourcePools = listOf(
                    ResourcePoolState(
                        resourceId = mirrorId,
                        name = "Slot di Magia del patto",
                        maximum = 2,
                        recovery = RecoveryPeriod.SHORT_OR_LONG_REST,
                    ),
                ),
            ),
            spellcasting = Spellcasting(
                pactSlots = SpellSlot(level = 3, total = 2),
            ),
            abilityIds = listOf(smiteId),
        )

        val actor = sheet.toActorDefinition(abilityCatalog = Srd521ItContent.catalog)
        val pactId = "${PACT_SLOT_RESOURCE_PREFIX}3"

        assertEquals(pactId, actor.ability(smiteId).resourceId())
        assertEquals(1, actor.ability(smiteId).resourceCost())
        assertEquals(listOf(pactId), actor.resources().map { it.id() })
        assertEquals(2, actor.resources().single().remaining())
    }

    @Test
    fun `la creazione guidata applica la dotazione ufficiale e rende le armi capacita' da combattimento`() {
        val draft = CharacterSheet(
            abilityScores = Ability.entries.associateWith {
                if (it == Ability.STRENGTH) 16 else 10
            },
        )
        val selections = selectionsFor(draft, CharacterClassId.FIGHTER)
            .map { selection ->
                if (selection.choiceId == "srd521-it:choice:class:guerriero:equipment") {
                    ChoiceSelection(
                        selection.choiceId,
                        listOf("srd521-it:equipment:class:guerriero:a"),
                    )
                } else {
                    selection
                }
            }
        val validation = service.validate(
            draft,
            LevelUpRequest(
                CharacterClassId.FIGHTER,
                service.fixedHitPointIncrease(draft, CharacterClassId.FIGHTER),
                true,
                selections,
            ),
        )
        assertTrue(
            validation.valid,
            validation.issues.joinToString(" | ") { "${it.code}: ${it.message}" },
        )

        val fighter = service.advance(
            draft,
            LevelUpRequest(
                CharacterClassId.FIGHTER,
                service.fixedHitPointIncrease(draft, CharacterClassId.FIGHTER),
                true,
                selections,
            ),
        )

        val sword = fighter.weapons.single { it.name == "Spadone" }
        // Forza 16 da +3, il 1º livello da +2 di competenza.
        assertEquals(5, sword.attackBonus)
        assertEquals(3, sword.damageModifier)
        assertEquals(2, sword.diceCount)
        assertEquals(6, sword.diceSides)
        assertEquals(5, sword.rangeFeet)
        assertTrue(sword.note.contains("Colpo di striscio"), "La nota riporta la Padronanza.")

        // Il giavellotto si usa in mischia: la gittata di lancio resta annotata, non
        // diventa la portata dell'attacco.
        val javelin = fighter.weapons.single { it.name == "Giavellotto" }
        assertEquals(5, javelin.rangeFeet)
        assertTrue(javelin.note.contains("gittata 9/36 m"), "L'arma da lancio riporta la gittata.")
        assertEquals(ArmorClassMethod.CHAIN_MAIL, fighter.armorClassMethod)
        assertEquals(18, fighter.money.gold)

        val combat = fighter.toActorDefinition(abilityCatalog = Srd521ItContent.catalog)
        val playable = combat.abilities().filterNot { it.passive() }
        assertTrue(
            playable.any { it.name() == "Spadone" } && playable.any { it.name() == "Giavellotto" },
            "Le armi equipaggiate devono comparire fra le capacità giocabili.",
        )
        assertTrue(
            combat.abilities().any { it.passive() },
            "La scheda porta in combattimento anche i propri tratti permanenti.",
        )
        assertNotNull(
            combat.abilities().firstOrNull { it.name() == "Padronanza d'armi" }?.takeIf { it.passive() },
            "Padronanza d'armi arriva in combattimento come tratto permanente.",
        )
    }

    @Test
    fun `l'azione di Magia arriva solo a chi sa lanciare incantesimi`() {
        fun magicAction(classId: CharacterClassId): Boolean {
            val draft = CharacterSheet(abilityScores = Ability.entries.associateWith { 14 })
            val built = service.advance(
                draft,
                LevelUpRequest(
                    classId,
                    service.fixedHitPointIncrease(draft, classId),
                    true,
                    selectionsFor(draft, classId),
                ),
            )
            return "srd521-it:action:magia" in built.abilityIds
        }

        assertTrue(magicAction(CharacterClassId.BARD), "Il bardo lancia incantesimi dal 1º livello.")
        assertFalse(
            magicAction(CharacterClassId.BARBARIAN),
            "Il barbaro non lancia nulla: l'azione di Magia non gli dice niente.",
        )
    }

    private fun selectionsFor(
        sheet: CharacterSheet,
        classId: CharacterClassId,
    ): List<ChoiceSelection> {
        val classLevel = sheet.progression.levelIn(classId) + 1
        var selected = linkedMapOf<String, List<String>>()
        repeat(4) {
            val requirements = service.requirements(
                sheet,
                classId,
                selected.map { ChoiceSelection(it.key, it.value) },
            )
            val currentIds = requirements.mapTo(mutableSetOf()) { it.id }
            selected = selected.filterKeys { it in currentIds }.toMap(LinkedHashMap())
            requirements.forEach { choice ->
                val options = SrdChoiceResolver.options(
                    choice,
                    classId,
                    classLevel,
                    sheet,
                    selected.map { ChoiceSelection(it.key, it.value) },
                )
                selected[choice.id] = if (choice.kind == ChoiceKind.BACKGROUND) {
                    listOf("srd521-it:background:soldato")
                } else {
                    options.take(choice.count).map { it.id }
                }
            }
        }
        return service
            .requirements(sheet, classId, selected.map { ChoiceSelection(it.key, it.value) })
            .map { ChoiceSelection(it.id, selected[it.id].orEmpty()) }
    }
}
