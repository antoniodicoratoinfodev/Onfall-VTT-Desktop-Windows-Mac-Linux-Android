package app.d6d.ui.images

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import app.d6d.sheet.ImageStore
import app.d6d.sheet.PortraitLibrary
import java.nio.file.Path

/**
 * Scelta di un file dalla piattaforma.
 *
 * Desktop e Android aprono selettori completamente diversi, quindi la scelta viene
 * iniettata dalla shell invece di essere risolta nel codice condiviso. Una shell
 * che non sa scegliere file restituisce semplicemente null.
 */
fun interface FilePicker {
    fun pick(): Path?
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

    var message by mutableStateOf<String?>(null)

    private val decoded = mutableMapOf<String, ImageBitmap?>()

    /** Cambia a ogni import: fa ricomporre chi disegna le immagini. */
    var revision by mutableStateOf(0)
        private set

    init {
        runCatching { library = store.loadLibrary() }
    }

    /** Immagine associata a una definizione, se ce n'e' una. */
    fun portraitOf(definitionId: String): ImageBitmap? {
        val name = library.portraits[definitionId] ?: return null
        return bitmap(name)
    }

    /** Immagine dell'archivio per nome, decodificata una volta sola. */
    fun bitmap(name: String): ImageBitmap? {
        if (name.isBlank()) return null
        return decoded.getOrPut(name) {
            runCatching { store.readBytes(name)?.decodeToImageBitmap() }
                .onFailure { message = "Immagine illeggibile: $name" }
                .getOrNull()
        }
    }

    /**
     * Chiede un file all'utente e lo assegna come ritratto.
     *
     * Restituisce true se l'immagine e' stata assegnata. Un errore diventa un
     * messaggio: caricare un ritratto non deve poter far cadere l'applicazione.
     */
    fun assignPortrait(definitionId: String): Boolean = guard {
        val chosen = picker.pick() ?: return@guard false
        val stored = store.importImage(chosen)
        val updated = library.copy(portraits = library.portraits + (definitionId to stored))
        store.saveLibrary(updated)
        library = updated
        revision++
        message = "Ritratto assegnato."
        true
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
