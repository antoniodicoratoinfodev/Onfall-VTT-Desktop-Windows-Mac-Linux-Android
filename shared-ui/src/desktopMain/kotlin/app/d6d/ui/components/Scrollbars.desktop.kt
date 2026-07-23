package app.d6d.ui.components

import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.d6d.ui.theme.Palette

/** Scrollbar desktop: bronzo a riposo, oro sotto il mouse, spessore minimo. */
@Composable
actual fun PanelScrollbar(state: LazyListState, modifier: Modifier) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(state),
        modifier = modifier,
        style = ScrollbarStyle(
            minimalHeight = 24.dp,
            thickness = 6.dp,
            shape = RoundedCornerShape(3.dp),
            hoverDurationMillis = 240,
            unhoverColor = Palette.Bronze.copy(alpha = 0.35f),
            hoverColor = Palette.Gold.copy(alpha = 0.65f),
        ),
    )
}
