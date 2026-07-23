package app.d6d.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Su Android non si disegna nulla: lo scorrimento a tocco non vuole barre fisse. */
@Composable
actual fun PanelScrollbar(state: LazyListState, modifier: Modifier) = Unit
