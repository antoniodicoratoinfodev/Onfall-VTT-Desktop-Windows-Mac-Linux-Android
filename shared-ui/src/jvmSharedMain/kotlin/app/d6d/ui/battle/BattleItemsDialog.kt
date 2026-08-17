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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.d6d.ui.components.Chip
import app.d6d.ui.components.Eyebrow
import app.d6d.ui.theme.OrnateDivider
import app.d6d.sheet.i18n.distanceLabel
import app.d6d.ui.i18n.Strings
import app.d6d.ui.i18n.strings
import app.d6d.ui.theme.Palette
import app.d6d.ui.theme.ornateFrame
import app.d6d.ui.theme.panelBrush

/** Categorie dell'inventario da combattimento. */
enum class ItemCategory(val tint: androidx.compose.ui.graphics.Color) {
    POZIONI(Palette.Heal),
    ARMI(Palette.Enemy),
    ARMATURE(Palette.Party),
    PERGAMENE(Palette.Gold),
    VARIE(Palette.TextMuted),
}

fun ItemCategory.label(strings: Strings): String = when (this) {
    ItemCategory.POZIONI -> strings.items.categoryPotions
    ItemCategory.ARMI -> strings.items.categoryWeapons
    ItemCategory.ARMATURE -> strings.items.categoryArmor
    ItemCategory.PERGAMENE -> strings.items.categoryScrolls
    ItemCategory.VARIE -> strings.items.categoryMisc
}

/**
 * Oggetto usabile in scontro: pozione, arma, armatura e simili.
 *
 * Per ora e' solo un descrittore: l'interfaccia mostra nome, descrizione ed
 * effetti, ma nessun effetto viene ancora applicato al combattimento. Il modello
 * vive qui in attesa che il catalogo vero (e l'uso al tavolo) venga collegato.
 */
data class BattleItem(
    val id: String,
    val name: String,
    val category: ItemCategory,
    val description: String,
    val effects: List<String>,
)

/**
 * Catalogo di esempio.
 *
 * Sono voci illustrative, non contenuto definitivo: servono a mostrare come si
 * leggeranno descrizione ed effetti. Sostituiscile (o svuota la lista) quando
 * arrivera' l'inventario reale. Sono scritte da noi, quindi si traducono: il
 * testo vive in `strings.items` e la lista si ricostruisce a ogni cambio lingua.
 */
fun sampleBattleItems(strings: Strings): List<BattleItem> {
    val words = strings.items
    val language = strings.language
    return listOf(
        BattleItem(
            id = "pozione-guarigione",
            name = words.healingPotionName,
            category = ItemCategory.POZIONI,
            description = words.healingPotionDescription,
            effects = listOf(words.healingPotionEffect1, words.healingPotionEffect2),
        ),
        BattleItem(
            id = "pozione-forza",
            name = words.strengthPotionName,
            category = ItemCategory.POZIONI,
            description = words.strengthPotionDescription,
            effects = listOf(words.strengthPotionEffect1, words.strengthPotionEffect2),
        ),
        BattleItem(
            id = "spada-lunga",
            name = words.longswordName,
            category = ItemCategory.ARMI,
            description = words.longswordDescription,
            effects = listOf(words.longswordEffect1, words.longswordEffect2),
        ),
        BattleItem(
            id = "arco-corto",
            name = words.shortbowName,
            category = ItemCategory.ARMI,
            description = words.shortbowDescription,
            effects = listOf(
                words.shortbowEffect1,
                // 80/320 piedi e' la gittata SRD: in italiano diventano metri.
                words.shortbowRange(distanceLabel(80, language), distanceLabel(320, language)),
            ),
        ),
        BattleItem(
            id = "pergamena-scudo",
            name = words.shieldScrollName,
            category = ItemCategory.PERGAMENE,
            description = words.shieldScrollDescription,
            effects = listOf(words.shieldScrollEffect1, words.shieldScrollEffect2),
        ),
        BattleItem(
            id = "cuoio-borchiato",
            name = words.studdedLeatherName,
            category = ItemCategory.ARMATURE,
            description = words.studdedLeatherDescription,
            effects = listOf(words.studdedLeatherEffect1, words.studdedLeatherEffect2),
        ),
    )
}

/**
 * Inventario da combattimento.
 *
 * L'interfaccia c'e' gia' — elenco a sinistra, lettura di descrizione ed effetti
 * a destra — mentre l'uso vero degli oggetti arrivera' in seguito. Il pannello di
 * dettaglio segue l'oggetto sotto il mouse; se non se ne sta sorvolando nessuno,
 * mostra l'ultimo selezionato con un clic.
 */
@Composable
fun BattleItemsDialog(
    items: List<BattleItem>,
    open: Boolean,
    onDismiss: () -> Unit,
) {
    if (!open) return

    val strings = strings
    val words = strings.items
    var category by remember { mutableStateOf<ItemCategory?>(null) }
    var selected by remember { mutableStateOf<BattleItem?>(null) }
    var hovered by remember { mutableStateOf<BattleItem?>(null) }

    val visible = items.filter { category == null || it.category == category }
    val detail = hovered ?: selected

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(12.dp),
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
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            strings.battle.itemsSubtitle,
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
                    ItemCategory.entries.forEach { entry ->
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
    items: List<BattleItem>,
    selected: BattleItem?,
    onSelect: (BattleItem) -> Unit,
    onHover: (BattleItem, Boolean) -> Unit,
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
    item: BattleItem,
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
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                item.category.label(strings),
                color = item.category.tint,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun ItemDetail(item: BattleItem?, modifier: Modifier = Modifier) {
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
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleLarge,
            )
            Chip(item.category.label(strings), item.category.tint)
        }

        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Eyebrow(strings.items.descriptionCaps)
            Text(
                item.description,
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
