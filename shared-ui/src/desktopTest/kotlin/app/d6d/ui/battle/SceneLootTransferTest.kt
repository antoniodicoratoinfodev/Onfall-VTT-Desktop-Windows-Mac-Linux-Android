package app.d6d.ui.battle

import app.d6d.board.GridPoint
import app.d6d.board.SceneToken
import app.d6d.board.TokenCategory
import app.d6d.board.TokenLootCategory
import app.d6d.persistence.catalog.ActorCatalogStore
import app.d6d.sheet.InventoryCategory
import app.d6d.sheet.SheetStore
import app.d6d.ui.board.BoardController
import app.d6d.ui.roster.RosterViewModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class SceneLootTransferTest {

    @TempDir
    lateinit var directory: Path

    @Test
    fun `il loot entra solo nel PG scelto e la pedina non torna con undo`() {
        val roster = roster()
        val recipient = roster.sheets.library.characters.first()
        val other = roster.sheets.library.characters.drop(1).first()
        val board = BoardController()
        val token = lootToken()
        board.add(token)

        assertEquals(
            LootTransferResult.SUCCESS,
            transferSceneLoot(token.id(), recipient.id, board, roster),
        )

        val received = roster.characterInventory(recipient.id)!!.items.single()
        assertEquals(token.id(), received.id)
        assertEquals(token.id(), received.sourceTokenId)
        assertEquals("Fiala luminescente", received.name)
        assertEquals(InventoryCategory.POTION, received.category)
        assertEquals(3, received.quantity)
        assertEquals("Recupera vigore.", received.description)
        assertFalse(received.description.contains("maledetta", ignoreCase = true))
        assertTrue(roster.characterInventory(other.id)!!.items.isEmpty())
        assertTrue(board.document.objects().isEmpty())

        assertFalse(board.canUndo)
        assertFalse(board.undo())
        assertTrue(board.document.objects().none { it.id() == token.id() })
        assertFalse(board.canRedo)
        assertFalse(board.redo())
        assertTrue(board.document.objects().none { it.id() == token.id() })

        // Simula il recovery di una sessione non ancora autosalvata: la pedina
        // ricompare, ma la provenienza globale impedisce di darla a un altro PG.
        assertTrue(board.add(token))
        assertEquals(
            LootTransferResult.SUCCESS,
            transferSceneLoot(token.id(), other.id, board, roster),
        )
        assertTrue(roster.characterInventory(other.id)!!.items.isEmpty())
        assertTrue(board.document.objects().none { it.id() == token.id() })
    }

    @Test
    fun `un token non raccoglibile non modifica scheda o mappa`() {
        val roster = roster()
        val recipient = roster.sheets.library.characters.first()
        val board = BoardController()
        val token = SceneToken(
            "scena", "Statua", TokenCategory.OBJECT, GridPoint(1.5, 1.5),
            1.0, 0.0, 0xffcc8844.toInt(), "", true, true, "Solo scenario",
        )
        board.add(token)

        assertEquals(
            LootTransferResult.TOKEN_NOT_LOOTABLE,
            transferSceneLoot(token.id(), recipient.id, board, roster),
        )
        assertTrue(roster.characterInventory(recipient.id)!!.items.isEmpty())
        assertEquals(token, board.document.objects().single())
    }

    @Test
    fun `raccogli usa i metadati correnti del popup senza richiedere applica`() {
        val roster = roster()
        val recipient = roster.sheets.library.characters.first()
        val board = BoardController()
        val stored = lootToken()
        board.add(stored)
        val visibleDraft = SceneToken(
            stored.id(), stored.name(), stored.category(), stored.position(), stored.sizeSquares(),
            stored.rotationDegrees(), stored.colorArgb(), stored.imageAssetId(), stored.showLabel(),
            stored.visibleToPlayers(), true, TokenLootCategory.POTION, 7,
            "Descrizione corretta nel popup.", stored.notes(),
        )

        assertEquals(
            LootTransferResult.SUCCESS,
            transferSceneLoot(visibleDraft, recipient.id, board, roster),
        )

        val received = roster.characterInventory(recipient.id)!!.items.single()
        assertEquals(7, received.quantity)
        assertEquals("Descrizione corretta nel popup.", received.description)
    }

    @Test
    fun `se l inventario non si salva la pedina rimane sulla mappa`() {
        val sheetFile = directory.resolve("schede-bloccate.json")
        val roster = RosterViewModel(ActorCatalogStore(directory.resolve("catalogo")), SheetStore(sheetFile))
        val recipient = roster.sheets.library.characters.first()
        Files.delete(sheetFile)
        Files.createDirectory(sheetFile)
        val board = BoardController()
        val token = lootToken()
        board.add(token)

        assertEquals(
            LootTransferResult.INVENTORY_WRITE_FAILED,
            transferSceneLoot(token.id(), recipient.id, board, roster),
        )
        assertTrue(roster.characterInventory(recipient.id)!!.items.isEmpty())
        assertEquals(token, board.document.objects().single())
    }

    private fun roster() = RosterViewModel(
        ActorCatalogStore(directory.resolve("catalogo")),
        SheetStore(directory.resolve("schede.json")),
    )

    private fun lootToken() = SceneToken(
        "loot-token", "Fiala luminescente", TokenCategory.LOOT, GridPoint(2.5, 2.5),
        1.0, 0.0, 0xffcc8844.toInt(), "", true, true,
        true, TokenLootCategory.POTION, 3, "Recupera vigore.", "È maledetta.",
    )
}
