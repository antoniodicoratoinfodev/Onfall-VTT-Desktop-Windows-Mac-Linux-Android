package app.d6d.ui.images

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import app.d6d.sheet.ImageStore
import app.d6d.sheet.MapLibrary
import app.d6d.sheet.PortraitLibrary
import app.d6d.sheet.StoredMap
import java.nio.file.Path
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scelta di un file dalla piattaforma.
 *
 * Desktop e Android aprono selettori completamente diversi, quindi la scelta viene
 * iniettata dalla shell invece di essere risolta nel codice condiviso. Una shell
 * che non sa scegliere file restituisce semplicemente null.
 */
fun interface FilePicker {
    fun pick(): Path?

    /**
     * Variante asincrona usata dalle shell che delegano la scelta al sistema.
     *
     * Il comportamento predefinito conserva esattamente il selettore sincrono
     * desktop. Android puo' invece aprire Activity Result senza bloccare il main
     * thread e consegnare il percorso temporaneo quando il documento e' pronto.
     */
    fun pickAsync(
        onPicked: (Path?) -> Unit,
        onError: (Throwable) -> Unit = { throw it },
    ) {
        try {
            onPicked(pick())
        } catch (failure: Throwable) {
            onError(failure)
        }
    }
}

/**
 * Ritratti degli attori e sfondi delle mappe.
 *
 * Le immagini decodificate restano in cache: ridisegnare la mappa a ogni fotogramma
 * non deve rileggere e ridecodificare i file. `decodeToImageBitmap` e' disponibile
 * sia su desktop sia su Android, quindi non serve alcuna astrazione di piattaforma.
 */
class PortraitRepository(
    private val store: ImageStore,
    private val picker: FilePicker = FilePicker { null },
) {

    var library by mutableStateOf(PortraitLibrary())
        private set

    /** Archivio delle mappe caricate dall'utente, riusabili come sfondi. */
    var mapLibrary by mutableStateOf(MapLibrary())
        private set

    var message by mutableStateOf<String?>(null)

    private val decoded = mutableMapOf<String, ImageBitmap?>()

    /** Cambia a ogni import: fa ricomporre chi disegna le immagini. */
    var revision by mutableStateOf(0)
        private set

    init {
        runCatching { library = store.loadLibrary() }
        runCatching { mapLibrary = store.loadMapLibrary() }
    }

    /** Le mappe dell'archivio, nell'ordine in cui sono state aggiunte. */
    val maps: List<StoredMap> get() = mapLibrary.maps

    /** Immagine associata a una definizione, se ce n'e' una. */
    fun portraitOf(definitionId: String): ImageBitmap? {
        val name = library.portraits[definitionId] ?: return null
        return bitmap(name)
    }

    /** Immagine dell'archivio per nome, decodificata una volta sola. */
    fun bitmap(name: String): ImageBitmap? {
        if (name.isBlank()) return null
        return synchronized(decoded) {
            if (decoded.containsKey(name)) return@synchronized decoded[name]
            val image = runCatching { store.readBytes(name)?.decodeToImageBitmap() }.getOrNull()
            decoded[name] = image
            image
        }
    }

    fun portraitName(definitionId: String): String? = library.portraits[definitionId]

    /**
     * Chiede un file all'utente e lo assegna come ritratto.
     *
     * Restituisce true se l'immagine e' stata assegnata. Un errore diventa un
     * messaggio: caricare un ritratto non deve poter far cadere l'applicazione.
     */
    fun assignPortrait(definitionId: String): Boolean = guard {
        val chosen = picker.pick() ?: return@guard false
        importPortrait(definitionId, chosen)
    }

    /** Assegna un ritratto tramite il selettore asincrono della piattaforma. */
    fun assignPortraitAsync(definitionId: String, onComplete: (Boolean) -> Unit = {}) {
        message = null
        picker.pickAsync(
            onPicked = { chosen ->
                if (chosen == null) {
                    message = "Selezione immagine annullata."
                    onComplete(false)
                } else {
                    onComplete(guard { importPortrait(definitionId, chosen) })
                }
            },
            onError = { failure ->
                message = pickerError(failure)
                onComplete(false)
            },
        )
    }

    /** Chiede un file all'utente e lo restituisce come sfondo di mappa. */
    fun pickBackground(): String? {
        var result: String? = null
        guard {
            val chosen = picker.pick() ?: return@guard false
            result = store.importImage(chosen)
            revision++
            message = "Sfondo caricato."
            true
        }
        return result
    }

    /** Sceglie e importa uno sfondo senza bloccare il thread dell'interfaccia. */
    fun pickBackgroundAsync(onPicked: (String?) -> Unit) {
        message = null
        picker.pickAsync(
            onPicked = { chosen ->
                if (chosen == null) {
                    message = "Selezione immagine annullata."
                    onPicked(null)
                } else {
                    var storedName: String? = null
                    guard {
                        storedName = store.importImage(chosen)
                        revision++
                        message = "Sfondo caricato."
                        true
                    }
                    onPicked(storedName)
                }
            },
            onError = { failure ->
                message = pickerError(failure)
                onPicked(null)
            },
        )
    }

    /**
     * Chiede un file all'utente e lo aggiunge all'archivio delle mappe.
     *
     * A differenza di uno sfondo scelto al volo, una mappa dell'archivio si
     * riusa fra le partite: la si carica una volta e la si ritrova per nome. Il
     * nome iniziale viene dal file scelto e resta rinominabile.
     */
    fun importMapAsync(onComplete: (StoredMap?) -> Unit = {}) {
        message = null
        picker.pickAsync(
            onPicked = { chosen ->
                if (chosen == null) {
                    message = "Selezione immagine annullata."
                    onComplete(null)
                } else {
                    var created: StoredMap? = null
                    guard {
                        val stored = store.importImage(chosen)
                        val entry = StoredMap("map-${UUID.randomUUID()}", mapDisplayName(chosen), stored)
                        val updated = mapLibrary.copy(maps = mapLibrary.maps + entry)
                        store.saveMapLibrary(updated)
                        mapLibrary = updated
                        revision++
                        created = entry
                        message = "Mappa «${entry.name}» aggiunta all'archivio."
                        true
                    }
                    onComplete(created)
                }
            },
            onError = { failure ->
                message = pickerError(failure)
                onComplete(null)
            },
        )
    }

    /** Rinomina una mappa dell'archivio; un nome vuoto viene ignorato. */
    fun renameMap(id: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        guard {
            val updated = mapLibrary.copy(
                maps = mapLibrary.maps.map { if (it.id == id) it.copy(name = trimmed) else it },
            )
            store.saveMapLibrary(updated)
            mapLibrary = updated
            true
        }
    }

    /**
     * Elimina una mappa dall'archivio.
     *
     * Il file dell'immagine viene rimosso dal disco solo se nessun'altra voce lo
     * condivide, cosi' non si cancella per sbaglio l'immagine ancora usata altrove.
     */
    fun deleteMap(id: String) {
        guard {
            val entry = mapLibrary.maps.firstOrNull { it.id == id } ?: return@guard false
            val updated = mapLibrary.copy(maps = mapLibrary.maps - entry)
            store.saveMapLibrary(updated)
            mapLibrary = updated
            if (mapLibrary.maps.none { it.image == entry.image }) {
                store.deleteImage(entry.image)
                synchronized(decoded) { decoded.remove(entry.image) }
            }
            revision++
            message = "Mappa «${entry.name}» eliminata."
            true
        }
    }

    /** Nome iniziale di una mappa: il nome del file scelto, senza estensione. */
    private fun mapDisplayName(source: Path): String {
        val file = source.fileName?.toString().orEmpty()
        return file.substringBeforeLast('.', file).ifBlank { "Mappa senza nome" }
    }

    fun clearPortrait(definitionId: String) {
        guard {
            val updated = library.copy(portraits = library.portraits - definitionId)
            store.saveLibrary(updated)
            library = updated
            revision++
            true
        }
    }

    fun dismissMessage() {
        message = null
    }

    private fun importPortrait(definitionId: String, chosen: Path): Boolean {
        val stored = store.importImage(chosen)
        val updated = library.copy(portraits = library.portraits + (definitionId to stored))
        store.saveLibrary(updated)
        library = updated
        revision++
        message = "Ritratto assegnato."
        return true
    }

    private fun pickerError(failure: Throwable): String =
        "Impossibile aprire l'immagine: ${failure.message ?: failure::class.simpleName ?: "errore sconosciuto"}"

    private inline fun guard(block: () -> Boolean): Boolean = try {
        block()
    } catch (failure: IllegalArgumentException) {
        message = failure.message
        false
    } catch (failure: java.io.IOException) {
        message = "Errore su disco: ${failure.message}"
        false
    }
}

/** Decodifica fuori dal thread dell'interfaccia e riusa la cache del repository. */
@Composable
fun PortraitRepository.rememberBitmap(name: String?): ImageBitmap? {
    val currentRevision = revision
    return produceState<ImageBitmap?>(
        initialValue = null,
        key1 = name,
        key2 = currentRevision,
    ) {
        value = if (name.isNullOrBlank()) null else withContext(Dispatchers.IO) { bitmap(name) }
    }.value
}

@Composable
fun PortraitRepository.rememberPortrait(definitionId: String): ImageBitmap? =
    rememberBitmap(portraitName(definitionId))
