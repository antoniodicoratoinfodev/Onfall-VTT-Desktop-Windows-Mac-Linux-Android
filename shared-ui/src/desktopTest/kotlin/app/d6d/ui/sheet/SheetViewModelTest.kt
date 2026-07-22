package app.d6d.ui.sheet

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
}
