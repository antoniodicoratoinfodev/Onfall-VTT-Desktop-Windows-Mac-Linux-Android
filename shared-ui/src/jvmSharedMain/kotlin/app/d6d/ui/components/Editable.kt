package app.d6d.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.d6d.ui.i18n.strings
import app.d6d.ui.theme.OnfallTheme
import app.d6d.ui.theme.Palette

/**
 * Valore che diventa modificabile con un clic, ma solo quando la modalita' di
 * modifica e' attiva.
 *
 * Fuori dalla modalita' modifica il clic non fa nulla: evita di alterare
 * una scheda per sbaglio nel mezzo di un combattimento. Invio conferma, Esc
 * annulla, e anche perdere il fuoco conferma — cosi' non si perde una modifica
 * cliccando altrove.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditableValue(
    value: String,
    editMode: Boolean,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    fieldWidth: Dp = 74.dp,
    display: @Composable () -> Unit,
) {
    val strings = strings
    var editing by remember { mutableStateOf(false) }
    var draft by remember(value) { mutableStateOf(value) }
    val focusRequester = remember { FocusRequester() }

    // Uscire dalla modalita' modifica chiude ogni campo aperto.
    LaunchedEffect(editMode) {
        if (!editMode) editing = false
    }

    if (editing && editMode) {
        // Il campo non deve confermarsi e chiudersi alla prima callback di fuoco,
        // che all'apertura arriva come "non focalizzato" prima che `requestFocus`
        // faccia effetto: senza questa guardia il campo si richiudeva subito e
        // sembrava impossibile modificare. Si conferma solo dopo aver davvero
        // preso il fuoco e poi averlo perso (clic altrove).
        var acquiredFocus by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        val commit = {
            onCommit(draft)
            editing = false
        }

        BasicTextField(
            value = draft,
            onValueChange = { draft = it },
            singleLine = true,
            textStyle = OnfallTheme.typography.fieldValue.copy(color = Palette.Text),
            cursorBrush = SolidColor(Palette.Gold),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { commit() }),
            modifier = modifier
                .width(fieldWidth)
                .background(Palette.Night, RoundedCornerShape(4.dp))
                .border(1.dp, Palette.Gold, RoundedCornerShape(4.dp))
                .padding(horizontal = 5.dp, vertical = 3.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { state ->
                    if (state.isFocused) {
                        acquiredFocus = true
                    } else if (acquiredFocus && editing) {
                        commit()
                    }
                }
                .onPreviewKeyEvent { event ->
                    when {
                        event.type != KeyEventType.KeyDown -> false
                        event.key == Key.Enter -> { commit(); true }
                        event.key == Key.Escape -> { draft = value; editing = false; true }
                        else -> false
                    }
                },
        )
    } else {
        Box(
            modifier
                .then(
                    if (editMode) {
                        Modifier
                            .background(Palette.Gold.copy(alpha = 0.07f), RoundedCornerShape(4.dp))
                            .border(1.dp, Palette.Gold.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 3.dp)
                            .semantics {
                                role = Role.Button
                                stateDescription = strings.compendium.editableField
                            }
                    } else {
                        Modifier
                    },
                )
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = editMode,
                    onDoubleClick = {
                        draft = value
                        editing = true
                    },
                    onClick = {
                        draft = value
                        editing = true
                    },
                ),
        ) {
            display()
        }
    }
}
