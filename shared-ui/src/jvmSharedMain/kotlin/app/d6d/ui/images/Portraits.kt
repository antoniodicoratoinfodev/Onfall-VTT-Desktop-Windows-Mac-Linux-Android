package app.d6d.ui.images

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import app.d6d.sheet.ImageStore
import app.d6d.sheet.MapLibrary
import app.d6d.sheet.PortraitLibrary
import app.d6d.sheet.PortraitFraming
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
import app.d6d.ui.i18n.AppLocale

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
 * Una mappa che arriva insieme al programma.
 *
 * I byte si leggono su richiesta e non alla costruzione: sono qualche megabyte
 * l'una, e chi ha gia' tutte le mappe installate non deve leggerne nemmeno una.
 */
class MapSeed(
    val id: String,
    val name: String,
    val fileName: String,
    val bytes: suspend () -> ByteArray,
)

/**
 * Ritratti degli attori e sfondi delle mappe.
 *
 * Le immagini decodificate restano in cache: ridisegnare la mappa a ogni fotogramma
 * non deve rileggere e ridecodificare i file. La decodifica passa da [decodeSampled],
 * che legge dal disco e sottocampiona: una battlemap in alta risoluzione non deve
 * entrare in memoria per intero solo per essere disegnata su uno schermo.
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

    /** Vocabolario in uso: qui non arriva `LocalStrings`, siamo fuori da Compose. */
    private val words get() = AppLocale.current.maps

    var library by mutableStateOf(PortraitLibrary())
        private set

    /** Archivio delle mappe caricate dall'utente, riusabili come sfondi. */
    var mapLibrary by mutableStateOf(MapLibrary())
        private set

    /**
     * Vero quando l'indice delle mappe e' stato letto davvero.
     *
     * Il valore iniziale di [mapLibrary] e un archivio vuoto letto da disco sono
     * indistinguibili, e la differenza conta: solo il secondo autorizza a riscrivere
     * `mappe.json`.
     */
    private var mapLibraryLoaded = false

    var message by mutableStateOf<String?>(null)

    /** I messaggi sono gia' risolti: al cambio lingua non devono restare indietro. */
    internal fun onLanguageChanged() {
        message = null
    }

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
            runCatching {
                mapLibrary = store.loadMapLibrary()
                mapLibraryLoaded = true
            }
        }
    }

    /** Rilegge gli indici; la shell lo usa sul dispatcher I/O durante l'avvio. */
    fun reload() {
        library = store.loadLibrary()
        mapLibrary = store.loadMapLibrary()
        mapLibraryLoaded = true
        revision++
    }

    /** Cartella dove vivono i file delle mappe, da mostrare a chi vuole copiarcene. */
    val mapsDirectory: Path get() = store.mapsDirectory

    /**
     * Mette l'archivio in pari con la cartella delle mappe.
     *
     * Fa due cose che sono la stessa cosa vista da due lati. Installa le mappe che
     * arrivano col programma, cosi' l'archivio non e' vuoto al primo avvio; e adotta
     * i file che trova nella cartella e non conosce, cosi' aggiungere una mappa
     * copiandocela dentro funziona quanto caricarla dal selettore — che e' il motivo
     * per cui il percorso della cartella e' scritto in chiaro nell'Archivio mappe.
     *
     * Nessuno dei due passaggi riporta indietro cio' che l'utente ha tolto: una mappa
     * inclusa eliminata resta eliminata perche' il suo identificativo rimane fra
     * quelle installate, e un file eliminato dalla cartella non c'e' piu' da adottare.
     */
    suspend fun syncMaps(seeds: List<MapSeed> = emptyList()) {
        // Non si sincronizza su un indice mai letto. Questa funzione **riscrive**
        // `mappe.json` a partire da cio' che ha in memoria: se la lettura all'avvio
        // e' fallita, cio' che ha in memoria e' un archivio vuoto, e lo salverebbe
        // sopra le mappe di chi gioca.
        if (!mapLibraryLoaded) return

        val current = mapLibrary
        // Una mappa inclusa si salta quando c'e' gia': per identificativo, o perche'
        // l'archivio ne mostra una con lo stesso nome. Il secondo caso non e' teorico
        // — chi aveva caricato a mano le stesse mappe prima che fossero incluse ne
        // ha una copia con un altro identificativo — e due righe identiche
        // nell'archivio sono peggio di una mappa in meno.
        val alreadyThere = current.maps.mapTo(mutableSetOf()) { it.name.trim().lowercase() }
        val (skipped, pending) = seeds
            .filterNot { it.id in current.installedDefaults }
            .partition { seed ->
                current.maps.any { it.id == seed.id } ||
                    seed.name.trim().lowercase() in alreadyThere
            }

        val updated = withContext(ioDispatcher) {
            // I byte si leggono fuori dal lock: sono decine di megabyte che arrivano
            // dal pacchetto dell'applicazione, e tenerci fermo l'archivio mentre si
            // estraggono non serve a nessuno. Fuori anche dal thread dell'interfaccia,
            // che e' il motivo per cui si legge qui dentro.
            val payloads = pending.map { it to runCatching { it.bytes() }.getOrNull() }
            ioMutex.withLock {
                var library = current
                // Si ricorda solo cio' che e' stato saltato di proposito, perche'
                // quella mappa nell'archivio c'e' gia' e non va rimessa. Un'installazione
                // *fallita* non entra qui: segnarla come installata la condannerebbe a
                // non essere piu' ritentata, e un disco pieno per un minuto diventerebbe
                // una mappa che non arriva mai.
                library = library.copy(
                    installedDefaults = (library.installedDefaults + skipped.map { it.id }).distinct(),
                )
                payloads.forEach { (seed, bytes) ->
                    if (bytes == null) return@forEach
                    runCatching {
                        // Il nome con cui e' finita su disco, non quello richiesto: puo'
                        // essere stato cambiato per non coprire un ritratto omonimo.
                        val stored = store.writeMapImage(seed.fileName, bytes)
                        library = library.copy(
                            maps = library.maps + StoredMap(seed.id, seed.name, stored),
                            installedDefaults = library.installedDefaults + seed.id,
                        )
                    }
                }
                val known = library.maps.mapTo(mutableSetOf()) { it.image }
                store.mapImageNames()
                    .filterNot { it in known }
                    .forEach { fileName ->
                        library = library.copy(
                            maps = library.maps + StoredMap(
                                "map-${UUID.randomUUID()}",
                                fileName.substringBeforeLast('.', fileName).ifBlank { words.unnamedMap },
                                fileName,
                            ),
                        )
                    }
                if (library != current) store.saveMapLibrary(library)
                library
            }
        }

        if (updated != current) {
            mapLibrary = updated
            revision++
        }
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
        val image = runCatching {
            store.resolve(name)?.let { decodeSampled(it, MAX_DECODED_PIXELS) }
        }.getOrNull()
        cache(name, image)
        return image
    }

    fun portraitName(definitionId: String): String? = library.portraits[definitionId]

    /** Inquadratura salvata del ritratto; gli archivi precedenti restano centrati. */
    fun portraitFraming(definitionId: String): PortraitFraming =
        library.framings[definitionId]?.normalized() ?: PortraitFraming.DEFAULT

    /**
     * Salva una nuova inquadratura senza modificare il file originale.
     *
     * Lo stato in memoria cambia subito, cosi' il token non lampeggia tornando al
     * centro mentre la piccola libreria JSON viene scritta sul dispatcher I/O.
     */
    fun setPortraitFraming(definitionId: String, framing: PortraitFraming) {
        if (definitionId !in library.portraits) return
        val normalized = framing.normalized()
        val previous = library
        val framings = if (normalized == PortraitFraming.DEFAULT) {
            library.framings - definitionId
        } else {
            library.framings + (definitionId to normalized)
        }
        val updated = library.copy(framings = framings)
        if (updated == previous) return

        library = updated
        revision++
        dispatchIo(
            operation = { store.saveLibrary(updated) },
            onSuccess = {},
            onFailure = { failure ->
                // Non coprire modifiche piu' recenti mentre termina una scrittura.
                if (library == updated) {
                    library = previous
                    revision++
                }
                message = operationError(failure)
            },
        )
    }

    /** Assegna un ritratto tramite il selettore asincrono della piattaforma. */
    fun assignPortraitAsync(definitionId: String, onComplete: (Boolean) -> Unit = {}) {
        message = null
        picker.pickAsync(
            onPicked = { chosen ->
                if (chosen == null) {
                    message = words.imageSelectionCancelled
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

    /** Sceglie e importa uno sfondo senza bloccare il thread dell'interfaccia. */
    fun pickBackgroundAsync(onPicked: (String?) -> Unit) {
        message = null
        picker.pickAsync(
            onPicked = { chosen ->
                if (chosen == null) {
                    message = words.imageSelectionCancelled
                    onPicked(null)
                } else {
                    dispatchPicked(
                        chosen = chosen,
                        operation = { store.importImage(chosen, AppLocale.language) },
                        onSuccess = { storedName ->
                            revision++
                            message = words.backgroundLoaded
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
                    message = words.imageSelectionCancelled
                    onComplete(null)
                } else {
                    dispatchPicked(
                        chosen = chosen,
                        operation = { persistMap(chosen) },
                        onSuccess = { result ->
                            mapLibrary = result.library
                            revision++
                            message = words.mapAdded(result.entry.name)
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
                message = words.mapDeleted(entry.name)
            },
            onFailure = { message = operationError(it) },
        )
    }

    /** Nome iniziale di una mappa: il nome del file scelto, senza estensione. */
    private fun mapDisplayName(source: Path): String {
        val file = source.fileName?.toString().orEmpty()
        return file.substringBeforeLast('.', file).ifBlank { words.unnamedMap }
    }

    fun clearPortrait(definitionId: String) {
        val previous = library.portraits[definitionId]
        val updated = library.copy(
            portraits = library.portraits - definitionId,
            framings = library.framings - definitionId,
        )
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
        val stored = store.importImage(chosen, AppLocale.language)
        val previous = library.portraits[definitionId]
        val updated = library.copy(
            portraits = library.portraits + (definitionId to stored),
            // Una nuova immagine riparte dall'inquadratura completa e centrata.
            framings = library.framings - definitionId,
        )
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
        message = words.portraitAssigned
    }

    private fun persistMap(chosen: Path): MapImport {
        val stored = store.importMapImage(chosen, AppLocale.language)
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

    private fun pickerError(failure: Throwable): String = words.cannotOpenImage(
        failure.message
            ?: failure::class.simpleName
            ?: AppLocale.current.battle.unknownError,
    )

    private fun operationError(failure: Throwable): String = when (failure) {
        is IllegalArgumentException -> failure.message ?: words.invalidImage
        is java.io.IOException -> words.diskError(failure.message.orEmpty())
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
