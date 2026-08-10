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
import java.util.LinkedHashMap
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /** Rilascia un eventuale file temporaneo prodotto dalla shell. */
    fun release(path: Path) = Unit
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
    private val scope: CoroutineScope? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val maxDecodedBytes: Long = DEFAULT_DECODED_CACHE_BYTES,
    private val maxDecodedEntries: Int = DEFAULT_DECODED_CACHE_ENTRIES,
    loadOnCreate: Boolean = true,
) {

    var library by mutableStateOf(PortraitLibrary())
        private set

    /** Archivio delle mappe caricate dall'utente, riusabili come sfondi. */
    var mapLibrary by mutableStateOf(MapLibrary())
        private set

    var message by mutableStateOf<String?>(null)

    private data class CachedBitmap(val image: ImageBitmap?, val estimatedBytes: Long)

    private val decoded = LinkedHashMap<String, CachedBitmap>(16, 0.75f, true)
    private var decodedBytes = 0L
    private val ioMutex = Mutex()

    internal val decodedCacheSize: Int get() = synchronized(decoded) { decoded.size }
    internal val decodedCacheBytes: Long get() = synchronized(decoded) { decodedBytes }

    /** Lettura non bloccante per rimontare una schermata senza un passaggio su IO. */
    internal fun cachedBitmap(name: String): ImageBitmap? =
        synchronized(decoded) { decoded[name]?.image }

    /** Cambia a ogni import: fa ricomporre chi disegna le immagini. */
    var revision by mutableStateOf(0)
        private set

    init {
        require(maxDecodedBytes >= 0) { "Il limite in byte della cache non può essere negativo" }
        require(maxDecodedEntries >= 0) { "Il numero massimo di immagini in cache non può essere negativo" }
        if (loadOnCreate) {
            runCatching { library = store.loadLibrary() }
            runCatching { mapLibrary = store.loadMapLibrary() }
        }
    }

    /** Rilegge gli indici; la shell lo usa sul dispatcher I/O durante l'avvio. */
    fun reload() {
        library = store.loadLibrary()
        mapLibrary = store.loadMapLibrary()
        revision++
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
        synchronized(decoded) {
            decoded[name]?.let { return it.image }
            if (decoded.containsKey(name)) return null
        }
        val image = runCatching { store.readBytes(name)?.decodeToImageBitmap() }.getOrNull()
        cache(name, image)
        return image
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
        try {
            applyPortraitImport(persistPortrait(definitionId, chosen))
            true
        } finally {
            picker.release(chosen)
        }
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
                    dispatchPicked(
                        chosen = chosen,
                        operation = { persistPortrait(definitionId, chosen) },
                        onSuccess = { result ->
                            applyPortraitImport(result)
                            onComplete(true)
                        },
                        onFailure = { failure ->
                            message = operationError(failure)
                            onComplete(false)
                        },
                    )
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
            try {
                result = store.importImage(chosen)
                revision++
                message = "Sfondo caricato."
                true
            } finally {
                picker.release(chosen)
            }
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
                    dispatchPicked(
                        chosen = chosen,
                        operation = { store.importImage(chosen) },
                        onSuccess = { storedName ->
                            revision++
                            message = "Sfondo caricato."
                            onPicked(storedName)
                        },
                        onFailure = { failure ->
                            message = operationError(failure)
                            onPicked(null)
                        },
                    )
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
                    dispatchPicked(
                        chosen = chosen,
                        operation = { persistMap(chosen) },
                        onSuccess = { result ->
                            mapLibrary = result.library
                            revision++
                            message = "Mappa «${result.entry.name}» aggiunta all'archivio."
                            onComplete(result.entry)
                        },
                        onFailure = { failure ->
                            message = operationError(failure)
                            onComplete(null)
                        },
                    )
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
        val updated = mapLibrary.copy(
            maps = mapLibrary.maps.map { if (it.id == id) it.copy(name = trimmed) else it },
        )
        dispatchIo(
            operation = { store.saveMapLibrary(updated) },
            onSuccess = { mapLibrary = updated },
            onFailure = { message = operationError(it) },
        )
    }

    /**
     * Elimina una mappa dall'archivio.
     *
     * Il file dell'immagine viene rimosso dal disco solo se nessun'altra voce lo
     * condivide, cosi' non si cancella per sbaglio l'immagine ancora usata altrove.
     */
    fun deleteMap(id: String) {
        val entry = mapLibrary.maps.firstOrNull { it.id == id } ?: return
        val updated = mapLibrary.copy(maps = mapLibrary.maps - entry)
        val deleteImage = updated.maps.none { it.image == entry.image } &&
            library.portraits.values.none { it == entry.image }
        dispatchIo(
            operation = {
                store.saveMapLibrary(updated)
                if (deleteImage) runCatching { store.deleteImage(entry.image) }
            },
            onSuccess = {
                mapLibrary = updated
                if (deleteImage) removeCached(entry.image)
                revision++
                message = "Mappa «${entry.name}» eliminata."
            },
            onFailure = { message = operationError(it) },
        )
    }

    /** Nome iniziale di una mappa: il nome del file scelto, senza estensione. */
    private fun mapDisplayName(source: Path): String {
        val file = source.fileName?.toString().orEmpty()
        return file.substringBeforeLast('.', file).ifBlank { "Mappa senza nome" }
    }

    fun clearPortrait(definitionId: String) {
        val previous = library.portraits[definitionId]
        val updated = library.copy(portraits = library.portraits - definitionId)
        val deleteImage = previous != null &&
            updated.portraits.values.none { it == previous } &&
            mapLibrary.maps.none { it.image == previous }
        val obsolete = previous.takeIf { deleteImage }
        dispatchIo(
            operation = {
                store.saveLibrary(updated)
                obsolete?.let { runCatching { store.deleteImage(it) } }
            },
            onSuccess = {
                library = updated
                obsolete?.let(::removeCached)
                revision++
            },
            onFailure = { message = operationError(it) },
        )
    }

    fun dismissMessage() {
        message = null
    }

    private data class PortraitImport(val library: PortraitLibrary, val stored: String, val previous: String?)

    private data class MapImport(val library: MapLibrary, val entry: StoredMap)

    private fun persistPortrait(definitionId: String, chosen: Path): PortraitImport {
        val stored = store.importImage(chosen)
        val previous = library.portraits[definitionId]
        val updated = library.copy(portraits = library.portraits + (definitionId to stored))
        try {
            store.saveLibrary(updated)
        } catch (failure: Exception) {
            runCatching { store.deleteImage(stored) }
            throw failure
        }
        if (
            previous != null &&
            updated.portraits.values.none { it == previous } &&
            mapLibrary.maps.none { it.image == previous }
        ) {
            runCatching { store.deleteImage(previous) }
        }
        return PortraitImport(updated, stored, previous)
    }

    private fun applyPortraitImport(result: PortraitImport) {
        library = result.library
        result.previous?.takeIf { it != result.stored }?.let(::removeCached)
        revision++
        message = "Ritratto assegnato."
    }

    private fun persistMap(chosen: Path): MapImport {
        val stored = store.importImage(chosen)
        val entry = StoredMap("map-${UUID.randomUUID()}", mapDisplayName(chosen), stored)
        val updated = mapLibrary.copy(maps = mapLibrary.maps + entry)
        try {
            store.saveMapLibrary(updated)
        } catch (failure: Exception) {
            runCatching { store.deleteImage(stored) }
            throw failure
        }
        return MapImport(updated, entry)
    }

    private fun pickerError(failure: Throwable): String =
        "Impossibile aprire l'immagine: ${failure.message ?: failure::class.simpleName ?: "errore sconosciuto"}"

    private fun operationError(failure: Throwable): String = when (failure) {
        is IllegalArgumentException -> failure.message ?: "Immagine non valida."
        is java.io.IOException -> "Errore su disco: ${failure.message}"
        else -> pickerError(failure)
    }

    private fun <T> dispatchPicked(
        chosen: Path,
        operation: () -> T,
        onSuccess: (T) -> Unit,
        onFailure: (Throwable) -> Unit,
    ) = dispatchIo(
        operation = {
            try {
                operation()
            } finally {
                picker.release(chosen)
            }
        },
        onSuccess = onSuccess,
        onFailure = onFailure,
    )

    private fun <T> dispatchIo(
        operation: () -> T,
        onSuccess: (T) -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        val activeScope = scope
        if (activeScope == null) {
            try {
                onSuccess(operation())
            } catch (failure: Exception) {
                onFailure(failure)
            }
            return
        }
        activeScope.launch {
            try {
                val result = withContext(ioDispatcher) {
                    ioMutex.withLock { operation() }
                }
                onSuccess(result)
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                onFailure(failure)
            }
        }
    }

    private fun cache(name: String, image: ImageBitmap?) {
        val estimatedBytes = image?.let { it.width.toLong() * it.height.toLong() * 4L } ?: 0L
        synchronized(decoded) {
            decoded.remove(name)?.let { decodedBytes -= it.estimatedBytes }
            if (estimatedBytes <= maxDecodedBytes) {
                decoded[name] = CachedBitmap(image, estimatedBytes)
                decodedBytes += estimatedBytes
            }
            while (decoded.size > maxDecodedEntries || decodedBytes > maxDecodedBytes) {
                val eldest = decoded.entries.iterator().next()
                decodedBytes -= eldest.value.estimatedBytes
                decoded.remove(eldest.key)
            }
        }
    }

    private fun removeCached(name: String) {
        synchronized(decoded) {
            decoded.remove(name)?.let { decodedBytes -= it.estimatedBytes }
        }
    }

    private inline fun guard(block: () -> Boolean): Boolean = try {
        block()
    } catch (failure: IllegalArgumentException) {
        message = failure.message
        false
    } catch (failure: java.io.IOException) {
        message = "Errore su disco: ${failure.message}"
        false
    }

    companion object {
        private const val DEFAULT_DECODED_CACHE_BYTES = 64L * 1024 * 1024
        private const val DEFAULT_DECODED_CACHE_ENTRIES = 64
    }
}

/** Decodifica fuori dal thread dell'interfaccia e riusa la cache del repository. */
@Composable
fun PortraitRepository.rememberBitmap(name: String?): ImageBitmap? {
    val currentRevision = revision
    val cached = name?.takeIf { it.isNotBlank() }?.let(::cachedBitmap)
    return produceState<ImageBitmap?>(
        initialValue = cached,
        key1 = name,
        key2 = currentRevision,
    ) {
        value = when {
            name.isNullOrBlank() -> null
            cached != null -> cached
            else -> withContext(Dispatchers.IO) { bitmap(name) }
        }
    }.value
}

@Composable
fun PortraitRepository.rememberPortrait(definitionId: String): ImageBitmap? =
    rememberBitmap(portraitName(definitionId))
