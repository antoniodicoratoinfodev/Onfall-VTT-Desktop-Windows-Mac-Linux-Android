package app.d6d.ui.state

import app.d6d.sheet.metresLabel

/**
 * Traduzione dei rifiuti del motore.
 *
 * Il motore e' deliberatamente indipendente dall'interfaccia e non conosce la
 * lingua del tavolo: i suoi messaggi sono in inglese e restano tali nel registro
 * e nei test. La traduzione vive quindi qui, nello strato che gia' possiede il
 * vocabolario italiano delle condizioni e delle capacita'.
 *
 * Tre livelli, dal piu' specifico al piu' generico. Un messaggio sconosciuto
 * torna indietro immutato: aggiungere una regola al motore non puo' far sparire
 * l'avviso dal tavolo, al massimo lo lascia in inglese finche' non entra qui.
 */
internal fun italianRuleMessage(message: String?): String? {
    if (message.isNullOrBlank()) return message
    exactMessages[message]?.let { return it }
    patterns.firstNotNullOfOrNull { it.translate(message) }?.let { return it }
    messagePrefixes.firstOrNull { (english, _) -> message.startsWith(english) }
        ?.let { (english, italian) -> return italian + message.removePrefix(english) }
    return message
}

/** Stato dello scontro nei messaggi che lo nominano. */
private fun statusLabel(status: String): String = when (status.trim()) {
    "DRAFT" -> "in preparazione"
    "READY" -> "pronto"
    "ACTIVE" -> "in corso"
    "PAUSED" -> "in pausa"
    "RESOLVED" -> "concluso"
    else -> status.trim().lowercase()
}

/**
 * Messaggi che vanno ricomposti, non solo tradotti: l'italiano mette altrove i
 * valori che l'inglese incastra nella frase.
 */
private class MessagePattern(regex: String, val build: (List<String>) -> String) {
    private val pattern = Regex(regex)

    fun translate(message: String): String? =
        pattern.matchEntire(message)?.let { build(it.groupValues.drop(1)) }
}

private val patterns = listOf(
    MessagePattern("""It is not (.+)'s turn""") { (combatant) ->
        "Non è il turno di $combatant."
    },
    MessagePattern("""Command requires (\w+) but encounter is (\w+)""") { (required, actual) ->
        "Il comando richiede uno scontro ${statusLabel(required)}, ma questo è ${statusLabel(actual)}."
    },
    MessagePattern("""Target is (\d+) feet away, beyond the ability range of (\d+) feet""") { (away, range) ->
        "Il bersaglio è a ${metresLabel(away.toInt())}: oltre la gittata di " +
            "${metresLabel(range.toInt())} della capacità."
    },
    MessagePattern("""The area centre is (\d+) feet away, beyond the range of (\d+) feet""") { (away, range) ->
        "Il centro dell'area è a ${metresLabel(away.toInt())}: oltre la gittata di " +
            "${metresLabel(range.toInt())}."
    },
    MessagePattern("""A token covers between 1 and (\d+) squares per side""") { (maximum) ->
        "Un segnaposto occupa da 1 a $maximum caselle per lato."
    },
    MessagePattern("""Grid side exceeds the supported limit of (\d+)""") { (maximum) ->
        "Il lato della griglia supera il limite di $maximum caselle."
    },
    MessagePattern("""Current hit points must be between 0 and (\d+)""") { (maximum) ->
        "I punti ferita attuali devono essere fra 0 e $maximum."
    },
    MessagePattern("""Exhaustion must be between 0 and (\d+)""") { (maximum) ->
        "Il livello di Sfinimento deve essere fra 0 e $maximum."
    },
    MessagePattern("""(\w+) cannot be blank""") { (field) ->
        "Campo obbligatorio: $field."
    },
    MessagePattern("""(\w+) cannot be negative""") { (field) ->
        "Valore negativo non ammesso: $field."
    },
)

/**
 * Messaggi che terminano con un identificativo o un nome: si traduce la parte
 * fissa e si conserva intatta la coda, che appartiene ai dati del tavolo.
 *
 * Ordinati per lunghezza decrescente, cosi' un prefisso corto non puo' rubare la
 * corrispondenza a uno piu' lungo che lo contiene.
 */
private val messagePrefixes: List<Pair<String, String>> = listOf(
    "A passive trait cannot be activated: " to "Un tratto permanente non può essere attivato: ",
    "Ability requires a different resolution: " to "La capacità richiede un'altra risoluzione: ",
    "Ability does not use an attack roll: " to "La capacità non usa un tiro per colpire: ",
    "Ability requires manual resolution: " to "La capacità richiede la risoluzione manuale: ",
    "Duplicate combatant instance id: " to "Identificativo di combattente duplicato: ",
    "Ability has no automatic effect: " to "La capacità non ha un effetto automatico: ",
    "Duplicate condition instance id: " to "Identificativo di condizione duplicato: ",
    "Ability is not an area effect: " to "La capacità non è un effetto ad area: ",
    "Ability resource is missing: " to "Risorsa della capacità mancante: ",
    "Placement key must match its combatant id" to "Il segnaposto non corrisponde al suo combattente",
    "Invalid grid position: " to "Posizione sulla griglia non valida: ",
    "Ability does not heal: " to "La capacità non cura: ",
    "Unknown combatant: " to "Combattente sconosciuto: ",
    "Not enough uses of " to "Usi insufficienti di ",
    "Unknown ability: " to "Capacità sconosciuta: ",
).sortedByDescending { (english, _) -> english.length }

/** Rifiuti a testo fisso: la traduzione e' una frase intera. */
private val exactMessages: Map<String, String> = mapOf(
    // --- turno e budget -----------------------------------------------------------
    "Action already spent" to "Azione già spesa in questo turno.",
    "Bonus action already spent" to "Azione bonus già spesa in questo turno.",
    "Reaction already spent" to "Reazione già spesa in questo turno.",
    "No attacks remain in the Attack action" to
        "Non restano attacchi nell'azione di Attacco.",
    "An Attack action is already in progress" to "Un'azione di Attacco è già in corso.",
    "An additional action is already available" to "Hai già un'azione aggiuntiva disponibile.",
    "Action Surge was already used this turn" to
        "Azione Impetuosa è già stata usata in questo turno.",
    "Action Surge can be used only once in the same turn" to
        "Azione Impetuosa si può usare una sola volta per turno.",
    "NONE does not spend a turn resource" to
        "Questa capacità non consuma alcuna risorsa del turno.",
    "Legendary action pools are not part of this vertical slice" to
        "Le azioni leggendarie non sono ancora gestite.",
    "There is no current turn" to "Nessun turno in corso.",
    "Movement exceeds the remaining budget" to
        "Lo spostamento supera il movimento rimasto in questo turno.",

    // --- stato del combattente ----------------------------------------------------
    "A combatant at zero hit points cannot act" to "Un combattente a 0 PF non può agire.",
    "A combatant at zero hit points cannot attack" to "Un combattente a 0 PF non può attaccare.",
    "A combatant at zero hit points cannot cast" to "Un combattente a 0 PF non può lanciare incantesimi.",
    "A combatant at zero hit points cannot take a turn" to
        "Un combattente a 0 PF non può giocare il proprio turno.",
    "A dead combatant cannot act" to "Un combattente morto non può agire.",
    "A dead combatant cannot attack" to "Un combattente morto non può attaccare.",
    "A dead combatant cannot cast" to "Un combattente morto non può lanciare incantesimi.",
    "A dead combatant cannot be healed" to "Un combattente morto non può essere curato.",
    "A dead combatant cannot be targeted by an attack" to
        "Un combattente morto non può essere bersaglio di un attacco.",
    "A dead combatant cannot take a turn" to "Un combattente morto non può giocare il proprio turno.",
    "An incapacitated combatant cannot attack" to "Un combattente Incapacitato non può attaccare.",
    "An incapacitated combatant cannot cast" to
        "Un combattente Incapacitato non può lanciare incantesimi.",
    "An incapacitated combatant cannot begin concentration" to
        "Un combattente Incapacitato non può iniziare a concentrarsi.",
    "An incapacitated combatant cannot use a healing ability" to
        "Un combattente Incapacitato non può usare una capacità di cura.",
    "The combatant cannot cast spells while wearing armor without training" to
        "Il combattente non può lanciare incantesimi con un'armatura in cui non è addestrato.",
    "Combatant is not in the current initiative order" to
        "Il combattente non è nell'ordine d'iniziativa corrente.",

    // --- morte, stabilizzazione e sfinimento --------------------------------------
    "A dead creature cannot be stabilized" to "Una creatura morta non può essere stabilizzata.",
    "A dead creature does not roll death saves" to
        "Una creatura morta non tira più i tiri salvezza contro morte.",
    "A stable creature does not roll death saves" to
        "Una creatura stabile non tira i tiri salvezza contro morte.",
    "Only a creature at zero hit points can be stabilized" to
        "Solo una creatura a 0 PF può essere stabilizzata.",
    "Only a creature at zero hit points rolls death saves" to
        "Solo una creatura a 0 PF tira i tiri salvezza contro morte.",

    // --- danni e cure -------------------------------------------------------------
    "Damage needs at least one component" to "Il danno richiede almeno un componente.",
    "A damage component exceeds the supported range" to
        "Un componente di danno supera il valore massimo gestito.",
    "Total damage exceeds the supported range" to "Il danno totale supera il valore massimo gestito.",
    "Manual damage must contain one value per damage component" to
        "Il danno manuale richiede un valore per ogni componente.",
    "Healing must be positive" to "La cura deve essere di almeno 1 punto ferita.",
    "Maximum hit points must be positive" to "I punti ferita massimi devono essere almeno 1.",
    "Temporary hit points cannot be negative" to "I punti ferita temporanei non possono essere negativi.",
    "A healing ability can target only the healer's faction" to
        "Questa cura può bersagliare soltanto la propria squadra.",
    "This healing ability can target only its user" to
        "Questa cura può essere usata soltanto su di sé.",
    "This healing ability requires a different ally" to
        "Questa cura richiede un alleato diverso da chi la usa.",
    "This healing ability cannot use a different resource" to
        "Questa cura non può attingere a un'altra risorsa.",
    "A selected healing resource id cannot be blank" to
        "Scegli la risorsa da cui attingere per la cura.",
    "The healing ability has no valid base spell slot" to
        "La capacità di cura non ha uno slot incantesimo di base valido.",
    "The healing ability base slot does not match its scaling" to
        "Lo slot di base della cura non corrisponde alla sua progressione.",
    "An upcast healing spell must consume exactly one spell slot" to
        "Una cura potenziata deve consumare esattamente uno slot incantesimo.",

    // --- risorse e slot incantesimo -----------------------------------------------
    "A spell slot was already spent this turn" to
        "Hai già speso uno slot incantesimo in questo turno.",
    "The selected resource is not a spell slot" to
        "La risorsa scelta non è uno slot incantesimo.",
    "The selected spell slot is below the ability's base level" to
        "Lo slot scelto è di livello inferiore a quello base della capacità.",

    // --- concentrazione e condizioni ----------------------------------------------
    "The condition's concentration owner is not concentrating" to
        "Chi dovrebbe mantenere la condizione non si sta concentrando.",

    // --- trasformazione -----------------------------------------------------------
    "A passive trait cannot transform a combatant" to
        "Un tratto permanente non può trasformare un combattente.",
    "A transformation must preserve combatant and definition ids" to
        "Una trasformazione deve conservare l'identità del combattente e della sua scheda.",

    // --- correzioni alla scheda in combattimento ----------------------------------
    "A combatant edit cannot change its definition id" to
        "Una correzione non può cambiare la scheda di provenienza del combattente.",
    "A combatant edit cannot change its instance id" to
        "Una correzione non può cambiare l'identità del combattente nello scontro.",

    // --- ciclo di vita dello scontro ----------------------------------------------
    "An encounter needs at least one combatant" to "Uno scontro richiede almeno un combattente.",
    "A resolved encounter cannot receive new combatants" to
        "Uno scontro concluso non può accogliere altri combattenti.",
    "Only an active or paused encounter can be resolved" to
        "Solo uno scontro in corso o in pausa può essere concluso.",
    "Party combatants must be unique" to "Ogni membro della squadra deve comparire una sola volta.",

    // --- iniziativa e ordine dei turni ---------------------------------------------
    "Every combatant needs initiative before starting" to
        "Ogni combattente deve avere un'iniziativa prima di cominciare.",
    "Initiative can only be changed in DRAFT or READY" to
        "L'iniziativa si può cambiare solo prima che lo scontro cominci.",
    "Initiative can only be overridden during active play" to
        "L'iniziativa si può correggere solo a scontro avviato.",
    "Initiative order must contain every initialized combatant exactly once" to
        "L'ordine d'iniziativa deve contenere ogni combattente una sola volta.",
    "Turn order must contain every combatant exactly once" to
        "L'ordine dei turni deve contenere ogni combattente una sola volta.",
    "Turns can only be reordered during active play" to
        "I turni si possono riordinare solo a scontro avviato.",
    "The current turn can only be changed during active play" to
        "Il turno corrente si può spostare solo a scontro avviato.",
    "Shared initiative needs a non-empty set of unique combatants" to
        "Un'iniziativa condivisa richiede almeno un combattente, senza ripetizioni.",
    "A started encounter needs a complete initiative state" to
        "Uno scontro avviato richiede un'iniziativa completa.",
    "An encounter that has not started cannot have a current turn" to
        "Uno scontro non ancora avviato non può avere un turno corrente.",

    // --- mappa e segnaposti ---------------------------------------------------------
    "No map has been configured for this encounter" to
        "Nessuna mappa configurata per questo scontro.",
    "The combatant is not on the map" to "Il combattente non è sulla mappa.",
    "The destination is outside the grid" to "La destinazione è fuori dalla griglia.",
    "The destination is already occupied" to "La destinazione è già occupata.",
    "That space is already occupied" to "Quello spazio è già occupato.",
    "The token does not fit inside the grid" to "Il segnaposto non entra nella griglia.",
    "The area centre is outside the map" to "Il centro dell'area è fuori dalla mappa.",
    "Grid coordinates cannot be negative" to "Le coordinate della griglia non possono essere negative.",
    "Grid size cannot be negative" to "Le dimensioni della griglia non possono essere negative.",
    "A grid needs both dimensions, or neither" to
        "La griglia richiede entrambe le dimensioni, o nessuna.",
    "A square must cover a positive distance" to "Una casella deve coprire una distanza positiva.",
    "origin is required" to "Il segnaposto richiede una posizione d'origine.",
)
