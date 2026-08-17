package app.d6d.ui.i18n

/**
 * Le parole che ricorrono ovunque.
 *
 * Un fascicolo piccolo e molto usato. Sta a se' per una ragione sola: «Annulla»
 * compare in tredici punti diversi, e tradotto tredici volte prima o poi diventa
 * «Annulla» in dodici e «Indietro» nel tredicesimo. Qui e' una parola sola, e
 * cambiarla la cambia dappertutto.
 *
 * Non entra qui il testo che *sembra* generico ma appartiene a una schermata:
 * «Salva» sta qui, «Salva la scheda» sta nel fascicolo della scheda, perche' e'
 * quella schermata a sapere che cosa sta salvando.
 */
interface CommonStrings {
    val save: String
    val cancel: String
    val close: String
    val delete: String
    val remove: String
    val add: String
    val edit: String
    val open: String
    val back: String
    val next: String
    val done: String
    val apply: String
    val reset: String
    val clear: String
    val confirm: String
    val replace: String
    val yes: String
    val no: String
    val none: String
    val all: String
    val search: String
    val loading: String
    val nameLabel: String
    val level: String
    val unnamed: String

    /** Separatore fra voci in fila su una riga sola. Non cambia con la lingua. */
    val bullet: String get() = " · "
}

internal object CommonStringsIt : CommonStrings {
    override val save = "Salva"
    override val cancel = "Annulla"
    override val close = "Chiudi"
    override val delete = "Elimina"
    override val remove = "Rimuovi"
    override val add = "Aggiungi"
    override val edit = "Modifica"
    override val open = "Apri"
    override val back = "Indietro"
    override val next = "Avanti"
    override val done = "Fine"
    override val apply = "Applica"
    override val reset = "Ripristina"
    override val clear = "Azzera"
    override val confirm = "Conferma"
    override val replace = "Sostituisci"
    override val yes = "Sì"
    override val no = "No"
    override val none = "Nessuno"
    override val all = "Tutti"
    override val search = "Cerca"
    override val loading = "Caricamento…"
    override val nameLabel = "Nome"
    override val level = "Livello"
    override val unnamed = "Senza nome"
}

internal object CommonStringsEn : CommonStrings {
    override val save = "Save"
    override val cancel = "Cancel"
    override val close = "Close"
    override val delete = "Delete"
    override val remove = "Remove"
    override val add = "Add"
    override val edit = "Edit"
    override val open = "Open"
    override val back = "Back"
    override val next = "Next"
    override val done = "Done"
    override val apply = "Apply"
    override val reset = "Reset"
    override val clear = "Clear"
    override val confirm = "Confirm"
    override val replace = "Replace"
    override val yes = "Yes"
    override val no = "No"
    override val none = "None"
    override val all = "All"
    override val search = "Search"
    override val loading = "Loading…"
    override val nameLabel = "Name"
    override val level = "Level"
    override val unnamed = "Unnamed"
}
