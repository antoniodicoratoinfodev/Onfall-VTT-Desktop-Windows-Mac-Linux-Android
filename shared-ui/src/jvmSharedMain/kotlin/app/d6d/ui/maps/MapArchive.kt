package app.d6d.ui.maps

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.d6d.sheet.ImageStore
import app.d6d.sheet.StoredMap
import app.d6d.ui.battle.GameButton
import app.d6d.ui.components.Chip
import app.d6d.ui.components.Eyebrow
import app.d6d.ui.components.PanelScrollbar
import app.d6d.ui.images.PortraitRepository
import app.d6d.ui.images.rememberBitmap
import app.d6d.ui.i18n.strings
import app.d6d.ui.theme.Palette

/**
 * Archivio delle mappe: la sezione del Compendio dove l'utente raccoglie i propri
 * sfondi.
 *
 * Una mappa si carica una volta e la si ritrova per nome in ogni partita: da qui
 * si rinomina e si elimina, e da «Scegli sfondo» la si sceglie senza ricaricarla.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MapArchive(
    portraits: PortraitRepository,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val words = strings.maps
    // Legare la composizione a `revision` fa ricomparire miniature e voci appena
    // una mappa viene aggiunta o rimossa.
    @Suppress("UNUSED_EXPRESSION")
    portraits.revision

    var pendingDelete by remember { mutableStateOf<StoredMap?>(null) }
    val maps = portraits.maps

    Column(modifier.fillMaxSize()) {
        MapArchiveHeader(compact, onUpload = { portraits.importMapAsync() })
        MapsFolderRow(portraits.mapsDirectory.toString())

        portraits.message?.let { note ->
            Text(
                text = note,
                color = Palette.Gold,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Palette.Gold.copy(alpha = 0.10f))
                    .clickable { portraits.dismissMessage() }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }

        if (maps.isEmpty()) {
            MapArchiveEmpty(Modifier.weight(1f))
        } else {
            val listState = rememberLazyListState()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                ) {
                    item {
                        Eyebrow(words.mapsCount(maps.size), modifier = Modifier.padding(bottom = 8.dp))
                    }
                    item {
                        FlowRow(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            maps.forEach { map ->
                                MapCard(
                                    map = map,
                                    portraits = portraits,
                                    onRename = { portraits.renameMap(map.id, it) },
                                    onDelete = { pendingDelete = map },
                                    modifier = Modifier.width(if (compact) 148.dp else 208.dp),
                                )
                            }
                        }
                    }
                }
                PanelScrollbar(listState, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
    }

    pendingDelete?.let { map ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = Palette.Surface,
            title = { Text(words.deleteMapTitle, color = Palette.Text) },
            text = {
                Text(
                    words.deleteMapBody(map.name),
                    color = Palette.TextMuted,
                )
            },
            confirmButton = {
                GameButton(strings.common.delete, accent = Palette.Enemy, onClick = {
                    portraits.deleteMap(map.id)
                    pendingDelete = null
                })
            },
            dismissButton = {
                GameButton(strings.common.cancel, accent = Palette.TextMuted, onClick = { pendingDelete = null })
            },
        )
    }
}

@Composable
private fun MapArchiveHeader(compact: Boolean, onUpload: () -> Unit) {
    val words = strings.maps
    val upload = @Composable {
        GameButton(
            label = words.uploadMap,
            accent = Palette.Party,
            subtitle = words.formatsAndLimit(ImageStore.acceptedFormatsLabel, ImageStore.maxSizeLabel),
            onClick = onUpload,
        )
    }
    if (compact) {
        Column(
            Modifier.fillMaxWidth().background(Palette.Surface).padding(14.dp, 10.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            MapArchiveTitle()
            upload()
        }
    } else {
        Row(
            Modifier.fillMaxWidth().background(Palette.Surface).padding(14.dp, 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MapArchiveTitle(Modifier.weight(1f))
            upload()
        }
    }
}

@Composable
private fun MapArchiveTitle(modifier: Modifier = Modifier) {
    val words = strings.maps
    Column(modifier) {
        Text(
            text = strings.compendium.maps,
            color = Palette.Text,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = words.archiveSubtitle,
            color = Palette.TextMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * Il percorso della cartella delle mappe, scritto per esteso.
 *
 * Non e' un dettaglio da impostazioni avanzate: chi arriva con una collezione di
 * sfondi gia' pronta vuole copiarcela dentro, non caricarne trenta uno a uno dal
 * selettore. Il percorso e' selezionabile perche' la cosa che ci si fa e'
 * incollarlo altrove — nel Finder, in un terminale, in una finestra di copia.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MapsFolderRow(path: String) {
    val words = strings.maps
    Column(
        Modifier
            .fillMaxWidth()
            .background(Palette.Night)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Eyebrow(words.mapsFolder)
        SelectionContainer {
            Text(
                text = path,
                color = Palette.Gold,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = words.mapsFolderHint,
            color = Palette.TextMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun MapArchiveEmpty(modifier: Modifier = Modifier) {
    val words = strings.maps
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = words.archiveEmpty,
            color = Palette.TextFaint,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun MapCard(
    map: StoredMap,
    portraits: PortraitRepository,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .background(Palette.Night, RoundedCornerShape(10.dp))
            .border(1.dp, Palette.Line, RoundedCornerShape(10.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        MapThumbnail(
            portraits = portraits,
            imageName = map.image,
            modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f),
        )
        MapNameField(name = map.name, onCommit = onRename)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            GameButton(strings.common.delete, accent = Palette.Enemy, dense = true, onClick = onDelete)
        }
    }
}

/** Nome della mappa: un clic lo trasforma in campo; Invio conferma, Esc annulla. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MapNameField(name: String, onCommit: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember(name) { mutableStateOf(name) }
    val focusRequester = remember { FocusRequester() }

    if (editing) {
        var acquiredFocus by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        val commit = {
            onCommit(draft)
            editing = false
        }
        BasicTextField(
            value = draft,
            onValueChange = { draft = it },
            singleLine = true,
            textStyle = TextStyle(color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold),
            cursorBrush = SolidColor(Palette.Gold),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit() }),
            modifier = Modifier
                .fillMaxWidth()
                .background(Palette.Abyss, RoundedCornerShape(5.dp))
                .border(1.dp, Palette.Gold, RoundedCornerShape(5.dp))
                .padding(horizontal = 7.dp, vertical = 5.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { state ->
                    if (state.isFocused) {
                        acquiredFocus = true
                    } else if (acquiredFocus && editing) {
                        commit()
                    }
                }
                .onPreviewKeyEvent { event ->
                    when {
                        event.type != KeyEventType.KeyDown -> false
                        event.key == Key.Enter -> { commit(); true }
                        event.key == Key.Escape -> { draft = name; editing = false; true }
                        else -> false
                    }
                },
        )
    } else {
        Text(
            text = name,
            color = Palette.Text,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(5.dp))
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { draft = name; editing = true },
                )
                .padding(horizontal = 2.dp, vertical = 2.dp),
        )
    }
}

/**
 * Miniatura di una mappa dell'archivio: l'immagine ritagliata, o un segnaposto
 * quando il file non è (più) leggibile.
 */
@Composable
fun MapThumbnail(
    portraits: PortraitRepository,
    imageName: String,
    modifier: Modifier = Modifier,
) {
    val words = strings.maps
    val bitmap = portraits.rememberBitmap(imageName)
    Box(
        modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Palette.SurfaceHigh)
            .border(1.dp, Palette.Line, RoundedCornerShape(7.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(7.dp)),
            )
        } else {
            Chip(words.previewUnavailable, Palette.TextFaint)
        }
    }
}
