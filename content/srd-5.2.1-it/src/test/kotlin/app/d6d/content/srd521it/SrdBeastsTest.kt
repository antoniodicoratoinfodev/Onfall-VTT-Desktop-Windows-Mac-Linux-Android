package app.d6d.content.srd521it

import app.d6d.rules.character.CharacterClassId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SrdBeastsTest {

    @Test
    fun `il catalogo contiene tutte e sole le bestie SRD con GS massimo uno`() {
        val forms = SrdBeasts.all

        assertEquals(64, forms.size)
        assertEquals(64, forms.mapTo(mutableSetOf()) { it.id }.size)
        assertFalse(forms.any { it.name == "Aquila gigante" })
        assertFalse(forms.any { it.name == "Gufo gigante" })
        assertTrue(forms.any { it.name == "Aquila" && it.hasFlySpeed })
        assertTrue(forms.any { it.name == "Iena gigante" && it.challengeRating == "1" })
    }

    @Test
    fun `i limiti di Forma selvatica seguono livello GS e velocita di volo`() {
        val atSecond = SrdBeasts.availableAt(2)
        val atFourth = SrdBeasts.availableAt(4)
        val atEighth = SrdBeasts.availableAt(8)

        assertEquals(39, atSecond.size)
        assertEquals(46, atFourth.size)
        assertEquals(64, atEighth.size)
        assertTrue(atSecond.none { it.hasFlySpeed })
        assertTrue(atFourth.none { it.hasFlySpeed })
        assertTrue(atSecond.all { it.challengeRating in setOf("0", "1/8", "1/4") })
        assertTrue(atFourth.all { it.challengeRating != "1" })
        assertTrue(atEighth.any { it.name == "Vespa gigante" && it.hasFlySpeed })
    }

    @Test
    fun `ogni forma ha una proiezione da combattimento con gli attacchi dello stat block`() {
        val actors = SrdBeasts.all.map { it.toActorDefinition() }

        assertEquals(64, actors.size)
        assertTrue(actors.all { it.armorClass() > 0 && it.maxHitPoints() > 0 })
        SrdBeasts.all.zip(actors)
            .filter { (form, _) -> "Tiro per colpire" in form.statBlock }
            .forEach { (form, actor) ->
                assertTrue(actor.abilities().isNotEmpty(), "Attacco non proiettato per ${form.name}")
            }
        val wolf = actors.first { it.id() == "srd521-it:beast:lupo" }
        assertTrue(wolf.abilities().any { it.name() == "Morso" })
    }

    @Test
    fun `ogni forma e consultabile nel compendio con la scheda delle statistiche`() {
        val wolf = requireNotNull(SrdBeasts.byId("srd521-it:beast:lupo"))
        val element = requireNotNull(Srd521ItContent.pack.element(wolf.id))
        val catalog = Srd521ItContent.catalog.single { it.id == wolf.id }

        assertTrue(element.description.contains("PF 11 (2d8 + 2)"))
        assertTrue(element.description.contains("Tattiche del branco"))
        assertEquals(394, element.sourcePage)
        assertEquals(CharacterClassId.DRUID, element.classEligibility.single().classId)
        assertFalse(catalog.passive)
        assertEquals("srd521-it:resource:druido:forma-selvatica", catalog.resourceId)
        assertEquals(1, catalog.resourceCost)
        assertTrue(catalog.rulesText.contains("Morso"))
    }
}
