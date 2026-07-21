package app.d6d.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.d6d.ui.AppRoot

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

        // Offline-first: l'archivio locale dell'app e' la fonte di verita'.
        val dataDirectory = filesDir.toPath()

        setContent {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                AppRoot(
                    dataDirectory = dataDirectory,
                    compact = maxWidth < 1000.dp,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
