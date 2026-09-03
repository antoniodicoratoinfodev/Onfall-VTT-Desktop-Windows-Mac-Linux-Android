package app.d6d.ui.battle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.d6d.ui.components.Chip
import app.d6d.ui.components.dismissDialogOnTap
import app.d6d.ui.components.Eyebrow
import app.d6d.ui.components.keepDialogOpenOnTap
import app.d6d.ui.theme.OrnateDivider
import app.d6d.ui.i18n.Strings
import app.d6d.ui.i18n.strings
import app.d6d.ui.theme.Palette
import app.d6d.ui.theme.ornateFrame
import app.d6d.ui.theme.panelBrush
import app.d6d.board.TokenLootCategory
import app.d6d.sheet.InventoryCategory
import app.d6d.sheet.InventoryItem

/** Categorie dell'inventario da combattimento. */
private val InventoryCategory.tint: androidx.compose.ui.graphics.Color
    get() = when (this) {
        InventoryCategory.POTION -> Palette.Heal
        InventoryCategory.WEAPON -> Palette.Enemy
        InventoryCategory.ARMOR -> Palette.Party
        InventoryCategory.SCROLL -> Palette.Gold
        InventoryCategory.MISC -> Palette.TextMuted
    }

fun InventoryCategory.label(strings: Strings): String = when (this) {
    InventoryCategory.POTION -> strings.items.categoryPotions
    InventoryCategory.WEAPON -> strings.items.categoryWeapons
    InventoryCategory.ARMOR -> strings.items.categoryArmor
    InventoryCategory.SCROLL -> strings.items.categoryScrolls
    InventoryCategory.MISC -> strings.items.categoryMisc
}

/** La categoria salvata sulla pedina usa lo stesso vocabolario dell'inventario. */
internal fun TokenLootCategory.label(strings: Strings): String = when (this) {
    TokenLootCategory.POTION -> strings.items.categoryPotions
    TokenLootCategory.WEAPON -> strings.items.categoryWeapons
    TokenLootCategory.ARMOR -> strings.items.categoryArmor
    TokenLootCategory.SCROLL -> strings.items.categoryScrolls
    TokenLootCategory.MISC -> strings.items.categoryMisc
}

/**
 * Inventario da combattimento.
 *
 * Legge le voci persistite nella scheda del personaggio ispezionato. Il pannello
 * di dettaglio segue l'oggetto sotto il mouse; se non se ne sta sorvolando
 * nessuno, mostra l'ultimo selezionato con un clic.
 */
@Composable
fun BattleItemsDialog(
    items: List<InventoryItem>,
    ownerName: String?,
    open: Boolean,
    onDismiss: () -> Unit,
) {
    if (!open) return

    val strings = strings
    val words = strings.items
    var category by remember(ownerName) { mutableStateOf<InventoryCategory?>(null) }
    var selected by remember(ownerName) { mutableStateOf<InventoryItem?>(null) }
    var hovered by remember(ownerName) { mutableStateOf<InventoryItem?>(null) }

    val visible = items.filter { category == null || it.category == category }
    val detail = hovered ?: selected

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            Modifier.fillMaxSize().dismissDialogOnTap(onDismiss).padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            val stacked = maxWidth < 560.dp
            val dialogShape = RoundedCornerShape(14.dp)
            Column(
                Modifier
                    .widthIn(max = 780.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .panelBrush(dialogShape)
                    .border(1.dp, Palette.Bronze.copy(alpha = 0.6f), dialogShape)
                    .ornateFrame(accent = Palette.Gold, alpha = 0.5f)
                    .keepDialogOpenOnTap()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            words.title,
                            color = Palette.Text,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            ownerName?.let(strings.battle::inventoryOf)
                                ?: strings.battle.selectCharacterForItems,
                            color = Palette.TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    GameButton(strings.common.close, accent = Palette.TextMuted, onClick = onDismiss)
                }
                OrnateDivider(color = Palette.GoldDim)

                // Filtro per categoria: "Tutti" piu' una voce per tipo.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    GameButton(
                        label = strings.common.all,
                        accent = if (category == null) Palette.Gold else Palette.TextFaint,
                        selected = category == null,
                        onClick = { category = null },
                    )
                    InventoryCategory.entries.forEach { entry ->
                        GameButton(
                            label = entry.label(strings),
                            accent = if (category == entry) entry.tint else Palette.TextFaint,
                            selected = category == entry,
                            onClick = { category = entry },
                        )
                    }
                }

                val list: @Composable (Modifier) -> Unit = { listModifier ->
                    ItemList(
                        items = visible,
                        selected = selected,
                        onSelect = { selected = it },
                        onHover = { item, isHovered ->
                            if (isHovered) hovered = item else if (hovered == item) hovered = null
                        },
                        modifier = listModifier,
                    )
                }
                val panel: @Composable (Modifier) -> Unit = { panelModifier ->
                    ItemDetail(detail, modifier = panelModifier)
                }

                if (stacked) {
                    list(Modifier.fillMaxWidth().height(220.dp))
                    panel(Modifier.fillMaxWidth().heightIn(min = 150.dp))
                } else {
                    Row(
                        Modifier.fillMaxWidth().height(360.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        list(Modifier.weight(1.05f).fillMaxHeight())
                        panel(Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemList(
    items: List<InventoryItem>,
    selected: InventoryItem?,
    onSelect: (InventoryItem) -> Unit,
    onHover: (InventoryItem, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .background(Palette.Night, RoundedCornerShape(10.dp))
            .border(1.dp, Palette.Line, RoundedCornerShape(10.dp))
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Eyebrow(strings.items.inventoryCaps)
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    strings.battle.noItemsYet,
                    color = Palette.TextFaint,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                items.forEach { item ->
                    ItemRow(
                        item = item,
                        selected = item == selected,
                        onSelect = { onSelect(item) },
                        onHover = { isHovered -> onHover(item, isHovered) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemRow(
    item: InventoryItem,
    selected: Boolean,
    onSelect: () -> Unit,
    onHover: (Boolean) -> Unit,
) {
    val strings = strings
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    LaunchedEffect(hovered) { onHover(hovered) }

    val shape = RoundedCornerShape(8.dp)
    val highlight = selected || hovered
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (highlight) item.category.tint.copy(alpha = 0.12f) else Palette.Surface,
                shape,
            )
            .border(
                1.dp,
                if (selected) item.category.tint else Palette.Line.copy(alpha = if (hovered) 0.9f else 0.5f),
                shape,
            )
            .hoverable(interaction)
            .clickable(onClick = onSelect)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                item.name,
                color = Palette.Text,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                item.category.label(strings),
                color = item.category.tint,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (item.quantity > 1) {
            Chip("×${item.quantity}", item.category.tint)
        }
    }
}

@Composable
private fun ItemDetail(item: InventoryItem?, modifier: Modifier = Modifier) {
    val strings = strings
    Column(
        modifier
            .background(Palette.Night, RoundedCornerShape(10.dp))
            .border(1.dp, Palette.Line, RoundedCornerShape(10.dp))
            .padding(13.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        if (item == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    strings.battle.hoverItemHint,
                    color = Palette.TextFaint,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            return@Column
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                item.name,
                color = Palette.Text,
                style = MaterialTheme.typography.titleLarge,
            )
            Chip(item.category.label(strings), item.category.tint)
        }

        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Eyebrow(strings.items.descriptionCaps)
            Text(
                item.description.ifBlank { strings.battle.noDescriptionListed },
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Eyebrow(strings.items.effectsCaps)
            if (item.effects.isEmpty()) {
                Text(
                    strings.battle.noEffectListed,
                    color = Palette.TextFaint,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                item.effects.forEach { effect ->
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("•", color = item.category.tint, style = MaterialTheme.typography.bodyMedium)
                        Text(effect, color = Palette.Text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
