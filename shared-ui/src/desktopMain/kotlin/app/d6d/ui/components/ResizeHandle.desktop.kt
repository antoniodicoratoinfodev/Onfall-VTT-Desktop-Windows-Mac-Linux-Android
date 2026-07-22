package app.d6d.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import java.awt.Cursor

/** Sul desktop il bordo trascinabile mostra il cursore di ridimensionamento est-ovest. */
actual fun Modifier.horizontalResizeCursor(): Modifier =
    pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))

/** La barra fra due pannelli impilati mostra il cursore di ridimensionamento nord-sud. */
actual fun Modifier.verticalResizeCursor(): Modifier =
    pointerHoverIcon(PointerIcon(Cursor(Cursor.N_RESIZE_CURSOR)))
