package app.d6d.ui.maps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.d6d.sheet.ImageStore
import app.d6d.ui.battle.GameButton
import app.d6d.ui.components.dismissDialogOnTap
import app.d6d.ui.components.keepDialogOpenOnTap
import app.d6d.ui.images.PortraitRepository
import app.d6d.ui.theme.OrnateDivider
import app.d6d.ui.i18n.strings
import app.d6d.ui.theme.Palette
import app.d6d.ui.theme.ornateFrame
import app.d6d.ui.theme.panelBrush

/**
 * Scelta dello sfondo dall'archivio delle mappe.
 *
 * Invece di riaprire ogni volta il selettore di file, «Scegli sfondo» mostra le
 * mappe già caricate: si sceglie con un tocco. Da qui si può comunque caricarne
 * una nuova, che entra nell'archivio e diventa subito lo sfondo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MapPickerDialog(
    portraits: PortraitRepository,
    currentImage: String,
    onChoose: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val words = strings.maps
    // Legare a `revision`: una mappa appena caricata compare senza riaprire.
    @Suppress("UNUSED_EXPRESSION")
    portraits.revision
    val maps = portraits.maps

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            Modifier.fillMaxSize().dismissDialogOnTap(onDismiss).padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            val compact = maxWidth < 460.dp
            val dialogShape = RoundedCornerShape(12.dp)
            Column(
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .panelBrush(dialogShape)
                    .border(1.dp, Palette.Bronze.copy(alpha = 0.6f), dialogShape)
                    .ornateFrame(accent = Palette.Gold, alpha = 0.5f)
                    .keepDialogOpenOnTap()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = words.chooseBackground,
                            color = Palette.Text,
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = words.chooseBackgroundSubtitle,
                            color = Palette.TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    GameButton(
                        label = words.upload,
                        accent = Palette.Party,
                        subtitle = words.formatsAndLimit(ImageStore.acceptedFormatsLabel, ImageStore.maxSizeLabel),
                        onClick = {
                            portraits.importMapAsync { created ->
                                created?.let {
                                    onChoose(it.image)
                                    onDismiss()
                                }
                            }
                        },
                    )
                }
                OrnateDivider(color = Palette.GoldDim)

                portraits.message?.let { note ->
                    Text(
                        text = note,
                        color = Palette.Gold,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Palette.Gold.copy(alpha = 0.10f), RoundedCornerShape(5.dp))
                            .clickable { portraits.dismissMessage() }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                    )
                }

                if (maps.isEmpty()) {
                    Text(
                        text = words.pickerEmpty,
                        color = Palette.TextFaint,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else {
                    FlowRow(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .weight(1f, fill = false),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        maps.forEach { map ->
                            val selected = map.image == currentImage
                            Column(
                                Modifier
                                    .width(if (compact) 132.dp else 172.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Palette.Night, RoundedCornerShape(10.dp))
                                    .border(
                                        1.dp,
                                        if (selected) Palette.Gold else Palette.Line,
                                        RoundedCornerShape(10.dp),
                                    )
                                    .clickable {
                                        onChoose(map.image)
                                        onDismiss()
                                    }
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                MapThumbnail(
                                    portraits = portraits,
                                    imageName = map.image,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f),
                                )
                                Text(
                                    text = map.name,
                                    color = if (selected) Palette.GoldBright else Palette.Text,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                if (selected) {
                                    Text(
                                        text = words.currentBackground,
                                        color = Palette.Gold,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                }

                OrnateDivider(color = Palette.GoldDim)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (currentImage.isNotBlank()) {
                        GameButton(words.removeBackground, accent = Palette.TextFaint, onClick = {
                            onRemove()
                            onDismiss()
                        })
                    }
                    Box(Modifier.weight(1f))
                    GameButton(strings.common.close, accent = Palette.TextMuted, onClick = onDismiss)
                }
            }
        }
    }
}
