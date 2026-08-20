package app.d6d.ui.i18n

import app.d6d.i18n.AppLanguage

/**
 * Tutto il testo dell'applicazione, diviso per stanza.
 *
 * E' un'interfaccia e non una mappa da chiave a stringa, ed e' una scelta
 * precisa: con una mappa una voce dimenticata si scopre a schermo, in
 * produzione, come una chiave nuda in mezzo alla pagina; con un'interfaccia non
 * si compila. Aggiungere una frase vuol dire scriverla in *entrambe* le lingue,
 * subito, perche' il compilatore non permette il contrario.
 *
 * La divisione in fascicoli segue le schermate e non la grammatica: chi lavora
 * sulla mappa tattica apre [battle] e trova li' tutto quello che quella
 * schermata dice. Le parole che ricorrono ovunque — Salva, Annulla, Chiudi —
 * stanno in [common], una volta sola, perche' tradurle in modo diverso in due
 * punti e' un difetto che si nota.
 *
 * Ogni fascicolo dichiara l'interfaccia e le due realizzazioni nello stesso file,
 * una sotto l'altra: una revisione della traduzione si legge di seguito invece di
 * rimbalzare fra due alberi paralleli.
 */
interface Strings {

    /** La lingua di questo vocabolario, da passare alle funzioni del motore. */
    val language: AppLanguage

    val common: CommonStrings
    val nav: NavStrings
    val settings: SettingsStrings
    val battle: BattleStrings
    val maps: MapStrings
    val glossary: GlossaryStrings
    val log: LogStrings
    val sheet: SheetStrings
    val items: ItemStrings
    val encounter: EncounterStrings
    val session: SessionStrings
    val compendium: CompendiumStrings
    val cursors: CursorStrings
    val abilities: AbilityStrings
    val board: BoardStrings
}

/** Il vocabolario italiano. */
object ItalianStrings : Strings {
    override val language = AppLanguage.ITALIAN
    override val common: CommonStrings = CommonStringsIt
    override val nav: NavStrings = NavStringsIt
    override val settings: SettingsStrings = SettingsStringsIt
    override val battle: BattleStrings = BattleStringsIt
    override val maps: MapStrings = MapStringsIt
    override val glossary: GlossaryStrings = GlossaryStringsIt
    override val log: LogStrings = LogStringsIt
    override val sheet: SheetStrings = SheetStringsIt
    override val items: ItemStrings = ItemStringsIt
    override val encounter: EncounterStrings = EncounterStringsIt
    override val session: SessionStrings = SessionStringsIt
    override val compendium: CompendiumStrings = CompendiumStringsIt
    override val cursors: CursorStrings = CursorStringsIt
    override val abilities: AbilityStrings = AbilityStringsIt
    override val board: BoardStrings = BoardStringsIt
}

/** Il vocabolario inglese. */
object EnglishStrings : Strings {
    override val language = AppLanguage.ENGLISH
    override val common: CommonStrings = CommonStringsEn
    override val nav: NavStrings = NavStringsEn
    override val settings: SettingsStrings = SettingsStringsEn
    override val battle: BattleStrings = BattleStringsEn
    override val maps: MapStrings = MapStringsEn
    override val glossary: GlossaryStrings = GlossaryStringsEn
    override val log: LogStrings = LogStringsEn
    override val sheet: SheetStrings = SheetStringsEn
    override val items: ItemStrings = ItemStringsEn
    override val encounter: EncounterStrings = EncounterStringsEn
    override val session: SessionStrings = SessionStringsEn
    override val compendium: CompendiumStrings = CompendiumStringsEn
    override val cursors: CursorStrings = CursorStringsEn
    override val abilities: AbilityStrings = AbilityStringsEn
    override val board: BoardStrings = BoardStringsEn
}

/** Il vocabolario di una lingua. L'unico posto che lega l'enum alle parole. */
fun stringsFor(language: AppLanguage): Strings = when (language) {
    AppLanguage.ITALIAN -> ItalianStrings
    AppLanguage.ENGLISH -> EnglishStrings
}
