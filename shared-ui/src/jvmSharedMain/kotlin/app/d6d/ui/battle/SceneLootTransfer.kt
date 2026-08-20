package app.d6d.ui.battle

import app.d6d.board.SceneToken
import app.d6d.board.TokenLootCategory
import app.d6d.sheet.InventoryCategory
import app.d6d.sheet.InventoryItem
import app.d6d.ui.board.BoardController
import app.d6d.ui.roster.RosterViewModel
import app.d6d.ui.state.BattleViewModel

data class LootCollector(
    val characterId: String,
    val name: String,
)

enum class LootTransferResult {
    SUCCESS,
    TOKEN_NOT_FOUND,
    TOKEN_NOT_LOOTABLE,
    COLLECTOR_NOT_AVAILABLE,
    INVENTORY_WRITE_FAILED,
    BOARD_CHANGED,
}

/** Personaggi della squadra che possiedono davvero una scheda inventario locale. */
fun eligibleLootCollectors(
    battle: BattleViewModel,
    roster: RosterViewModel,
): List<LootCollector> = battle.partyIds
    .mapNotNull { combatantId ->
        val definitionId = battle.combatant(combatantId)?.snapshot()?.definitionId()
            ?: return@mapNotNull null
        roster.characterInventory(definitionId)?.let { inventory ->
            LootCollector(inventory.characterId, inventory.characterName)
        }
    }
    .distinctBy { it.characterId }

/**
 * Trasferisce una pedina raccoglibile nell'inventario del PG scelto.
 *
 * La scheda viene scritta per prima: se il disco rifiuta il salvataggio, la
 * pedina resta intatta. Il consumo Board è definitivo rispetto al solo Undo del
 * Lucido, così la pedina non può essere ripristinata e raccolta di nuovo.
 */
fun transferSceneLoot(
    tokenId: String,
    collectorId: String,
    board: BoardController,
    roster: RosterViewModel,
): LootTransferResult {
    val token = board.document.objects()
        .filterIsInstance<SceneToken>()
        .firstOrNull { it.id() == tokenId }
        ?: return LootTransferResult.TOKEN_NOT_FOUND
    return transferSceneLoot(token, collectorId, board, roster)
}

/** Variante usata dal popup: trasferisce i metadati attualmente visibili nel form. */
fun transferSceneLoot(
    token: SceneToken,
    collectorId: String,
    board: BoardController,
    roster: RosterViewModel,
): LootTransferResult {
    if (board.document.objects().none { it.id() == token.id() }) {
        return LootTransferResult.TOKEN_NOT_FOUND
    }
    if (!token.lootable()) return LootTransferResult.TOKEN_NOT_LOOTABLE
    if (roster.characterInventory(collectorId) == null) {
        return LootTransferResult.COLLECTOR_NOT_AVAILABLE
    }
    if (!roster.addInventoryItem(collectorId, token.toInventoryItem())) {
        return LootTransferResult.INVENTORY_WRITE_FAILED
    }
    return if (board.consume(token.id())) {
        LootTransferResult.SUCCESS
    } else {
        // Un nuovo tentativo resta sicuro: addInventoryItem è idempotente sulla
        // provenienza e non duplica la voce già persistita.
        LootTransferResult.BOARD_CHANGED
    }
}

private fun SceneToken.toInventoryItem(): InventoryItem = InventoryItem(
    id = id(),
    name = name(),
    category = lootCategory().toInventoryCategory(),
    quantity = lootQuantity(),
    description = lootDescription(),
    sourceTokenId = id(),
)

private fun TokenLootCategory.toInventoryCategory(): InventoryCategory = when (this) {
    TokenLootCategory.POTION -> InventoryCategory.POTION
    TokenLootCategory.WEAPON -> InventoryCategory.WEAPON
    TokenLootCategory.ARMOR -> InventoryCategory.ARMOR
    TokenLootCategory.SCROLL -> InventoryCategory.SCROLL
    TokenLootCategory.MISC -> InventoryCategory.MISC
}
