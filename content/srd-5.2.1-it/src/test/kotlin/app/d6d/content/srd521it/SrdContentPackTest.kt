package app.d6d.content.srd521it

import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.CharacterProgression
import app.d6d.rules.character.ChoiceKind
import app.d6d.rules.character.ClassLevelState
import app.d6d.rules.character.RuleElementKind
import app.d6d.sheet.CharacterSheet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SrdContentPackTest {
    @Test
    fun `tutti i riferimenti di classe risolvono nel pacchetto`() {
        val pack = Srd521ItContent.pack
        val referenced = buildSet {
            pack.classes.forEach { definition ->
                addAll(definition.subclassIds)
                definition.levels.forEach { level ->
                    addAll(level.featureIds)
                    addAll(level.spellGrants.flatMap { it.spellIds })
                    level.choices.forEach { choice ->
                        addAll(
                            choice.optionIds.filter {
                                it.startsWith("srd521-it:feature:") ||
                                    it.startsWith("srd521-it:subclass:") ||
                                    it.startsWith("srd521-it:metamagic:") ||
                                    it.startsWith("srd521-it:feat:")
                            },
                        )
                    }
                }
            }
        }
        assertTrue(referenced.all { pack.element(it) != null })
    }

    @Test
    fun `il pacchetto aggrega classi privilegi talenti azioni e incantesimi`() {
        val pack = Srd521ItContent.pack
        assertEquals(12, pack.classes.size)
        assertEquals(339, pack.elements.count { it.spell != null })
        assertEquals(17, SrdFeatsAndActions.feats.size)
        assertEquals(12, pack.elements.count { it.kind == RuleElementKind.COMMON_ACTION })
        assertTrue(pack.elements.count { it.kind == RuleElementKind.CLASS_FEATURE } >= 170)
        assertEquals(10, pack.elements.count { it.kind == RuleElementKind.METAMAGIC })
        assertEquals(28, pack.elements.count { it.kind == RuleElementKind.ELDRITCH_INVOCATION })
    }

    @Test
    fun `manifesto conserva versione fonte e attribuzione`() {
        val manifest = Srd521ItContent.pack.manifest
        assertEquals("srd521-it", manifest.id)
        assertEquals("5.2.1", manifest.version)
        assertEquals("it-IT", manifest.locale)
        assertTrue("Wizards of the Coast LLC" in manifest.attribution)
        assertTrue("creativecommons.org/licenses/by/4.0" in manifest.attribution)
    }

    @Test
    fun `i privilegi collegati alle tabelle hanno testo SRD e pagina sorgente`() {
        val pack = Srd521ItContent.pack
        val referencedFeatures = pack.classes
            .flatMap { definition -> definition.levels.flatMap { it.featureIds } }
            .distinct()
            .map { checkNotNull(pack.element(it)) }
        val unresolved = referencedFeatures.filter {
            it.sourcePage == 0 ||
                it.description.startsWith("Privilegio di classe indicato nella tabella")
        }

        assertTrue(
            unresolved.isEmpty(),
            "Riferimenti senza testo SRD: ${unresolved.joinToString { it.id }}",
        )
    }

    @Test
    fun `le metamagie consumano il numero corretto di punti stregoneria`() {
        val metamagic = Srd521ItContent.pack.elements.filter {
            it.kind == RuleElementKind.METAMAGIC
        }
        assertEquals(10, metamagic.size)
        assertEquals(
            setOf("Incantesimo intensificato", "Incantesimo rapido"),
            metamagic.filter { it.resourceCost == 2 }.mapTo(mutableSetOf()) { it.name },
        )
        assertEquals(8, metamagic.count { it.resourceCost == 1 })
    }

    @Test
    fun `le suppliche rispettano livello e patto scelto`() {
        val warlock = Srd521ItContent.pack.classDefinition(CharacterClassId.WARLOCK)
        val levelOneChoice = warlock.level(1).choices.single {
            it.kind == ChoiceKind.ELDRITCH_INVOCATION
        }
        val levelOneIds = SrdChoiceResolver.options(
            levelOneChoice,
            CharacterClassId.WARLOCK,
            1,
            CharacterSheet(),
        ).mapTo(mutableSetOf()) { it.id }
        assertTrue("srd521-it:feature:warlock:conoscenze-degli-antichi" !in levelOneIds)

        val levelFiveChoice = warlock.level(5).choices.single {
            it.kind == ChoiceKind.ELDRITCH_INVOCATION
        }
        val withoutPact = CharacterSheet(
            progression = CharacterProgression(
                classLevels = listOf(ClassLevelState(CharacterClassId.WARLOCK, 4)),
            ),
        )
        val withoutPactIds = SrdChoiceResolver.options(
            levelFiveChoice,
            CharacterClassId.WARLOCK,
            5,
            withoutPact,
        ).mapTo(mutableSetOf()) { it.id }
        assertTrue("srd521-it:feature:warlock:lama-assetata" !in withoutPactIds)

        val withBladePact = withoutPact.copy(
            progression = withoutPact.progression.copy(
                selectedFeatureIds = listOf("srd521-it:feature:warlock:patto-della-lama"),
            ),
        )
        val withPactIds = SrdChoiceResolver.options(
            levelFiveChoice,
            CharacterClassId.WARLOCK,
            5,
            withBladePact,
        ).mapTo(mutableSetOf()) { it.id }
        assertTrue("srd521-it:feature:warlock:lama-assetata" in withPactIds)
    }
}
