package app.d6d.ui.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Un campo di una riga adattiva della scheda.
 *
 * Il peso conserva le proporzioni del layout desktop. Su uno schermo compatto
 * [AdaptiveFormRow] limita invece il numero di campi per riga, evitando caselle
 * troppo strette o contenuto tagliato fuori dallo schermo.
 */
internal class AdaptiveFormItem(
    val weight: Float,
    val content: @Composable (Modifier) -> Unit,
)

internal fun adaptiveFormItem(
    weight: Float = 1f,
    content: @Composable (Modifier) -> Unit,
): AdaptiveFormItem = AdaptiveFormItem(weight, content)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AdaptiveFormRow(
    compact: Boolean,
    vararg items: AdaptiveFormItem,
    compactColumns: Int = 1,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        maxItemsInEachRow = if (compact) compactColumns.coerceAtLeast(1) else items.size,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        items.forEach { item ->
            item.content(Modifier.weight(item.weight))
        }
    }
}
