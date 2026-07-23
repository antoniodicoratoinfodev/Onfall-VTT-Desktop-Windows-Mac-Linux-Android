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
import app.d6d.ui.theme.Palette
import app.d6d.ui.theme.ornateFrame
import app.d6d.ui.theme.panelBrush

/** Categorie dell'inventario da combattimento. */
enum class ItemCategory(val label: String, val tint: androidx.compose.ui.graphics.Color) {
    POZIONI("Pozioni", Palette.Heal),
    ARMI("Armi", Palette.Enemy),
    ARMATURE("Armature", Palette.Party),
    PERGAMENE("Pergamene", Palette.Gold),
    VARIE("Varie", Palette.TextMuted),
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
 * arrivera' l'inventario reale.
 */
val sampleBattleItems: List<BattleItem> = listOf(
    BattleItem(
        id = "pozione-guarigione",
        name = "Pozione di guarigione",
        category = ItemCategory.POZIONI,
        description = "Una fiala di liquido scarlatto che luccica quando la si agita.",
        effects = listOf("Recupera 2d4 + 2 punti ferita", "Berla costa un'azione bonus"),
    ),
    BattleItem(
        id = "pozione-forza",
        name = "Pozione di forza smisurata",
        category = ItemCategory.POZIONI,
        description = "Denso intruglio che sa di ferro e tuono.",
        effects = listOf("Vantaggio alle prove di Forza per 10 minuti", "Concentrazione non richiesta"),
    ),
    BattleItem(
        id = "spada-lunga",
        name = "Spada lunga affilata",
        category = ItemCategory.ARMI,
        description = "Lama a un taglio ben bilanciata, adatta a una o due mani.",
        effects = listOf("1d8 danni taglienti (1d10 a due mani)", "Proprieta': versatile"),
    ),
    BattleItem(
        id = "arco-corto",
        name = "Arco corto da caccia",
        category = ItemCategory.ARMI,
        description = "Arco leggero in legno di tasso, buono per il tiro rapido.",
        effects = listOf("1d6 danni perforanti", "Gittata 24 / 96 metri"),
    ),
    BattleItem(
        id = "pergamena-scudo",
        name = "Pergamena dello scudo arcano",
        category = ItemCategory.PERGAMENE,
        description = "Sottile foglio runato che si sbriciola dopo l'uso.",
        effects = listOf("Reazione: +5 alla Classe Armatura", "Dura fino all'inizio del tuo turno"),
    ),
    BattleItem(
        id = "cuoio-borchiato",
        name = "Cuoio borchiato",
        category = ItemCategory.ARMATURE,
        description = "Armatura leggera rinforzata da borchie metalliche.",
        effects = listOf("CA 12 + modificatore di Destrezza", "Nessuna penalita' alla furtivita'"),
    ),
)

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
                            "Oggetti",
                            color = Palette.Text,
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            "Pozioni, armi ed equipaggiamento del gruppo.",
                            color = Palette.TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    GameButton("Chiudi", accent = Palette.TextMuted, onClick = onDismiss)
                }
                OrnateDivider(color = Palette.GoldDim)

                // Filtro per categoria: "Tutti" piu' una voce per tipo.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    GameButton(
                        label = "Tutti",
                        accent = if (category == null) Palette.Gold else Palette.TextFaint,
                        selected = category == null,
                        onClick = { category = null },
                    )
                    ItemCategory.entries.forEach { entry ->
                        GameButton(
                            label = entry.label,
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
        Eyebrow("Inventario")
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Nessun oggetto qui ancora.",
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
                item.category.label,
                color = item.category.tint,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun ItemDetail(item: BattleItem?, modifier: Modifier = Modifier) {
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
                    "Passa il mouse su un oggetto — o cliccalo — per leggerne qui descrizione ed effetti.",
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
            Chip(item.category.label, item.category.tint)
        }

        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Eyebrow("Descrizione")
            Text(
                item.description,
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Eyebrow("Effetti")
            if (item.effects.isEmpty()) {
                Text(
                    "Nessun effetto indicato.",
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
