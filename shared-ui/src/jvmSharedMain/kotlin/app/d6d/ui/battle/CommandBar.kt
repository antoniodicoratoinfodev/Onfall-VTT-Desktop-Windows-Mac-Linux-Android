package app.d6d.ui.battle

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.d6d.domain.combat.ActivationCost
import app.d6d.domain.combat.AutomationStatus
import app.d6d.domain.combat.CombatStatus
import app.d6d.domain.combat.D20Mode
import app.d6d.sheet.feetWithMetres
import app.d6d.sheet.withMetricFeet
import app.d6d.ui.components.Eyebrow
import app.d6d.ui.components.ScaledDensity
import app.d6d.ui.layout.LocalUiLayout
import app.d6d.ui.state.BattleViewModel
import app.d6d.ui.theme.Palette

// Altezza a cui i comandi hanno la dimensione naturale (scala 1). Oltre questa la
// fascia ingrandisce abilita', oggetti e strumenti; sotto li rimpicciolisce.
private val COMMAND_BAR_BASE = 176.dp

/**
 * Pulsante in stile gioco: bordo acceso, riempimento scuro, etichetta marcata.
 *
 * `dense` ne offre una versione compatta — testo e spazi ridotti, nessuna soglia
 * di 48 dp — per le barre di controllo fitte come quella della mappa, mantenendo
 * lo stesso linguaggio visivo dei comandi grandi.
 */
@Composable
fun GameButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Palette.Gold,
    enabled: Boolean = true,
    subtitle: String? = null,
    selected: Boolean = false,
    dense: Boolean = false,
    // Azione principale della schermata: pillola d'oro piena, come lo stato
    // selezionato, ma senza dichiararsi "selezionata" all'accessibilita'.
    primary: Boolean = false,
) {
    val tint = if (enabled) accent else Palette.TextFaint
    val shape = RoundedCornerShape(if (dense) 5.dp else 7.dp)
    // Linguaggio dei comandi: superficie scura con un gradiente appena in
    // rilievo e bordo sottile; lo stato selezionato (o l'azione principale)
    // diventa una pillola d'oro battuto — piu' luminosa in cima — con testo scuro.
    val solid = selected || (primary && enabled)
    val fill = when {
        solid -> Brush.verticalGradient(listOf(Palette.GoldBright, Palette.Gold))
        enabled -> Brush.verticalGradient(listOf(Palette.SurfaceHigh, Palette.Surface))
        else -> Brush.verticalGradient(listOf(Palette.Surface, Palette.Surface))
    }
    // Le azioni dorate meritano un bordo di bronzo; le altre restano sulla linea.
    val restingBorder = when {
        solid -> Palette.GoldBright.copy(alpha = 0.85f)
        accent == Palette.Gold || accent == Palette.GoldBright -> Palette.Bronze.copy(alpha = if (enabled) 0.9f else 0.5f)
        else -> Palette.Line.copy(alpha = if (enabled) 1f else 0.55f)
    }

    // Sul desktop il pulsante risponde prima del clic: il bordo si accende d'oro
    // al passaggio del mouse e la pressione lo comprime appena, come un tasto
    // fisico. Sul touch resta solo la compressione.
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> restingBorder
            solid && hovered -> Palette.GoldBright
            hovered -> Palette.Gold.copy(alpha = 0.75f)
            else -> restingBorder
        },
        animationSpec = tween(140),
        label = "gameButtonBorder",
    )
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.96f else 1f,
        animationSpec = tween(90),
        label = "gameButtonPress",
    )

    val labelColor = if (solid) Palette.Abyss else tint
    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .then(if (dense) Modifier else Modifier.minimumInteractiveComponentSize())
            .semantics { this.selected = selected }
            .background(fill, shape)
            .border(1.dp, borderColor, shape)
            .hoverable(interaction, enabled = enabled)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
            ) { onClick() }
            .padding(
                horizontal = if (dense) 8.dp else 13.dp,
                vertical = if (dense) 4.dp else 8.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            text = label,
            color = labelColor,
            fontWeight = FontWeight.Bold,
            style = if (dense) MaterialTheme.typography.bodySmall else MaterialTheme.typography.titleMedium,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = if (solid) Palette.Abyss.copy(alpha = 0.62f) else Palette.TextMuted,
                style = if (dense) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private val ActivationCost.italianLabel: String
    get() = when (this) {
        ActivationCost.ACTION -> "Azione"
        ActivationCost.BONUS_ACTION -> "Azione Bonus"
        ActivationCost.REACTION -> "Reazione"
        ActivationCost.LEGENDARY_ACTION -> "Azione Leggendaria"
        ActivationCost.NONE -> "Gratuita"
    }

private val D20Mode.italianLabel: String
    get() = when (this) {
        D20Mode.NORMAL -> "Normale"
        D20Mode.ADVANTAGE -> "Vantaggio"
        D20Mode.DISADVANTAGE -> "Svantaggio"
    }

/**
 * Comandi del turno.
 *
 * Le capacita' vengono lette dallo snapshot del combattente attivo: e' il motore
 * a decidere se un'azione e' legale, qui si mostra soltanto cio' che possiede.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommandBar(
    viewModel: BattleViewModel,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val layout = LocalUiLayout.current
    var toolsOpen by remember { mutableStateOf(false) }
    var itemsOpen by remember { mutableStateOf(false) }
    // Il collasso vive nel layout persistito, cosi' si ricorda fra un avvio e
    // l'altro e la maniglia di ridimensionamento sa quando mostrarsi.
    val collapsed = layout.commandsCollapsed
    val scrollState = rememberScrollState()
    val activeId = viewModel.activeActorId
    val abilities = activeId?.let { viewModel.abilities(it) }.orEmpty()
    val budget = activeId?.let { viewModel.budget(it) }
    val combatActive = viewModel.status == CombatStatus.ACTIVE

    BattleToolsDialog(viewModel, open = toolsOpen, onDismiss = { toolsOpen = false })
    BattleItemsDialog(items = sampleBattleItems, open = itemsOpen, onDismiss = { itemsOpen = false })

    // Sul desktop, da espansa, la fascia ha un'altezza fissa scelta dall'utente e il
    // contenuto viene scalato per riempirla: allargandola i pulsanti di abilita',
    // oggetti e strumenti crescono, restringendola calano (e scorrono se non basta).
    val scaled = !compact && !collapsed
    val scale = if (scaled) (layout.commandBarHeight / COMMAND_BAR_BASE).coerceIn(0.6f, 2.4f) else 1f
    val outerModifier = if (scaled) {
        modifier
            .fillMaxWidth()
            .height(layout.commandBarHeight)
            .background(Palette.Night)
            .verticalScroll(scrollState)
    } else {
        modifier
            .fillMaxWidth()
            .background(Palette.Night)
    }

    Box(outerModifier) {
    ScaledDensity(scale) {
    Column(
        Modifier.fillMaxWidth().padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        // Intestazione sempre visibile: turno e bersaglio a capo se manca spazio
        // (responsive), con a destra il tasto per contrarre o riaprire i comandi.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlowRow(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                activeId?.let { Eyebrow("Turno: ${viewModel.name(it)}", Palette.Gold) }
                viewModel.effectiveTargetId()?.let {
                    Text(
                        text = "Bersaglio: ${viewModel.name(it)}",
                        color = Palette.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            CollapseToggle(
                collapsed = collapsed,
                expandedLabel = "Comandi ▾",
                collapsedLabel = "Comandi ▸",
                onToggle = { layout.commandsCollapsed = !layout.commandsCollapsed },
            )
        }

        if (!collapsed) {
        if (viewModel.isSimultaneousTurn) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Chi agisce:", color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
                viewModel.activeCombatantIds.forEach { id ->
                    val selected = id == activeId
                    GameButton(
                        label = viewModel.name(id),
                        accent = if (selected) Palette.Gold else Palette.TextFaint,
                        selected = selected,
                        onClick = { viewModel.selectActiveActor(id) },
                    )
                }
            }
        }

        if (abilities.isEmpty()) {
            Text(
                text = "Nessuna capacità disponibile per questo combattente.",
                color = Palette.TextFaint,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            // Le capacita' vanno a capo per riempire l'altezza scelta per la fascia:
            // allargandola si vedono tutte in griglia, restringendola scorrono.
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                abilities.forEach { ability ->
                    val affordable = combatActive && when (ability.activationCost()) {
                        ActivationCost.ACTION -> budget?.actionAvailable() ?: false
                        ActivationCost.BONUS_ACTION -> budget?.bonusActionAvailable() ?: false
                        ActivationCost.REACTION -> budget?.reactionAvailable() ?: false
                        else -> true
                    }
                    val manual = ability.automationStatus() == AutomationStatus.MANUAL_REQUIRED
                    val attackBonus = ability.attackBonus()
                    val bonusText = if (attackBonus >= 0) "+$attackBonus" else attackBonus.toString()
                    GameButton(
                        label = if (manual) ability.name() else "Attacca · ${ability.name()}",
                        subtitle = buildString {
                            append(ability.activationCost().italianLabel)
                            if (manual) {
                                append(" · Risoluzione manuale")
                            } else {
                                append(" · ").append(bonusText)
                                if (ability.rangeFeet() > 0) {
                                    append(" · ").append(feetWithMetres(ability.rangeFeet()))
                                }
                            }
                        },
                        accent = if (manual) Palette.Party else Palette.Gold,
                        enabled = if (manual) combatActive else affordable,
                        onClick = {
                            if (manual) {
                                viewModel.showMessage(
                                    ability.rulesText().withMetricFeet().ifBlank {
                                        "«${ability.name()}» richiede una risoluzione manuale al tavolo."
                                    },
                                )
                            } else {
                                viewModel.attack(ability.id())
                            }
                        },
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // FlowRow invece di una riga rigida: con la barra ristretta i comandi
            // vanno a capo invece di uscire dai bordi.
            FlowRow(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Vantaggio e Svantaggio non si sommano: sono tre stati esclusivi.
                // Il modo attivo resta grigio chiaro, gli altri piu' scuri: la
                // scelta si legge senza colori accesi.
                D20Mode.entries.forEach { mode ->
                    val selected = viewModel.rollMode == mode
                    GameButton(
                        label = mode.italianLabel,
                        accent = if (selected) Palette.TextMuted else Palette.TextFaint,
                        selected = selected,
                        enabled = combatActive,
                        onClick = { viewModel.rollMode = mode },
                    )
                }
                GameButton(
                    label = "Strumenti",
                    subtitle = "Danni, cure e condizioni",
                    accent = Palette.TextMuted,
                    onClick = { toolsOpen = true },
                )
                GameButton(
                    label = "Oggetti",
                    subtitle = "Pozioni, armi ed equipaggiamento",
                    accent = Palette.TextMuted,
                    onClick = { itemsOpen = true },
                )
                GameButton(
                    label = "Annulla",
                    accent = Palette.TextFaint,
                    enabled = viewModel.canUndo,
                    onClick = { viewModel.undo() },
                )
            }

            GameButton(
                label = "Fine turno",
                accent = Palette.Heal,
                enabled = combatActive,
                primary = true,
                onClick = { viewModel.endTurn() },
            )
        }
        } // fine del blocco nascondibile
    }
    }
    }
}

/**
 * Tasto compatto per contrarre o riaprire una barra: stesso linguaggio visivo del
 * resto, ma leggero, cosi' non ruba spazio ai comandi veri.
 */
@Composable
internal fun CollapseToggle(
    collapsed: Boolean,
    expandedLabel: String,
    collapsedLabel: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = if (collapsed) collapsedLabel else expandedLabel,
            color = Palette.TextMuted,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
