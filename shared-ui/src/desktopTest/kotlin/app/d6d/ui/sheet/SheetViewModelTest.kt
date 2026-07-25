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
    fun `il catalogo iniziale contiene palla di fuoco e salva nuove abilita`() {
        val model = model()

        assertTrue(model.library.abilities.any { it.name == "Palla di Fuoco" })
        assertTrue(model.library.abilities.any { it.name == "Arco Lungo" })
        assertTrue(model.library.abilities.any { it.name == "Pugnale" })
        assertTrue(model.library.abilities.any { it.name == "Dardo Runico" })
        assertTrue(model.library.abilities.any { it.name == "Morso Gelido" })

        val ability = CatalogAbility(id = "abilita-prova", name = "Colpo di prova")
        assertTrue(model.upsertAbility(ability))

        val reopened = model()
        assertEquals(ability, reopened.library.abilities.first { it.id == ability.id })
    }

    @Test
    fun `i personaggi di esempio usano le voci del catalogo`() {
        val model = model()
        val sylva = model.library.characters.first { it.id == "pg-sylva" }
        val mirethe = model.library.characters.first { it.id == "pg-mirethe" }
        val kaelen = model.library.characters.first { it.id == "pg-kaelen" }

        assertTrue("arma-arco" in sylva.abilityIds)
        assertTrue("arma-pugnale" in sylva.abilityIds)
        assertTrue("inc-dardo-runico" in mirethe.abilityIds)
        assertTrue("inc-palla-di-fuoco" in mirethe.abilityIds)
        assertTrue("abilita-recuperare-energie" in kaelen.abilityIds)
        assertTrue(kaelen.weapons.isEmpty())
    }

    @Test
    fun `una abilita usata da una scheda non puo essere eliminata`() {
        val model = model()
        val ability = model.library.abilities.first()
        model.character = model.character.copy(abilityIds = listOf(ability.id))

        assertFalse(model.deleteAbility(ability.id))
        assertTrue(model.library.abilities.any { it.id == ability.id })
    }
}
