package app.d6d.ui.components

import androidx.compose.ui.Modifier

/** Su Android non esiste un cursore: il ridimensionamento avviene col tocco. */
actual fun Modifier.horizontalResizeCursor(): Modifier = this
