package app.d6d.ui.i18n

import app.d6d.ui.dice.DiceRollPurpose
import app.d6d.ui.dice.DiceSkinId

/** Vocabolario del vassoio dei dadi e della relativa sezione Impostazioni. */
interface DiceStrings {
    val title: String
    val open: String
    val close: String
    val roll: String
    val rolling: String
    val result: String
    val total: String
    val quantity: String
    val sides: String
    val modifier: String
    val normal: String
    val advantage: String
    val disadvantage: String
    val linked: String
    val unlinked: String
    val linkedHint: String
    val unlinkedHint: String
    val linkedUnavailable: String
    val cancelLinkedRoll: String
    val staleLinkedRoll: String
    val noRollsRequired: String
    val history: String
    val clearHistory: String

    val settingsTitle: String
    val settingsDescription: String
    val rollVisibility: String
    val hiddenRolls: String
    val visibleRolls: String
    val hiddenRollsHint: String
    val visibleRollsHint: String
    val skin: String
    val skinHint: String
    val effects: String
    val fullEffects: String
    val reducedEffects: String
    val fullEffectsHint: String
    val reducedEffectsHint: String

    fun purpose(value: DiceRollPurpose): String
    fun skin(value: DiceSkinId): String
    fun rollFormula(formula: String): String
    fun rollFor(purpose: String, actor: String, target: String): String
    fun diceMore(count: Int): String
}

internal object DiceStringsIt : DiceStrings {
    override val title = "Dadi"
    override val open = "Apri il vassoio dei dadi"
    override val close = "Chiudi i dadi"
    override val roll = "Tira"
    override val rolling = "I dadi rotolano…"
    override val result = "Risultato"
    override val total = "Totale"
    override val quantity = "Quantità"
    override val sides = "Facce"
    override val modifier = "Modificatore"
    override val normal = "Normale"
    override val advantage = "Vantaggio"
    override val disadvantage = "Svantaggio"
    override val linked = "Collegato (Linked)"
    override val unlinked = "Libero (Unlinked)"
    override val linkedHint = "Il risultato richiesto viene applicato dal motore di gioco."
    override val unlinkedHint = "Il risultato resta sul tavolo e non modifica la partita."
    override val linkedUnavailable = "Il collegamento è disponibile quando il gioco attende un tiro visibile."
    override val cancelLinkedRoll = "Annulla tiro collegato"
    override val staleLinkedRoll = "La partita è cambiata: il tiro collegato è stato annullato."
    override val noRollsRequired = "Questa azione non richiede dadi."
    override val history = "Ultimi tiri liberi"
    override val clearHistory = "Svuota cronologia"

    override val settingsTitle = "Dadi"
    override val settingsDescription = "Come il tavolo mostra e anima i tiri del motore."
    override val rollVisibility = "Tiri del gioco"
    override val hiddenRolls = "Nascosti"
    override val visibleRolls = "Visibili"
    override val hiddenRollsHint = "Il gioco risolve subito i tiri come ora e li scrive nel registro."
    override val visibleRollsHint = "I tiri collegati compaiono sul tavolo; quelli del giocatore attendono Tira."
    override val skin = "Aspetto dei dadi"
    override val skinHint = "Il materiale cambia tutti i poliedri senza cambiare i risultati."
    override val effects = "Effetti del tiro"
    override val fullEffects = "Completi"
    override val reducedEffects = "Ridotti"
    override val fullEffectsHint = "Rotazione, rimbalzo, scia e particelle della skin."
    override val reducedEffectsHint = "Una transizione breve senza rotazioni rapide o particelle."

    override fun purpose(value: DiceRollPurpose): String = when (value) {
        DiceRollPurpose.ATTACK -> "Tiro per colpire"
        DiceRollPurpose.DAMAGE -> "Danni"
        DiceRollPurpose.SAVING_THROW -> "Tiro salvezza"
        DiceRollPurpose.HEALING -> "Cura"
        DiceRollPurpose.CONCENTRATION -> "Concentrazione"
        DiceRollPurpose.DEATH_SAVE -> "Tiro contro morte"
        DiceRollPurpose.ABILITY_CHECK -> "Prova di caratteristica"
        DiceRollPurpose.INITIATIVE -> "Iniziativa"
        DiceRollPurpose.FREE -> "Tiro libero"
    }

    override fun skin(value: DiceSkinId): String = when (value) {
        DiceSkinId.RUNIC_OBSIDIAN -> "Ossidiana runica"
        DiceSkinId.DRAGONFORGE -> "Forgia del drago"
        DiceSkinId.MOON_IVORY -> "Avorio lunare"
    }

    override fun rollFormula(formula: String) = "Tira $formula"
    override fun rollFor(purpose: String, actor: String, target: String): String = buildString {
        append(purpose)
        if (actor.isNotBlank()) append(" · ").append(actor)
        if (target.isNotBlank()) append(" → ").append(target)
    }
    override fun diceMore(count: Int) = "+$count altri"
}

internal object DiceStringsEn : DiceStrings {
    override val title = "Dice"
    override val open = "Open the dice tray"
    override val close = "Close dice"
    override val roll = "Roll"
    override val rolling = "The dice are rolling…"
    override val result = "Result"
    override val total = "Total"
    override val quantity = "Quantity"
    override val sides = "Sides"
    override val modifier = "Modifier"
    override val normal = "Normal"
    override val advantage = "Advantage"
    override val disadvantage = "Disadvantage"
    override val linked = "Linked"
    override val unlinked = "Unlinked"
    override val linkedHint = "The requested result is applied by the game engine."
    override val unlinkedHint = "The result stays on the table and does not change the game."
    override val linkedUnavailable = "Linking is available while the game is waiting for a visible roll."
    override val cancelLinkedRoll = "Cancel linked roll"
    override val staleLinkedRoll = "The game changed: the linked roll was cancelled."
    override val noRollsRequired = "This action does not require dice."
    override val history = "Recent unlinked rolls"
    override val clearHistory = "Clear history"

    override val settingsTitle = "Dice"
    override val settingsDescription = "How the table shows and animates engine rolls."
    override val rollVisibility = "Game rolls"
    override val hiddenRolls = "Hidden"
    override val visibleRolls = "Visible"
    override val hiddenRollsHint = "The game resolves rolls immediately as it does now and records them in the log."
    override val visibleRollsHint = "Linked rolls appear on the table; player rolls wait for Roll."
    override val skin = "Dice appearance"
    override val skinHint = "The material changes every polyhedron without changing its result."
    override val effects = "Roll effects"
    override val fullEffects = "Full"
    override val reducedEffects = "Reduced"
    override val fullEffectsHint = "Rotation, bounce, trail and particles from the selected skin."
    override val reducedEffectsHint = "A short transition without fast rotation or particles."

    override fun purpose(value: DiceRollPurpose): String = when (value) {
        DiceRollPurpose.ATTACK -> "Attack roll"
        DiceRollPurpose.DAMAGE -> "Damage"
        DiceRollPurpose.SAVING_THROW -> "Saving throw"
        DiceRollPurpose.HEALING -> "Healing"
        DiceRollPurpose.CONCENTRATION -> "Concentration"
        DiceRollPurpose.DEATH_SAVE -> "Death save"
        DiceRollPurpose.ABILITY_CHECK -> "Ability check"
        DiceRollPurpose.INITIATIVE -> "Initiative"
        DiceRollPurpose.FREE -> "Unlinked roll"
    }

    override fun skin(value: DiceSkinId): String = when (value) {
        DiceSkinId.RUNIC_OBSIDIAN -> "Runic Obsidian"
        DiceSkinId.DRAGONFORGE -> "Dragonforge"
        DiceSkinId.MOON_IVORY -> "Moon Ivory"
    }

    override fun rollFormula(formula: String) = "Roll $formula"
    override fun rollFor(purpose: String, actor: String, target: String): String = buildString {
        append(purpose)
        if (actor.isNotBlank()) append(" · ").append(actor)
        if (target.isNotBlank()) append(" → ").append(target)
    }
    override fun diceMore(count: Int) = "+$count more"
}
