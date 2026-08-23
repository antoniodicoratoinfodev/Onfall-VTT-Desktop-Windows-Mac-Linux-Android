package app.d6d.android

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.d6d.ui.AppRoot
import app.d6d.ui.images.FilePicker
import app.d6d.ui.i18n.AppLocale
import app.d6d.ui.theme.Palette
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Shell Android.
 *
 * Riusa motore, stato e schermate del modulo condiviso e sceglie soltanto la
 * forma: telefono in verticale ottiene il layout compatto, un tablet largo
 * ottiene la stessa shell densa del desktop.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        // Offline-first: l'archivio locale dell'app e' la fonte di verita'.
        val dataDirectory = filesDir.toPath()

        setContent {
            val filePicker = rememberAndroidImagePicker()
            var exitRequested by remember { mutableStateOf(false) }

            // Il gesto Indietro e la X desktop devono avere la stessa semantica:
            // prima si completano gli autosave e, se resta una bozza senza nome,
            // l'utente decide esplicitamente se conservarla o abbandonarla.
            BackHandler { exitRequested = true }

            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .background(Palette.Night)
                    .safeDrawingPadding()
                    .imePadding(),
            ) {
                AppRoot(
                    dataDirectory = dataDirectory,
                    compact = maxWidth < 1000.dp,
                    modifier = Modifier.fillMaxSize(),
                    filePicker = filePicker,
                    exitRequested = exitRequested,
                    onExitRequestHandled = { exitRequested = false },
                    onExitConfirmed = ::finish,
                )
            }
        }
    }

    /**
     * Converte il documento scelto dal provider Android in un vero [Path].
     *
     * L'archivio condiviso copia subito quel file nella cartella dati definitiva;
     * il temporaneo puo' quindi essere eliminato appena termina il callback.
     */
    @Composable
    private fun rememberAndroidImagePicker(): FilePicker {
        val pending = remember { PendingImagePickHolder() }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val request = pending.request ?: return@rememberLauncherForActivityResult
            pending.request = null

            if (uri == null) {
                request.onPicked(null)
                return@rememberLauncherForActivityResult
            }

            Thread(
                {
                    val temporary = try {
                        copyToTemporaryImage(uri)
                    } catch (failure: Throwable) {
                        runOnUiThread { request.onError(failure) }
                        return@Thread
                    }

                    runOnUiThread {
                        try {
                            request.onPicked(temporary)
                        } catch (failure: Throwable) {
                            runCatching { Files.deleteIfExists(temporary) }
                            request.onError(failure)
                        }
                    }
                },
                "onfall-image-import",
            ).start()
        }

        return remember(launcher) {
            object : FilePicker {
                // Android deve attendere Activity Result: la variante sincrona
                // resta volutamente vuota per rispettare il contratto desktop.
                override fun pick(): Path? = null

                override fun pickAsync(onPicked: (Path?) -> Unit, onError: (Throwable) -> Unit) {
                    pending.request?.onError(
                        IllegalStateException(AppLocale.current.maps.imagePickerAlreadyOpen),
                    )
                    pending.request = PendingImagePick(onPicked, onError)
                    try {
                        launcher.launch(arrayOf("image/*"))
                    } catch (failure: Throwable) {
                        pending.request = null
                        onError(failure)
                    }
                }

                override fun release(path: Path) {
                    runCatching { Files.deleteIfExists(path) }
                }
            }
        }
    }

    private fun copyToTemporaryImage(uri: Uri): Path {
        val displayName = contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        val extension = supportedExtension(displayName)
            ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(contentResolver.getType(uri))
                ?.lowercase()
                ?.takeIf(SUPPORTED_IMAGE_EXTENSIONS::contains)
            ?: "png"

        val temporaryDirectory = cacheDir.resolve("selected-images").also { it.mkdirs() }
        val temporary = kotlin.io.path.createTempFile(
            directory = temporaryDirectory.toPath(),
            prefix = "onfall-image-",
            suffix = ".$extension",
        )
        val input = contentResolver.openInputStream(uri)
            ?: throw IOException(AppLocale.current.maps.imageProviderReturnedNoData)
        input.use { source ->
            Files.newOutputStream(temporary).use(source::copyTo)
        }
        return temporary
    }

    private fun supportedExtension(displayName: String?): String? = displayName
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.lowercase()
        ?.takeIf(SUPPORTED_IMAGE_EXTENSIONS::contains)
}

private data class PendingImagePick(
    val onPicked: (Path?) -> Unit,
    val onError: (Throwable) -> Unit,
)

private class PendingImagePickHolder {
    var request: PendingImagePick? = null
}

private val SUPPORTED_IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "bmp", "gif")
