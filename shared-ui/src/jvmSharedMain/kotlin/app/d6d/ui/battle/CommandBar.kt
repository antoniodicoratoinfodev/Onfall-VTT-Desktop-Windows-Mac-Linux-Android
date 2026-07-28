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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import app.d6d.domain.combat.AbilityDefinition
import app.d6d.domain.combat.ActivationCost
import app.d6d.domain.combat.AutomationStatus
import app.d6d.domain.combat.CombatStatus
import app.d6d.domain.combat.D20Mode
import app.d6d.sheet.feetWithMetres
import app.d6d.sheet.withMetricFeet
import app.d6d.ui.compendium.italianAbbreviation
import app.d6d.ui.compendium.italianLabel
import app.d6d.ui.components.Chip
import app.d6d.ui.components.Eyebrow
import app.d6d.ui.layout.LocalUiLayout
import app.d6d.ui.state.BattleViewModel
import app.d6d.ui.theme.Palette

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
    role: Role = Role.Button,
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
                role = role,
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
 * Riepilogo del danno leggibile: per ogni componente la formula (quanti dadi, che
 * dado, il modificatore) e come colpisce (il tipo di danno). Piu' componenti si
 * sommano — un colpo che fa taglio e fuoco insieme si legge "1d8+3 tagliente + 1d6
 * fuoco". I danni fissi mostrano il numero secco.
 */
private fun AbilityDefinition.damageSummary(): String =
    damage().joinToString("  +  ") { formula ->
        val amount = if (formula.usesDice()) formula.dice().notation() else formula.fixedAmount().toString()
        "$amount ${formula.type().italianLabel.lowercase()}"
    }

/**
 * Scheda di una capacita': nome e costo in testa, poi le voci che contano — quanto
 * si colpisce, a che gittata, e quanto danno fa e di che tipo — allineate come un
 * piccolo blocco statistiche, cosi' si leggono a colpo d'occhio invece di stare
 * ammucchiate in una riga sola. Tutta la scheda e' il tasto: cliccarla avvia la
 * scelta del bersaglio (o, per le capacita' a risoluzione manuale, apre le regole).
 */
@Composable
private fun AbilityCard(
    ability: AbilityDefinition,
    manual: Boolean,
    enabled: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onHoverChange: (Boolean) -> Unit,
) {
    val accent = when {
        selected -> Palette.GoldBright
        manual -> Palette.Party
        else -> Palette.Gold
    }
    val shape = RoundedCornerShape(7.dp)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val currentOnHoverChange = rememberUpdatedState(onHoverChange)
    LaunchedEffect(hovered) {
        currentOnHoverChange.value(hovered)
    }
    DisposableEffect(Unit) {
        onDispose { currentOnHoverChange.value(false) }
    }
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = tween(90),
        label = "abilityCardPress",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> Palette.Line.copy(alpha = 0.5f)
            selected -> Palette.GoldBright
            hovered -> accent
            else -> Palette.Bronze.copy(alpha = 0.75f)
        },
        animationSpec = tween(140),
        label = "abilityCardBorder",
    )
    val fill = when {
        selected -> Brush.verticalGradient(
            listOf(Palette.GoldDim.copy(alpha = 0.62f), Palette.SurfaceHigh),
        )
        enabled -> Brush.verticalGradient(listOf(Palette.SurfaceHigh, Palette.Surface))
        else -> Brush.verticalGradient(listOf(Palette.Surface, Palette.Surface))
    }

    Column(
        Modifier
            // Larghezza guidata dal contenuto: le voci restano su una riga sola invece
            // di andare a capo e allungare la scheda; se sono tante, scorre la fila.
            .widthIn(min = 150.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(shape)
            .semantics { this.selected = selected }
            .background(fill, shape)
            .border(if (selected) 2.dp else 1.dp, borderColor, shape)
            // Anche una scheda in sola consultazione mostra la propria portata:
            // il clic resta disabilitato, ma il passaggio del mouse e' informativo.
            .hoverable(interaction, enabled = true)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
            ) { onClick() }
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        // Niente fillMaxWidth/weight qui: la scheda vive in una fila che scorre in
        // orizzontale (larghezza non vincolata), quindi si dimensiona sul contenuto.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = ability.name(),
                color = when {
                    selected -> Palette.GoldBright
                    enabled -> Palette.Text
                    else -> Palette.TextFaint
                },
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
            )
            Chip(
                text = ability.activationCost().italianLabel,
                color = if (enabled) accent else Palette.TextFaint,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (!manual && !ability.isArea) {
                val bonus = ability.attackBonus()
                AbilityStat("Colpire", if (bonus >= 0) "+$bonus" else bonus.toString(), enabled)
            }
            if (ability.isArea) {
                AbilityStat("Area", feetWithMetres(ability.areaRadiusFeet()), enabled)
                ability.saveAbility()?.let { AbilityStat("TS", it.italianAbbreviation, enabled) }
            }
            if (ability.rangeFeet() > 0) {
                AbilityStat("Gittata", feetWithMetres(ability.rangeFeet()), enabled)
            }
            val damage = ability.damageSummary()
            if (damage.isNotBlank()) {
                AbilityStat("Danno", damage, enabled)
            }
        }

        if (manual) {
            Text(
                text = "Risoluzione manuale · tocca per le regole",
                color = Palette.TextFaint,
                style = MaterialTheme.typography.labelSmall,
            )
        } else if (selected) {
            Text(
                text = "IN MIRA · RICLICCA PER ANNULLARE",
                color = Palette.GoldBright,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/** Una voce del blocco, in linea: etichetta minuta e valore marcato accanto. */
@Composable
private fun AbilityStat(label: String, value: String, enabled: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = label.uppercase(),
            color = Palette.TextFaint,
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            text = value,
            color = if (enabled) Palette.Text else Palette.TextFaint,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * Comandi del turno.
 *
 * Le capacita' vengono lette dallo snapshot del combattente ispezionato. Solo
 * quando coincide con l'attore reale del turno diventano interattive; negli altri
 * casi restano una consultazione grigia.
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
    val inspectedId = viewModel.inspectedCombatantId
    val abilities = inspectedId?.let { viewModel.abilities(it) }.orEmpty()
    val budget = inspectedId?.let { viewModel.budget(it) }
    val movementRemaining = activeId?.let { viewModel.budget(it)?.movementRemainingFeet() }
    val combatActive = viewModel.status == CombatStatus.ACTIVE
    val displayedActorCanAct = inspectedId?.let(viewModel::canUseAbilitiesOf) == true

    BattleToolsDialog(viewModel, open = toolsOpen, onDismiss = { toolsOpen = false })
    BattleItemsDialog(items = sampleBattleItems, open = itemsOpen, onDismiss = { itemsOpen = false })

    // Sul desktop la fascia ha l'altezza scelta dall'utente. Intestazione e riga dei
    // comandi restano di dimensione fissa e sempre visibili; sono solo le capacita'
    // nel mezzo a prendersi lo spazio in piu' — allargando la fascia se ne vedono di
    // piu' in griglia, non le si ingrandisce fino a far sparire tutto il resto.
    val scaled = !compact && !collapsed
    val outerModifier = if (scaled) {
        modifier
            .fillMaxWidth()
            .height(layout.commandBarHeight)
            .background(Palette.Night.copy(alpha = 0.86f))
    } else {
        modifier
            .fillMaxWidth()
            .background(Palette.Night.copy(alpha = 0.86f))
    }

    Box(outerModifier) {
    Column(
        Modifier.fillMaxWidth()
            .then(if (scaled) Modifier.fillMaxHeight() else Modifier)
            .padding(11.dp),
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
                inspectedId?.takeIf { it != activeId }?.let {
                    Eyebrow("In esame: ${viewModel.name(it)}", Palette.Party)
                }
                viewModel.selectedTargetId?.let {
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
        // Zona centrale: chi agisce e le capacita'. Prende lo spazio che avanza fra
        // intestazione e comandi e, quando le schede non ci stanno tutte, scorre da
        // sola in verticale — e' l'unica parte che scorre, cosi' la riga dei comandi
        // qui sotto resta ancorata e sempre visibile, anche allargando la fascia.
        Column(
            Modifier.fillMaxWidth().then(
                if (scaled) Modifier.weight(1f).verticalScroll(scrollState) else Modifier,
            ),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
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

        if (inspectedId != null && !displayedActorCanAct) {
            Text(
                text = if (viewModel.combatant(inspectedId)?.defeated() == true) {
                    "Solo consultazione · 0 PF, il suo turno viene saltato."
                } else {
                    "Solo consultazione · non è il turno di ${viewModel.name(inspectedId)}."
                },
                color = Palette.TextFaint,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        viewModel.singleTargeting?.let { targeting ->
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Scegli il bersaglio di «${targeting.name}» · " +
                        "riclicca l'abilità o annulla per tornare all'ispezione.",
                    color = Palette.GoldBright,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                )
                GameButton(
                    label = "Annulla mira",
                    accent = Palette.TextMuted,
                    dense = true,
                    onClick = viewModel::cancelSingleTargeting,
                )
            }
        }

        if (abilities.isEmpty()) {
            Text(
                text = "Nessuna capacità disponibile per questo combattente.",
                color = Palette.TextFaint,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            // Griglia di schede che va a capo: con la fascia stretta stanno su una
            // riga e le altre scorrono; allargandola se ne vedono di piu' affiancate.
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                abilities.forEach { ability ->
                    val affordable = displayedActorCanAct && when (ability.activationCost()) {
                        ActivationCost.ACTION -> budget?.actionAvailable() ?: false
                        ActivationCost.BONUS_ACTION -> budget?.bonusActionAvailable() ?: false
                        ActivationCost.REACTION -> budget?.reactionAvailable() ?: false
                        else -> true
                    }
                    val manual = ability.automationStatus() == AutomationStatus.MANUAL_REQUIRED
                    val selected = viewModel.singleTargeting?.abilityId == ability.id() ||
                        viewModel.areaTargeting?.abilityId == ability.id()
                    key(inspectedId, ability.id()) {
                        AbilityCard(
                            ability = ability,
                            manual = manual,
                            enabled = if (manual) displayedActorCanAct else affordable,
                            selected = selected,
                            onClick = {
                                if (manual) {
                                    viewModel.showMessage(
                                        ability.rulesText().withMetricFeet().ifBlank {
                                            "«${ability.name()}» richiede una risoluzione manuale al tavolo."
                                        },
                                    )
                                } else {
                                    // Un'area chiede un punto sulla griglia; una capacita'
                                    // singola chiede una creatura. Nessuna delle due usa
                                    // piu' un bersaglio implicito.
                                    viewModel.beginAbilityTargeting(ability.id())
                                }
                            },
                            onHoverChange = { hovered ->
                                inspectedId?.let { actorId ->
                                    viewModel.setAbilityRangeHovered(actorId, ability.id(), hovered)
                                }
                            },
                        )
                    }
                }
            }
        }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Riga dei comandi ancorata: non scorre mai e va a capo quando la fascia
            // si stringe, cosi' questi tasti restano sempre visibili e a portata.
            FlowRow(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Vantaggio e Svantaggio sono due interruttori esclusivi: se nessuno
                // e' acceso vale la regola normale. Ricliccare quello attivo lo spegne
                // (torna a normale); cliccare l'altro sposta l'evidenza — non si
                // sommano mai.
                listOf(D20Mode.ADVANTAGE, D20Mode.DISADVANTAGE).forEach { mode ->
                    val selected = viewModel.rollMode == mode
                    GameButton(
                        label = mode.italianLabel,
                        accent = if (selected) Palette.TextMuted else Palette.TextFaint,
                        selected = selected,
                        enabled = displayedActorCanAct,
                        dense = true,
                        onClick = {
                            viewModel.rollMode = if (selected) D20Mode.NORMAL else mode
                        },
                    )
                }
                // Compatti e su una riga sola: la fascia comandi resta bassa e lascia
                // spazio alle schede delle capacita' sopra.
                GameButton(
                    label = "Strumenti",
                    accent = Palette.TextMuted,
                    dense = true,
                    onClick = { toolsOpen = true },
                )
                GameButton(
                    label = "Oggetti",
                    accent = Palette.TextMuted,
                    dense = true,
                    onClick = { itemsOpen = true },
                )
            }

            GameButton(
                label = "Movimento residuo",
                subtitle = movementRemaining
                    ?.let { feetWithMetres(it) }
                    ?: "Non disponibile",
                accent = Palette.Party,
                enabled = viewModel.movementReachAvailable,
                selected = viewModel.movementReachVisible,
                dense = true,
                onClick = viewModel::toggleMovementReach,
            )
            GameButton(
                label = if (activeId == null) "Salta turno" else "Fine turno",
                accent = Palette.Heal,
                enabled = combatActive && viewModel.hasStandingCombatants,
                primary = true,
                dense = true,
                onClick = { viewModel.endTurn() },
            )
        }
        } // fine del blocco nascondibile
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
