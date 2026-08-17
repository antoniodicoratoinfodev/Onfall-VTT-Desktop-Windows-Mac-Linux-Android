package app.d6d.ui.i18n

/**
 * Le Impostazioni.
 *
 * Qui vive anche il testo che permette di cambiare lingua, ed e' l'unico punto
 * dell'applicazione dove le due lingue si incontrano davvero: il nome di ciascuna
 * si scrive sempre nella lingua stessa (vedi `AppLanguage.endonym`), perche' chi
 * ha aperto l'app in una lingua che non capisce deve poter ritrovare la propria.
 */
interface SettingsStrings {
    val eyebrow: String
    val title: String
    val subtitle: String

    val sectionGeneral: String
    val sectionCursors: String

    val groupLanguage: String
    val groupLanguageDescription: String
    val languageChoice: String
    val languageHint: String

    val groupGame: String
    val groupGameDescription: String
    val cpuSpeed: String

    val groupTable: String
    val groupTableDescription: String
    val turnOrder: String

    val groupLook: String
    val groupLookDescription: String
    val backdrop: String
    val backdropAnimated: String
    val backdropStill: String
    val backdropAnimatedHint: String
    val backdropStillHint: String

    val groupData: String
    val groupDataDescription: String
    val panelLayout: String
    val panelLayoutHint: String
    val resetLayout: String
    val dataFolder: String

    val resetLayoutTitle: String
    val resetLayoutBody: String

    val cpuSpeedSlow: String
    val cpuSpeedNormal: String
    val cpuSpeedFast: String
    val cpuSpeedInstant: String
    val cpuPaceSlow: String
    val cpuPaceNormal: String
    val cpuPaceFast: String
    val cpuPaceInstant: String

    val turnOrderHidden: String
    val turnOrderOnly: String
    val turnOrderWithInitiative: String
    val turnOrderHiddenHint: String
    val turnOrderOnlyHint: String
    val turnOrderWithInitiativeHint: String
}

internal object SettingsStringsIt : SettingsStrings {
    override val eyebrow = "PREFERENZE DELL'APPLICAZIONE"
    override val title = "Impostazioni"
    override val subtitle = "Valgono per tutte le partite, anche per quelle già aperte, " +
        "e restano come le hai lasciate fra un avvio e l'altro."

    override val sectionGeneral = "Generali"
    override val sectionCursors = "Cursori"

    override val groupLanguage = "Lingua"
    override val groupLanguageDescription = "In che lingua parla l'applicazione."
    override val languageChoice = "Lingua dell'interfaccia"
    override val languageHint = "Cambia subito, ovunque, senza riavviare. Le distanze " +
        "seguono la lingua: metri in italiano, piedi in inglese."

    override val groupGame = "Gioco"
    override val groupGameDescription = "Come si vede giocare la parte avversaria."
    override val cpuSpeed = "Ritmo della CPU"

    override val groupTable = "Tavolo"
    override val groupTableDescription = "Cosa mostra la fascia sopra la mappa."
    override val turnOrder = "Ordine dei turni"

    override val groupLook = "Aspetto"
    override val groupLookDescription = "La cornice attorno al gioco."
    override val backdrop = "Fondale"
    override val backdropAnimated = "Animato"
    override val backdropStill = "Fermo"
    override val backdropAnimatedHint = "Braci e bagliore si muovono piano dietro le schermate."
    override val backdropStillHint = "Solo la pietra ferma: qualche frame in meno da disegnare."

    override val groupData = "Dati e informazioni"
    override val groupDataDescription = "Dove vive quello che l'app salva."
    override val panelLayout = "Disposizione dei pannelli"
    override val panelLayoutHint =
        "Larghezze, collassi, zoom della mappa e posizione delle targhe."
    override val resetLayout = "Ripristina disposizione"
    override val dataFolder = "Cartella dati"

    override val resetLayoutTitle = "Ripristinare la disposizione?"
    override val resetLayoutBody = "Pannelli, larghezze, zoom della mappa e targhe tornano " +
        "ai valori iniziali. Le partite e il Compendio non vengono toccati."

    override val cpuSpeedSlow = "Lenta"
    override val cpuSpeedNormal = "Normale"
    override val cpuSpeedFast = "Veloce"
    override val cpuSpeedInstant = "Istantanea"
    override val cpuPaceSlow = "Una pausa lunga fra un comando e l'altro."
    override val cpuPaceNormal = "Il ritmo consigliato per seguire lo scontro."
    override val cpuPaceFast = "Si vede ogni comando, con pause molto brevi."
    override val cpuPaceInstant = "Nessuna pausa: il turno nemico si risolve tutto insieme."

    override val turnOrderHidden = "Nascosto"
    override val turnOrderOnly = "Solo ordine"
    override val turnOrderWithInitiative = "Con iniziativa"
    override val turnOrderHiddenHint = "La fascia sparisce e la mappa guadagna spazio."
    override val turnOrderOnlyHint = "Chi agisce e in che ordine, senza i numeri."
    override val turnOrderWithInitiativeHint = "L'ordine con accanto il tiro d'iniziativa."
}

internal object SettingsStringsEn : SettingsStrings {
    override val eyebrow = "APPLICATION PREFERENCES"
    override val title = "Settings"
    override val subtitle = "These apply to every game, including the ones already open, " +
        "and stay as you left them between sessions."

    override val sectionGeneral = "General"
    override val sectionCursors = "Cursors"

    override val groupLanguage = "Language"
    override val groupLanguageDescription = "What language the application speaks."
    override val languageChoice = "Interface language"
    override val languageHint = "Changes everywhere at once, no restart. Distances follow " +
        "the language: metres in Italian, feet in English."

    override val groupGame = "Game"
    override val groupGameDescription = "How you watch the opposing side play."
    override val cpuSpeed = "CPU pace"

    override val groupTable = "Table"
    override val groupTableDescription = "What the strip above the map shows."
    override val turnOrder = "Turn order"

    override val groupLook = "Look"
    override val groupLookDescription = "The frame around the game."
    override val backdrop = "Backdrop"
    override val backdropAnimated = "Animated"
    override val backdropStill = "Still"
    override val backdropAnimatedHint = "Embers and glow drift slowly behind the screens."
    override val backdropStillHint = "Bare stone: a few frames less to draw."

    override val groupData = "Data and information"
    override val groupDataDescription = "Where what the app saves actually lives."
    override val panelLayout = "Panel layout"
    override val panelLayoutHint = "Widths, collapsed panels, map zoom and plate positions."
    override val resetLayout = "Reset layout"
    override val dataFolder = "Data folder"

    override val resetLayoutTitle = "Reset the layout?"
    override val resetLayoutBody = "Panels, widths, map zoom and plates go back to their " +
        "initial values. Your games and the Compendium are left untouched."

    override val cpuSpeedSlow = "Slow"
    override val cpuSpeedNormal = "Normal"
    override val cpuSpeedFast = "Fast"
    override val cpuSpeedInstant = "Instant"
    override val cpuPaceSlow = "A long pause between one command and the next."
    override val cpuPaceNormal = "The recommended pace for following the fight."
    override val cpuPaceFast = "Every command is visible, with very short pauses."
    override val cpuPaceInstant = "No pauses: the enemy turn resolves all at once."

    override val turnOrderHidden = "Hidden"
    override val turnOrderOnly = "Order only"
    override val turnOrderWithInitiative = "With initiative"
    override val turnOrderHiddenHint = "The strip disappears and the map gains room."
    override val turnOrderOnlyHint = "Who acts and in what order, without the numbers."
    override val turnOrderWithInitiativeHint = "The order with each initiative roll beside it."
}
