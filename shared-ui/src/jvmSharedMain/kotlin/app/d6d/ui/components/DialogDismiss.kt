package app.d6d.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/** Chiude un dialogo adattivo quando si tocca il suo sfondo a tutta finestra. */
fun Modifier.dismissDialogOnTap(onDismiss: () -> Unit): Modifier =
    pointerInput(onDismiss) {
        detectTapGestures(onTap = { onDismiss() })
    }

/** Impedisce ai tocchi dentro il pannello di raggiungere lo sfondo dismissibile. */
fun Modifier.keepDialogOpenOnTap(): Modifier =
    pointerInput(Unit) {
        detectTapGestures(onTap = { })
    }
