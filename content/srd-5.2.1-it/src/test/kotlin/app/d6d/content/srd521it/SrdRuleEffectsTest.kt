package app.d6d.content.srd521it

import app.d6d.rules.character.Ability
import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.ChoiceKind
import app.d6d.rules.character.ChoiceSelection
import app.d6d.rules.character.EffectTarget
import app.d6d.rules.character.ExperienceProgression
import app.d6d.rules.character.LevelUpRequest
import app.d6d.sheet.ArmorClassMethod
import app.d6d.sheet.CharacterSheet
import app.d6d.sheet.GuidedCharacterService
import app.d6d.sheet.WeaponEntry
import app.d6d.domain.combat.DamageType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Gli effetti dei privilegi devono arrivare alle statistiche, non restare testo.
 *
 * Sono verificati sui numeri che il tavolo usa davvero — Classe Armatura,
 * velocita', tiro per colpire — e sulla proiezione da combattimento, perche' e'
 * quella che il motore riceve.
 */
class SrdRuleEffectsTest {

    private val service = GuidedCharacterService(Srd521ItContent.pack)
    private val catalog = Srd521ItContent.catalog

    private fun draft(vararg scores: Pair<Ability, Int>) = CharacterSheet(
        abilityScores = Ability.entries.associateWith { 12 } + scores,
    )

    private fun advance(
        sheet: CharacterSheet,
        classId: CharacterClassId,
        preferences: List<String> = emptyList(),
    ): CharacterSheet {
        val level = sheet.progression.totalLevel + 1
        val current = sheet.copy(experiencePoints = ExperienceProgression.thresholdForLevel(level))
        var chosen = linkedMapOf<String, List<String>>()
        repeat(4) {
            val provisional = chosen.map { ChoiceSelection(it.key, it.value) }
            service.requirements(current, classId, provisional).forEach { choice ->
                if (chosen[choice.id].orEmpty().size in choice.minimumCount..choice.count) return@forEach
                val options = SrdChoiceResolver.options(
                    choice,
                    classId,
                    current.progression.levelIn(classId) + 1,
                    current,
                    chosen.map { ChoiceSelection(it.key, it.value) },
                )
                val backgroundPreference = if (choice.kind == ChoiceKind.BACKGROUND) {
                    options.filter { it.id == "srd521-it:background:soldato" }
                } else {
                    emptyList()
                }
                val wanted = backgroundPreference +
                    preferences.flatMap { fragment -> options.filter { fragment in it.id } }
                chosen[choice.id] = (wanted + options).map { it.id }.distinct().take(choice.count)
            }
        }
        val requirements = service.requirements(
            current,
            classId,
            chosen.map { ChoiceSelection(it.key, it.value) },
        )
        val selections = requirements.map { ChoiceSelection(it.id, chosen[it.id].orEmpty()) }
        val increases = if (
            selections.flatMap { it.optionIds }.any { it.endsWith(":aumento-punteggi-caratteristica") }
        ) {
            mapOf(Ability.CONSTITUTION to 2)
        } else {
            emptyMap()
        }
        val conditional = requirements
            .filter { it.kind == ChoiceKind.ABILITY_SCORE_INCREASE }
            .flatMap { requirement -> selections.first { it.choiceId == requirement.id }.optionIds }
        return service.advance(
            current,
            LevelUpRequest(
                classId,
                service.fixedHitPointIncrease(current, classId),
                true,
                selections,
                if (conditional.isEmpty()) increases else mapOf(Ability.STRENGTH to conditional.size),
            ),
        )
    }

    @Test
    fun `lo Stile Difesa aggiunge un punto di Classe Armatura solo con l'armatura addosso`() {
        val armored = advance(
            draft(Ability.STRENGTH to 16).copy(armorClassMethod = ArmorClassMethod.CHAIN_MAIL),
            CharacterClassId.FIGHTER,
            preferences = listOf("fighting-style:difesa"),
        )

        assertTrue(
            armored.progression.effects.any { it.source == "Difesa" },
            "lo stile scelto non ha lasciato il proprio effetto nella progressione",
        )
        assertEquals(1, armored.armorClassEffectBonus)
        // Cotta di maglia 16, nessuno scudo, piu' il punto dello stile.
        assertEquals(17, armored.effectiveArmorClass)
        assertEquals(17, armored.toActorDefinition().armorClass())

        // Senza armatura la condizione non e' soddisfatta e il punto non vale.
        val unarmored = armored.copy(armorClassMethod = ArmorClassMethod.UNARMORED)
        assertEquals(0, unarmored.armorClassEffectBonus)
    }

    @Test
    fun `lo Stile Tiro vale sui tiri a distanza e non su quelli in mischia`() {
        val archer = advance(
            draft(Ability.DEXTERITY to 16),
            CharacterClassId.FIGHTER,
            preferences = listOf("fighting-style:tiro"),
        ).copy(
            weapons = listOf(
                WeaponEntry("Arco lungo", attackBonus = 5, rangeFeet = 150, damageType = DamageType.PIERCING),
                WeaponEntry("Spada lunga", attackBonus = 5, rangeFeet = 5, damageType = DamageType.SLASHING),
            ),
        )

        assertEquals(2, archer.attackEffectBonus(archer.weapons[0]))
        assertEquals(0, archer.attackEffectBonus(archer.weapons[1]))

        val abilities = archer.toActorDefinition().abilities()
        assertEquals(7, abilities.first { it.name() == "Arco lungo" }.attackBonus())
        assertEquals(5, abilities.first { it.name() == "Spada lunga" }.attackBonus())
    }

    @Test
    fun `il Movimento senza armatura del monaco cresce a scaglioni senza sommarsi`() {
        var monk = draft(Ability.DEXTERITY to 16, Ability.WISDOM to 16)
        val attesi = mapOf(1 to 30, 2 to 40, 6 to 45, 10 to 50, 14 to 55, 18 to 60)
        repeat(18) {
            monk = advance(monk, CharacterClassId.MONK)
            attesi[monk.effectiveLevel]?.let { atteso ->
                assertEquals(
                    atteso,
                    monk.effectiveSpeedFeet,
                    "velocita' sbagliata al ${monk.effectiveLevel}º livello",
                )
            }
        }
        // Un solo effetto sulla velocita': gli scalini si sostituiscono.
        assertEquals(1, monk.progression.effects.count { it.target == EffectTarget.SPEED_FEET })
        assertEquals(60, monk.toActorDefinition().speedFeet())

        // Con l'armatura addosso il bonus decade; Forza 10 non soddisfa inoltre
        // il requisito 13 della cotta di maglia, quindi la velocità perde 3 metri.
        assertEquals(
            20,
            monk.copy(
                armorClassMethod = ArmorClassMethod.CHAIN_MAIL,
                abilityScores = monk.abilityScores + (Ability.STRENGTH to 10),
            ).effectiveSpeedFeet,
        )

        // Il +10 esposto dal catalogo per l'aggiunta manuale non deve abbassare
        // il +30 che la progressione guidata ha gia' raggiunto.
        val movementId = "srd521-it:feature:monaco:movimento-senza-armatura"
        val history = monk.progression.advancementHistory
        monk = service.withRefreshedEffects(monk, catalog)
        assertEquals(60, monk.effectiveSpeedFeet)
        assertEquals(
            30,
            monk.progression.effects.single { it.target == EffectTarget.SPEED_FEET }.amount,
        )
        assertEquals(history, monk.progression.advancementHistory)

        // L'overlay puo' escludere il privilegio e ripristinarlo senza riscrivere
        // alcun passaggio di livello.
        val excluded = service.withRefreshedEffects(
            monk.copy(excludedTraitIds = monk.excludedTraitIds + movementId),
            catalog,
        )
        assertEquals(30, excluded.effectiveSpeedFeet)
        assertEquals(history, excluded.progression.advancementHistory)

        val restored = service.withRefreshedEffects(
            excluded.copy(excludedTraitIds = excluded.excludedTraitIds - movementId),
            catalog,
        )
        assertEquals(60, restored.effectiveSpeedFeet)
        assertEquals(history, restored.progression.advancementHistory)
    }

    @Test
    fun `il Movimento veloce del barbaro arriva al quinto livello`() {
        var barbarian = draft(Ability.STRENGTH to 16, Ability.CONSTITUTION to 16)
        repeat(4) { barbarian = advance(barbarian, CharacterClassId.BARBARIAN) }
        assertEquals(30, barbarian.effectiveSpeedFeet)

        barbarian = advance(barbarian, CharacterClassId.BARBARIAN)

        assertEquals(40, barbarian.effectiveSpeedFeet)
        // L'armatura pesante lo annulla, il resto no.
        assertEquals(30, barbarian.copy(armorClassMethod = ArmorClassMethod.PLATE).effectiveSpeedFeet)
        assertEquals(40, barbarian.copy(armorClassMethod = ArmorClassMethod.LEATHER).effectiveSpeedFeet)
    }

    @Test
    fun `una scheda manuale applica e rimuove gli effetti dei privilegi di livello`() {
        val fastMovementId = "srd521-it:feature:barbaro:movimento-veloce"
        val unarmoredMovementId = "srd521-it:feature:monaco:movimento-senza-armatura"
        val extraAttackId = "srd521-it:feature:barbaro:attacco-extra"
        val catalogById = catalog.associateBy { it.id }

        assertEquals(
            10,
            catalogById.getValue(fastMovementId).effects.single {
                it.target == EffectTarget.SPEED_FEET
            }.amount,
        )
        assertEquals(
            10,
            catalogById.getValue(unarmoredMovementId).effects.single {
                it.target == EffectTarget.SPEED_FEET
            }.amount,
        )
        assertFalse(
            catalogById.getValue(extraAttackId).effects.any {
                it.target == EffectTarget.SPEED_FEET
            },
            "l'effetto del Movimento veloce non deve propagarsi agli altri privilegi del 5º livello",
        )

        val manual = draft().copy(
            abilityIds = listOf(fastMovementId, unarmoredMovementId),
        )
        val withTraits = service.withRefreshedEffects(manual, catalog)

        assertFalse(withTraits.progression.configured)
        assertEquals(50, withTraits.effectiveSpeedFeet)
        // La piastra spegne entrambi i bonus e, con Forza 10, riduce di altri 3 metri.
        assertEquals(20, withTraits.copy(armorClassMethod = ArmorClassMethod.PLATE).effectiveSpeedFeet)

        val withoutFastMovement = service.withRefreshedEffects(
            withTraits.copy(abilityIds = withTraits.abilityIds - fastMovementId),
            catalog,
        )
        assertEquals(40, withoutFastMovement.effectiveSpeedFeet)

        val withoutTraits = service.withRefreshedEffects(
            withoutFastMovement.copy(abilityIds = emptyList()),
            catalog,
        )
        assertEquals(30, withoutTraits.effectiveSpeedFeet)
        assertTrue(withoutTraits.progression.classLevels.isEmpty())
        assertTrue(withoutTraits.progression.advancementHistory.isEmpty())
    }
}
