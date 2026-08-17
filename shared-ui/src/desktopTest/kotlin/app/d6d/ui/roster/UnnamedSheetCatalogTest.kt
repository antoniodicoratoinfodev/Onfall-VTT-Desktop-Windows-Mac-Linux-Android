package app.d6d.ui.roster

import app.d6d.i18n.AppLanguage
import app.d6d.sheet.CharacterSheet
import app.d6d.sheet.MonsterStatBlock
import app.d6d.ui.i18n.AppLocale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Una scheda senza nome deve entrare nel catalogo in entrambe le lingue.
 *
 * `ActorCatalogEntry` esige che il nome della voce coincida con quello della
 * definizione dell'attore. I due nascevano in strati diversi da due espressioni
 * separate: tradotta quella dell'interfaccia e non quella del motore, in inglese
 * la scheda si salvava e poi il catalogo non si lasciava piu' rigenerare —
 * «Unnamed» contro «Senza nome». Il difetto non si vedeva in italiano, dove i
 * due testi coincidevano per caso.
 */
class UnnamedSheetCatalogTest {
    @Test
    fun `un personaggio senza nome entra nel catalogo in italiano`() {
        val entry = CharacterSheet(id = "pg", characterName = "").toCatalogEntry()
        assertEquals("Senza nome", entry.template.name)
        assertEquals(entry.template.name, entry.combatDefinition.name)
    }

    @Test
    fun `un personaggio senza nome entra nel catalogo in inglese`() {
        AppLocale.use(AppLanguage.ENGLISH)
        val entry = CharacterSheet(id = "pg", characterName = "").toCatalogEntry()
        assertEquals("Unnamed", entry.template.name)
        assertEquals(entry.template.name, entry.combatDefinition.name)
    }

    @Test
    fun `una creatura senza nome entra nel catalogo in italiano`() {
        val entry = MonsterStatBlock(id = "mostro", name = "").toCatalogEntry()
        assertEquals("Creatura senza nome", entry.template.name)
        assertEquals(entry.template.name, entry.combatDefinition.name)
    }

    @Test
    fun `una creatura senza nome entra nel catalogo in inglese`() {
        AppLocale.use(AppLanguage.ENGLISH)
        val entry = MonsterStatBlock(id = "mostro", name = "").toCatalogEntry()
        assertEquals("Unnamed creature", entry.template.name)
        assertEquals(entry.template.name, entry.combatDefinition.name)
    }

    @Test
    fun `il nome di ripiego ha una sola definizione`() {
        // Se qualcuno reintroducesse un ripiego separato nell'interfaccia,
        // questo lo coglierebbe anche senza passare dal catalogo.
        AppLanguage.entries.forEach { language ->
            AppLocale.use(language)
            val sheet = CharacterSheet(id = "pg", characterName = "")
            assertEquals(
                AppLocale.current.compendium.unnamed,
                sheet.toActorDefinition(language = language).name,
                "il ripiego dell'interfaccia e quello del motore divergono in $language",
            )
        }
    }
}
