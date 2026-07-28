package app.d6d.ui.sheet

import app.d6d.sheet.ArmorClassMethod
import app.d6d.sheet.CatalogAbility
import app.d6d.sheet.SheetStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class SheetViewModelTest {

    @TempDir
    lateinit var directory: Path

    private fun model(file: Path = directory.resolve("schede.json")) = SheetViewModel(SheetStore(file))

    @Test
    fun `una modifica rende dirty la bozza e la selezione non la scarta implicitamente`() {
        val model = model()
        val originalId = model.selectedId!!
        val anotherId = model.library.characters.first { it.id != originalId }.id

        assertFalse(model.isDirty)
        model.character = model.character.copy(characterName = "Bozza non salvata")
        assertTrue(model.isDirty)

        assertEquals(SheetNavigationResult.UNSAVED_CHANGES, model.selectCharacter(anotherId))
        assertEquals(originalId, model.selectedId)
        assertEquals("Bozza non salvata", model.character.characterName)

        assertEquals(
            SheetNavigationResult.APPLIED,
            model.selectCharacter(anotherId, discardUnsavedChanges = true),
        )
        assertFalse(model.isDirty)
    }

    @Test
    fun `cambiare tipo non scarta una bozza tramite il setter compatibile`() {
        val model = model()
        model.character = model.character.copy(characterName = "Da conservare")

        model.kind = SheetKind.MOSTRO

        assertEquals(SheetKind.PERSONAGGIO, model.kind)
        assertEquals("Da conservare", model.character.characterName)
        assertTrue(model.isDirty)
    }

    @Test
    fun `un modulo nuovo e ancora intatto non viene trattato come lavoro perso`() {
        val model = model()

        assertEquals(SheetNavigationResult.APPLIED, model.newSheet())
        assertFalse(model.isDirty)
        assertEquals(ArmorClassMethod.UNARMORED, model.character.armorClassMethod)
        assertEquals(10, model.character.effectiveArmorClass)

        model.character = model.character.copy(characterName = "Nuovo eroe")
        assertTrue(model.isDirty)
    }

    @Test
    fun `un salvataggio fallito non anticipa il commit della libreria in memoria`() {
        val parent = directory.resolve("archivio")
        val file = parent.resolve("schede.json")
        val model = model(file)
        val libraryBefore = model.library
        model.character = model.character.copy(characterName = "Non persistito")

        // Trasforma la cartella in un file: la successiva scrittura deve fallire.
        Files.delete(file)
        Files.delete(parent)
        Files.writeString(parent, "bloccato")

        assertFalse(model.save())
        assertEquals(libraryBefore, model.library)
        assertTrue(model.isDirty)
    }

    @Test
    fun `un upsert silenzioso fallito lascia intatta la copia in memoria`() {
        val parent = directory.resolve("archivio")
        val file = parent.resolve("schede.json")
        val model = model(file)
        val libraryBefore = model.library
        val sheet = libraryBefore.characters.first().copy(characterName = "Correzione")

        Files.delete(file)
        Files.delete(parent)
        Files.writeString(parent, "bloccato")

        assertFalse(model.upsertCharacterSilently(sheet))
        assertEquals(libraryBefore, model.library)
    }

    @Test
    fun `il catalogo distribuito arriva dal content pack e le abilita private si salvano`() {
        val model = model()

        // Le voci SRD non stanno nel file dell'utente: le porta il content pack.
        assertTrue(model.library.abilities.isEmpty())
        assertTrue(model.abilityCatalog.any { it.name == "Palla di fuoco" })

        val ability = CatalogAbility(id = "abilita-prova", name = "Colpo di prova")
        assertTrue(model.upsertAbility(ability))

        val reopened = model()
        assertEquals(ability, reopened.library.abilities.first { it.id == ability.id })
    }

    @Test
    fun `i personaggi inclusi usano le voci del catalogo SRD`() {
        val model = model()
        val sibilla = model.library.characters.first { it.id == "pg-sibilla" }
        val nerea = model.library.characters.first { it.id == "pg-nerea" }

        assertTrue(sibilla.abilityIds.any { it.startsWith("srd521-it:spell:") })
        assertTrue(nerea.abilityIds.any { it.startsWith("srd521-it:spell:") })
        assertTrue(model.abilityCatalog.map { it.id }.containsAll(nerea.abilityIds))
    }

    @Test
    fun `una voce SRD si puo riclassificare senza modificare il pacchetto`() {
        val file = directory.resolve("schede.json")
        val model = model(file)
        val srd = model.abilityCatalog.first { it.id.startsWith("srd521-it:") && !it.passive }

        assertTrue(model.setAbilityPassive(srd.id, true))
        assertTrue(model.abilityCatalog.first { it.id == srd.id }.passive)
        assertTrue(model.abilityPassiveIsOverridden(srd.id))
        // Il pacchetto resta in sola lettura: la scelta vive nell'archivio utente,
        // non in una copia modificata della voce.
        assertTrue(model.library.abilities.none { it.id == srd.id })
        assertFalse(model.upsertAbility(srd.copy(name = "Modificata")))

        val reopened = model(file)
        assertTrue(reopened.abilityCatalog.first { it.id == srd.id }.passive)

        // Tornare al valore del pacchetto cancella l'annotazione.
        assertTrue(reopened.setAbilityPassive(srd.id, false))
        assertFalse(reopened.abilityPassiveIsOverridden(srd.id))
        assertFalse(model(file).abilityCatalog.first { it.id == srd.id }.passive)
    }

    @Test
    fun `una abilita personale porta la classificazione in se stessa`() {
        val model = model()
        val ability = CatalogAbility(id = "abilita-prova", name = "Colpo di prova")
        assertTrue(model.upsertAbility(ability))

        assertTrue(model.setAbilityPassive(ability.id, true))

        assertTrue(model.library.abilities.first { it.id == ability.id }.passive)
        assertFalse(model.abilityPassiveIsOverridden(ability.id))
    }

    @Test
    fun `una abilita usata da una scheda non puo essere eliminata`() {
        val model = model()
        val ability = CatalogAbility(id = "abilita-prova", name = "Colpo di prova")
        assertTrue(model.upsertAbility(ability))
        model.character = model.character.copy(abilityIds = listOf(ability.id))

        assertFalse(model.deleteAbility(ability.id))
        assertTrue(model.library.abilities.any { it.id == ability.id })
    }
}
