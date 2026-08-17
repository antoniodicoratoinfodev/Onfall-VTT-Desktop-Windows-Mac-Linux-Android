package app.d6d.ui.i18n

/** La procedura di nuova partita, in italiano. */
internal object EncounterStringsIt : EncounterStrings {

    override fun step(current: Int, total: Int, description: String) =
        "$current di $total · $description"
    override val stepSource = "Parti da una partita inclusa, dai tuoi template o da zero."
    override val stepParticipants = "Scegli personaggi, mob, quantità e schieramenti."
    override val stepGrid = "Imposta dimensioni e scala metrica della griglia."
    override val stepMode = "Scegli l'esperienza con cui iniziare."
    override val stepDifficulty = "Scegli l'avversario: nessuna CPU, oppure quanto sarà spietata."

    override val whereToStart = "Da dove vuoi partire?"
    override val whereToStartBody = "I personaggi possono partecipare a più sessioni: ogni " +
        "partita riceve una copia indipendente di PF, condizioni, turni e posizione. " +
        "Creare da zero non elimina i template."
    override val includedGames = "Partite incluse"
    override val orElse = "Oppure"
    override val defaultFeminine = "Predefinita"
    override val defaultMasculine = "Predefinito"
    override val useExistingTemplates = "Usa template già creati"
    override val createFromScratch = "Crea personaggi e mob da zero"
    override val createFromScratchHint = "Apre il Compendio per creare nuove schede"
    override val openSavedSession = "Apri sessione salvata"
    override val noSavedSession = "Nessuna sessione salvata"
    override val oneSavedSession = "1 sessione salvata"
    override val sourceIncludedGame = "Partita inclusa"
    override val sourceExistingTemplates = "Template già creati"
    override val sourceFromScratch = "Crea da zero"
    override fun savedSessions(count: Int) = "$count sessioni salvate"
    override fun templateSummary(level: Int, party: Int, opponents: Int) =
        "Livello $level · $party personaggi · $opponents avversari"
    override fun templateOpponentLine(name: String, summary: String) = "«$name» — $summary"
    override fun peopleAndCreatures(people: Int, creatures: Int) =
        "$people personaggi · $creatures mob"

    override val gameName = "Nome partita"
    override val alliesLabel = "Alleati"
    override val opponentsLabel = "Avversari"
    override val nameTheEncounter = "Dai un nome all'incontro."
    override val openCompendium = "Apri Compendio"
    override val emptyCompendiumTitle = "Il Compendio è vuoto."
    override val emptyCompendiumBody = "Crea i protagonisti della partita."
    override val emptyCompendiumHint =
        "Crea e salva personaggi e mob; tornando qui vedrai soltanto i nuovi template."
    override val emptyCompendiumRequirement = "Crea e salva almeno una scheda o uno stat block."
    override val baseParty = "Squadra base"
    override val createMoreFromScratch = "Crea altri da zero"
    override fun participants(count: Int) = "$count partecipanti"
    override fun allies(count: Int) = "$count alleati"
    override fun opponents(count: Int) = "$count avversari"
    override fun charactersCount(count: Int) = "Personaggi ($count)"
    override fun creaturesCount(count: Int) = "Mob e creature ($count)"
    override fun quantity(value: Int) = "Quantità $value"

    override val gridSize = "Dimensione della griglia"
    override val squareScaleLabel = "Metri rappresentati da ogni quadratino"
    override val fewerColumns = "− colonne"
    override val moreColumns = "+ colonne"
    override val fewerRows = "− righe"
    override val moreRows = "+ righe"
    override val resetGrid = "Ripristina 20 × 15"
    override fun gridDimensions(columns: Int, rows: Int) = "$columns × $rows"
    override fun columnsCount(count: Int) = "$count colonne"
    override fun rowsCount(count: Int) = "$count righe"
    override fun scalePerSquare(distance: String) = "$distance / quadratino"
    override fun decreaseScale(step: String) = "− $step"
    override fun increaseScale(step: String) = "+ $step"
    override fun chosenScale(distance: String) = "Scala scelta: $distance"
    override fun totalArea(width: String, height: String, columns: Int, rows: Int) =
        "Area totale: $width × $height · $columns × $rows quadratini."

    override val howToStart = "Come vuoi iniziare?"
    override val modeFight = "Modalità Fight"
    override val modeFightHint = "Apre la mappa tattica e dispone automaticamente alleati e " +
        "nemici vicini, pronti allo scontro."
    override val modeFull = "Roleplay & Fight & Exploration"
    override val modeFullHint = "Apre la schermata normale con la griglia pronta, lasciando " +
        "libero il posizionamento dei token."
    override val fightPlacementNote = "I token verranno disposti al centro in due schieramenti " +
        "vicini. Potrai trascinarli in Modifica."
    override val fullPlacementNote = "La griglia sarà pronta ma vuota: potrai preparare " +
        "liberamente esplorazione e scene narrative."

    override val howDangerous = "Quanto devono essere pericolosi i nemici?"
    override val difficultyBody = "Medio è il livello normale. La difficoltà cambia le decisioni " +
        "della CPU, non le statistiche delle creature né le regole del combattimento. " +
        "Con Sandbox la CPU resta spenta e comandi tu anche gli avversari."
    override val sandbox = "Sandbox"
    override val sandboxHint = "Nessuna CPU: gli avversari li muovi e li fai agire tu, come gli " +
        "alleati. Utile per arbitrare a mano, provare una scena o preparare un incontro. " +
        "Le regole restano quelle del motore: cambia solo chi sceglie le mosse nemiche."
    override val easyHint = "Rispetto a Medio usa scelte semplici: attacca il bersaglio più " +
        "vicino e cura solo nelle emergenze, con lo slot minimo sufficiente e senza focus o " +
        "accerchiamenti. È molto meno efficiente di «Mi dispiace per te!»."
    override val mediumHint = "Il livello normale: coordina attacchi e cure e cerca buone " +
        "posizioni. Rispetto a Facile gioca di squadra e potenzia una cura quanto basta per " +
        "uscire dal pericolo; rispetto a «Mi dispiace per te!» insiste meno sul bersaglio " +
        "prioritario e accetta scelte più prudenti."
    override val sorryForYouHint = "Rispetto al normale concentra il fuoco sui bersagli " +
        "vulnerabili, accerchia, evita il fuoco amico e investe slot superiori per rimettere " +
        "subito in sicurezza la squadra. È la CPU più aggressiva e coordinata."
    override fun singleFactionNote(reason: String) =
        "$reason Puoi comunque avviare questa sessione mono-fazione."

    override val nextGrid = "Avanti · Griglia"
    override val nextMode = "Avanti · Modalità"
    override val nextDifficulty = "Avanti · Difficoltà"
    override val nextDifficultyHint = "Poi scegli se e quanto sarà tattica la CPU nemica"
    override val startGame = "Avvia partita"
    override fun startSummary(mode: String, opponent: String) = "$mode · $opponent"

    override val cpuIdleNoAllies =
        "CPU inattiva: aggiungi almeno un alleato che gli avversari possano affrontare."
    override val cpuIdleNoOpponents =
        "CPU inattiva: non hai selezionato alcun avversario da controllare."
    override val nameTheGame = "Dai un nome alla partita."
    override val pickAtLeastOneParticipant = "Seleziona almeno un partecipante."
    override val invalidConfiguration = "Configurazione dell'incontro non valida."
    override val cannotStart = "Impossibile avviare l'incontro."
    override fun sheetNoLongerAvailable(name: String) = "La scheda «$name» non è più disponibile."
}
