package app.d6d.ui.state

import app.d6d.domain.combat.CombatStatus
import app.d6d.i18n.AppLanguage
import app.d6d.i18n.inlineLabel
import app.d6d.i18n.pick
import app.d6d.sheet.i18n.distanceLabel
import app.d6d.ui.i18n.LocalizedText
import app.d6d.ui.i18n.Strings

/**
 * Traduzione dei rifiuti del motore.
 *
 * Il motore e' deliberatamente indipendente dall'interfaccia e non conosce la
 * lingua del tavolo: i suoi messaggi sono in inglese tecnico e restano tali nel
 * registro e nei test. La traduzione vive quindi qui, nello strato che gia'
 * possiede il vocabolario delle condizioni e delle capacita'.
 *
 * Anche la versione inglese passa di qui e non e' un rimando all'originale: i
 * messaggi del motore sono frasi da log — senza punto, tagliate corte, scritte
 * per un `assert` — mentre al tavolo serve una frase. «Action already spent»
 * diventa «Action already spent this turn.», che dice la stessa cosa a chi legge
 * e non a chi esegue.
 *
 * Le due lingue stanno **sulla stessa riga**, in [RuleMessage]: e' il solo modo
 * perche' una revisione della traduzione si possa leggere per confronto, e
 * perche' aggiungere una regola al motore non ne lasci indietro una delle due.
 *
 * Tre livelli, dal piu' specifico al piu' generico. Un messaggio sconosciuto
 * torna indietro immutato: aggiungere una regola al motore non puo' far sparire
 * l'avviso dal tavolo, al massimo lo lascia in inglese tecnico finche' non entra
 * in queste tabelle.
 */

/** Le due forme di uno stesso rifiuto. */
private class RuleMessage(val italian: String, val english: String) {
    fun of(language: AppLanguage): String = language.pick(italian, english)
}

private infix fun String.to(pair: Pair<String, String>): Pair<String, RuleMessage> =
    this to RuleMessage(pair.first, pair.second)

/**
 * Il messaggio del motore, pronto da mostrare e legato alla lingua in uso.
 *
 * Restituisce un [LocalizedText] e non una stringa perche' un avviso resta sullo
 * schermo: se nel frattempo la lingua cambia, deve cambiare anche lui.
 */
internal fun ruleMessage(message: String?): LocalizedText? {
    if (message.isNullOrBlank()) return null
    return LocalizedText { strings -> translateRuleMessage(message, strings.language) }
}

/** La stessa traduzione, quando la lingua e' gia' nota e il risultato serve subito. */
internal fun translateRuleMessage(message: String, language: AppLanguage): String {
    exactMessages[message]?.let { return it.of(language) }
    patterns.firstNotNullOfOrNull { it.translate(message, language) }?.let { return it }
    messagePrefixes.firstOrNull { (english, _) -> message.startsWith(english) }
        ?.let { (english, translation) ->
            return translation.of(language) + message.removePrefix(english)
        }
    return message
}

/** Compatibilita' con chi chiede la traduzione avendo gia' un vocabolario in mano. */
internal fun ruleMessageIn(message: String?, strings: Strings): String? =
    message?.takeIf { it.isNotBlank() }?.let { translateRuleMessage(it, strings.language) }

/** Stato dello scontro nei messaggi che lo nominano. */
private fun statusLabel(status: String, language: AppLanguage): String =
    runCatching { CombatStatus.valueOf(status.trim()).inlineLabel(language) }
        .getOrElse { status.trim().lowercase() }

/**
 * Messaggi che vanno ricomposti, non solo tradotti: ogni lingua mette altrove i
 * valori che l'inglese del motore incastra nella frase.
 */
private class MessagePattern(
    regex: String,
    val build: (List<String>, AppLanguage) -> String,
) {
    private val pattern = Regex(regex)

    fun translate(message: String, language: AppLanguage): String? =
        pattern.matchEntire(message)?.let { build(it.groupValues.drop(1), language) }
}

private val patterns = listOf(
    MessagePattern("""It is not (.+)'s turn""") { (combatant), language ->
        language.pick("Non è il turno di $combatant.", "It is not $combatant's turn.")
    },
    MessagePattern("""Command requires (\w+) but encounter is (\w+)""") { (required, actual), language ->
        language.pick(
            "Il comando richiede uno scontro ${statusLabel(required, language)}, " +
                "ma questo è ${statusLabel(actual, language)}.",
            "The command needs an encounter that is ${statusLabel(required, language)}, " +
                "but this one is ${statusLabel(actual, language)}.",
        )
    },
    MessagePattern("""Target is (\d+) feet away, beyond the ability range of (\d+) feet""") { (away, range), language ->
        val distance = distanceLabel(away.toInt(), language)
        val reach = distanceLabel(range.toInt(), language)
        language.pick(
            "Il bersaglio è a $distance: oltre la gittata di $reach della capacità.",
            "The target is $distance away, beyond the ability's range of $reach.",
        )
    },
    MessagePattern("""The area centre is (\d+) feet away, beyond the range of (\d+) feet""") { (away, range), language ->
        val distance = distanceLabel(away.toInt(), language)
        val reach = distanceLabel(range.toInt(), language)
        language.pick(
            "Il centro dell'area è a $distance: oltre la gittata di $reach.",
            "The centre of the area is $distance away, beyond the range of $reach.",
        )
    },
    MessagePattern("""A token covers between 1 and (\d+) squares per side""") { (maximum), language ->
        language.pick(
            "Un segnaposto occupa da 1 a $maximum caselle per lato.",
            "A token covers between 1 and $maximum squares per side.",
        )
    },
    MessagePattern("""Grid side exceeds the supported limit of (\d+)""") { (maximum), language ->
        language.pick(
            "Il lato della griglia supera il limite di $maximum caselle.",
            "The grid side goes past the limit of $maximum squares.",
        )
    },
    MessagePattern("""Current hit points must be between 0 and (\d+)""") { (maximum), language ->
        language.pick(
            "I punti ferita attuali devono essere fra 0 e $maximum.",
            "Current hit points must be between 0 and $maximum.",
        )
    },
    MessagePattern("""Exhaustion must be between 0 and (\d+)""") { (maximum), language ->
        language.pick(
            "Il livello di Sfinimento deve essere fra 0 e $maximum.",
            "Exhaustion must be between 0 and $maximum.",
        )
    },
    MessagePattern("""(\w+) cannot be blank""") { (field), language ->
        language.pick("Campo obbligatorio: $field.", "This field is required: $field.")
    },
    MessagePattern("""(\w+) cannot be negative""") { (field), language ->
        language.pick("Valore negativo non ammesso: $field.", "This value cannot be negative: $field.")
    },
)

/**
 * Messaggi che terminano con un identificativo o un nome: si traduce la parte
 * fissa e si conserva intatta la coda, che appartiene ai dati del tavolo.
 *
 * Ordinati per lunghezza decrescente, cosi' un prefisso corto non puo' rubare la
 * corrispondenza a uno piu' lungo che lo contiene.
 */
private val messagePrefixes: List<Pair<String, RuleMessage>> = listOf(
    "A passive trait cannot be activated: " to (
        "Un tratto permanente non può essere attivato: " to
            "A passive trait cannot be activated: "
        ),
    "Ability requires a different resolution: " to (
        "La capacità richiede un'altra risoluzione: " to
            "This ability needs a different resolution: "
        ),
    "Ability does not use an attack roll: " to (
        "La capacità non usa un tiro per colpire: " to
            "This ability does not use an attack roll: "
        ),
    "Ability requires manual resolution: " to (
        "La capacità richiede la risoluzione manuale: " to
            "This ability has to be resolved by hand: "
        ),
    "Duplicate combatant instance id: " to (
        "Identificativo di combattente duplicato: " to
            "Duplicate combatant instance id: "
        ),
    "Ability has no automatic effect: " to (
        "La capacità non ha un effetto automatico: " to
            "This ability has no automatic effect: "
        ),
    "Duplicate condition instance id: " to (
        "Identificativo di condizione duplicato: " to
            "Duplicate condition instance id: "
        ),
    "Ability is not an area effect: " to (
        "La capacità non è un effetto ad area: " to "This ability is not an area effect: "
        ),
    "Ability resource is missing: " to (
        "Risorsa della capacità mancante: " to "The ability's resource is missing: "
        ),
    "Placement key must match its combatant id" to (
        "Il segnaposto non corrisponde al suo combattente" to
            "The token does not match its combatant"
        ),
    "Invalid grid position: " to (
        "Posizione sulla griglia non valida: " to "Invalid grid position: "
        ),
    "Ability does not heal: " to ("La capacità non cura: " to "This ability does not heal: "),
    "Unknown combatant: " to ("Combattente sconosciuto: " to "Unknown combatant: "),
    "Not enough uses of " to ("Usi insufficienti di " to "Not enough uses left of "),
    "Unknown ability: " to ("Capacità sconosciuta: " to "Unknown ability: "),
).sortedByDescending { (english, _) -> english.length }

/** Rifiuti a testo fisso: la traduzione e' una frase intera. */
private val exactMessages: Map<String, RuleMessage> = mapOf(
    // --- turno e budget -----------------------------------------------------------
    "Action already spent" to (
        "Azione già spesa in questo turno." to "Action already spent this turn."
        ),
    "Bonus action already spent" to (
        "Azione bonus già spesa in questo turno." to "Bonus action already spent this turn."
        ),
    "Reaction already spent" to (
        "Reazione già spesa in questo turno." to "Reaction already spent this turn."
        ),
    "No attacks remain in the Attack action" to (
        "Non restano attacchi nell'azione di Attacco." to
            "No attacks are left in the Attack action."
        ),
    "An Attack action is already in progress" to (
        "Un'azione di Attacco è già in corso." to "An Attack action is already under way."
        ),
    "An additional action is already available" to (
        "Hai già un'azione aggiuntiva disponibile." to "You already have an extra action."
        ),
    "Action Surge was already used this turn" to (
        "Azione Impetuosa è già stata usata in questo turno." to
            "Action Surge was already used this turn."
        ),
    "Action Surge can be used only once in the same turn" to (
        "Azione Impetuosa si può usare una sola volta per turno." to
            "Action Surge can only be used once per turn."
        ),
    "NONE does not spend a turn resource" to (
        "Questa capacità non consuma alcuna risorsa del turno." to
            "This ability spends nothing on your turn."
        ),
    "Legendary action pools are not part of this vertical slice" to (
        "Le azioni leggendarie non sono ancora gestite." to
            "Legendary actions are not handled yet."
        ),
    "There is no current turn" to ("Nessun turno in corso." to "No turn is under way."),
    "Movement exceeds the remaining budget" to (
        "Lo spostamento supera il movimento rimasto in questo turno." to
            "That move goes past the movement left this turn."
        ),

    // --- stato del combattente ----------------------------------------------------
    "A combatant at zero hit points cannot act" to (
        "Un combattente a 0 PF non può agire." to "A combatant at 0 HP cannot act."
        ),
    "A combatant at zero hit points cannot attack" to (
        "Un combattente a 0 PF non può attaccare." to "A combatant at 0 HP cannot attack."
        ),
    "A combatant at zero hit points cannot cast" to (
        "Un combattente a 0 PF non può lanciare incantesimi." to
            "A combatant at 0 HP cannot cast spells."
        ),
    "A combatant at zero hit points cannot take a turn" to (
        "Un combattente a 0 PF non può giocare il proprio turno." to
            "A combatant at 0 HP cannot take its turn."
        ),
    "A dead combatant cannot act" to (
        "Un combattente morto non può agire." to "A dead combatant cannot act."
        ),
    "A dead combatant cannot attack" to (
        "Un combattente morto non può attaccare." to "A dead combatant cannot attack."
        ),
    "A dead combatant cannot cast" to (
        "Un combattente morto non può lanciare incantesimi." to
            "A dead combatant cannot cast spells."
        ),
    "A dead combatant cannot be healed" to (
        "Un combattente morto non può essere curato." to "A dead combatant cannot be healed."
        ),
    "A dead combatant cannot be targeted by an attack" to (
        "Un combattente morto non può essere bersaglio di un attacco." to
            "A dead combatant cannot be attacked."
        ),
    "A dead combatant cannot take a turn" to (
        "Un combattente morto non può giocare il proprio turno." to
            "A dead combatant cannot take its turn."
        ),
    "An incapacitated combatant cannot attack" to (
        "Un combattente Incapacitato non può attaccare." to
            "An incapacitated combatant cannot attack."
        ),
    "An incapacitated combatant cannot cast" to (
        "Un combattente Incapacitato non può lanciare incantesimi." to
            "An incapacitated combatant cannot cast spells."
        ),
    "An incapacitated combatant cannot begin concentration" to (
        "Un combattente Incapacitato non può iniziare a concentrarsi." to
            "An incapacitated combatant cannot start concentrating."
        ),
    "An incapacitated combatant cannot use a healing ability" to (
        "Un combattente Incapacitato non può usare una capacità di cura." to
            "An incapacitated combatant cannot use a healing ability."
        ),
    "The combatant cannot cast spells while wearing armor without training" to (
        "Il combattente non può lanciare incantesimi con un'armatura in cui non è addestrato." to
            "This combatant cannot cast spells in armor it is not trained in."
        ),
    "Combatant is not in the current initiative order" to (
        "Il combattente non è nell'ordine d'iniziativa corrente." to
            "This combatant is not in the current initiative order."
        ),

    // --- morte, stabilizzazione e sfinimento --------------------------------------
    "A dead creature cannot be stabilized" to (
        "Una creatura morta non può essere stabilizzata." to
            "A dead creature cannot be stabilized."
        ),
    "A dead creature does not roll death saves" to (
        "Una creatura morta non tira più i tiri salvezza contro morte." to
            "A dead creature no longer rolls death saving throws."
        ),
    "A stable creature does not roll death saves" to (
        "Una creatura stabile non tira i tiri salvezza contro morte." to
            "A stable creature does not roll death saving throws."
        ),
    "Only a creature at zero hit points can be stabilized" to (
        "Solo una creatura a 0 PF può essere stabilizzata." to
            "Only a creature at 0 HP can be stabilized."
        ),
    "Only a creature at zero hit points rolls death saves" to (
        "Solo una creatura a 0 PF tira i tiri salvezza contro morte." to
            "Only a creature at 0 HP rolls death saving throws."
        ),

    // --- danni e cure -------------------------------------------------------------
    "Damage needs at least one component" to (
        "Il danno richiede almeno un componente." to "Damage needs at least one component."
        ),
    "A damage component exceeds the supported range" to (
        "Un componente di danno supera il valore massimo gestito." to
            "A damage component goes past the largest supported value."
        ),
    "Total damage exceeds the supported range" to (
        "Il danno totale supera il valore massimo gestito." to
            "Total damage goes past the largest supported value."
        ),
    "Manual damage must contain one value per damage component" to (
        "Il danno manuale richiede un valore per ogni componente." to
            "Manual damage needs one value per damage component."
        ),
    "Healing must be positive" to (
        "La cura deve essere di almeno 1 punto ferita." to
            "Healing has to be at least 1 hit point."
        ),
    "Maximum hit points must be positive" to (
        "I punti ferita massimi devono essere almeno 1." to
            "Maximum hit points have to be at least 1."
        ),
    "Temporary hit points cannot be negative" to (
        "I punti ferita temporanei non possono essere negativi." to
            "Temporary hit points cannot be negative."
        ),
    "A healing ability can target only the healer's faction" to (
        "Questa cura può bersagliare soltanto la propria squadra." to
            "This healing can only target the healer's own side."
        ),
    "This healing ability can target only its user" to (
        "Questa cura può essere usata soltanto su di sé." to
            "This healing can only be used on yourself."
        ),
    "This healing ability requires a different ally" to (
        "Questa cura richiede un alleato diverso da chi la usa." to
            "This healing needs an ally other than the healer."
        ),
    "This healing ability cannot use a different resource" to (
        "Questa cura non può attingere a un'altra risorsa." to
            "This healing cannot draw on a different resource."
        ),
    "A selected healing resource id cannot be blank" to (
        "Scegli la risorsa da cui attingere per la cura." to
            "Choose the resource this healing draws on."
        ),
    "The healing ability has no valid base spell slot" to (
        "La capacità di cura non ha uno slot incantesimo di base valido." to
            "This healing ability has no valid base spell slot."
        ),
    "The healing ability base slot does not match its scaling" to (
        "Lo slot di base della cura non corrisponde alla sua progressione." to
            "The healing ability's base slot does not match its scaling."
        ),
    "An upcast healing spell must consume exactly one spell slot" to (
        "Una cura potenziata deve consumare esattamente uno slot incantesimo." to
            "An upcast healing spell has to spend exactly one spell slot."
        ),

    // --- risorse e slot incantesimo -----------------------------------------------
    "A spell slot was already spent this turn" to (
        "Hai già speso uno slot incantesimo in questo turno." to
            "A spell slot was already spent this turn."
        ),
    "The selected resource is not a spell slot" to (
        "La risorsa scelta non è uno slot incantesimo." to
            "The chosen resource is not a spell slot."
        ),
    "The selected spell slot is below the ability's base level" to (
        "Lo slot scelto è di livello inferiore a quello base della capacità." to
            "The chosen slot is below the ability's base level."
        ),

    // --- concentrazione e condizioni ----------------------------------------------
    "The condition's concentration owner is not concentrating" to (
        "Chi dovrebbe mantenere la condizione non si sta concentrando." to
            "Whoever should hold this condition is not concentrating."
        ),

    // --- trasformazione -----------------------------------------------------------
    "A passive trait cannot transform a combatant" to (
        "Un tratto permanente non può trasformare un combattente." to
            "A passive trait cannot transform a combatant."
        ),
    "A transformation must preserve combatant and definition ids" to (
        "Una trasformazione deve conservare l'identità del combattente e della sua scheda." to
            "A transformation has to keep the combatant and sheet identity."
        ),

    // --- correzioni alla scheda in combattimento ----------------------------------
    "A combatant edit cannot change its definition id" to (
        "Una correzione non può cambiare la scheda di provenienza del combattente." to
            "A correction cannot change which sheet the combatant comes from."
        ),
    "A combatant edit cannot change its instance id" to (
        "Una correzione non può cambiare l'identità del combattente nello scontro." to
            "A correction cannot change the combatant's identity in the encounter."
        ),

    // --- ciclo di vita dello scontro ----------------------------------------------
    "An encounter needs at least one combatant" to (
        "Uno scontro richiede almeno un combattente." to
            "An encounter needs at least one combatant."
        ),
    "A resolved encounter cannot receive new combatants" to (
        "Uno scontro concluso non può accogliere altri combattenti." to
            "A resolved encounter cannot take on new combatants."
        ),
    "Only an active or paused encounter can be resolved" to (
        "Solo uno scontro in corso o in pausa può essere concluso." to
            "Only an active or paused encounter can be resolved."
        ),
    "Party combatants must be unique" to (
        "Ogni membro della squadra deve comparire una sola volta." to
            "Each party member can appear only once."
        ),

    // --- iniziativa e ordine dei turni ---------------------------------------------
    "Every combatant needs initiative before starting" to (
        "Ogni combattente deve avere un'iniziativa prima di cominciare." to
            "Every combatant needs an initiative before the fight starts."
        ),
    "Initiative can only be changed in DRAFT or READY" to (
        "L'iniziativa si può cambiare solo prima che lo scontro cominci." to
            "Initiative can only be changed before the encounter starts."
        ),
    "Initiative can only be overridden during active play" to (
        "L'iniziativa si può correggere solo a scontro avviato." to
            "Initiative can only be overridden once the encounter is under way."
        ),
    "Initiative order must contain every initialized combatant exactly once" to (
        "L'ordine d'iniziativa deve contenere ogni combattente una sola volta." to
            "The initiative order has to list every combatant exactly once."
        ),
    "Turn order must contain every combatant exactly once" to (
        "L'ordine dei turni deve contenere ogni combattente una sola volta." to
            "The turn order has to list every combatant exactly once."
        ),
    "Turns can only be reordered during active play" to (
        "I turni si possono riordinare solo a scontro avviato." to
            "Turns can only be reordered once the encounter is under way."
        ),
    "The current turn can only be changed during active play" to (
        "Il turno corrente si può spostare solo a scontro avviato." to
            "The current turn can only be moved once the encounter is under way."
        ),
    "Shared initiative needs a non-empty set of unique combatants" to (
        "Un'iniziativa condivisa richiede almeno un combattente, senza ripetizioni." to
            "A shared initiative needs at least one combatant, with no repeats."
        ),
    "A started encounter needs a complete initiative state" to (
        "Uno scontro avviato richiede un'iniziativa completa." to
            "An encounter under way needs a complete initiative state."
        ),
    "An encounter that has not started cannot have a current turn" to (
        "Uno scontro non ancora avviato non può avere un turno corrente." to
            "An encounter that has not started cannot have a current turn."
        ),

    // --- mappa e segnaposti ---------------------------------------------------------
    "No map has been configured for this encounter" to (
        "Nessuna mappa configurata per questo scontro." to
            "No map has been set up for this encounter."
        ),
    "The combatant is not on the map" to (
        "Il combattente non è sulla mappa." to "This combatant is not on the map."
        ),
    "The destination is outside the grid" to (
        "La destinazione è fuori dalla griglia." to "The destination is outside the grid."
        ),
    "The destination is already occupied" to (
        "La destinazione è già occupata." to "The destination is already taken."
        ),
    "That space is already occupied" to (
        "Quello spazio è già occupato." to "That space is already taken."
        ),
    "That space is blocked by a wall" to (
        "Quello spazio è occupato da un muro." to "That space is blocked by a wall."
        ),
    "The destination is blocked by a wall" to (
        "La destinazione è occupata da un muro." to "The destination is blocked by a wall."
        ),
    "A wall blocks every path to the destination" to (
        "Nessun percorso verso la destinazione evita i muri." to
            "Every path to the destination is blocked by a wall."
        ),
    "A wall blocks line of effect to the target" to (
        "Un muro blocca la linea d’effetto verso il bersaglio." to
            "A wall blocks line of effect to the target."
        ),
    "A wall blocks line of effect to the area centre" to (
        "Un muro blocca la linea d’effetto verso il centro dell’area." to
            "A wall blocks line of effect to the area centre."
        ),
    "The area centre is blocked by a wall" to (
        "Il centro dell’area è occupato da un muro." to
            "The centre of the area is blocked by a wall."
        ),
    "The token does not fit inside the grid" to (
        "Il segnaposto non entra nella griglia." to "The token does not fit inside the grid."
        ),
    "The area centre is outside the map" to (
        "Il centro dell'area è fuori dalla mappa." to "The centre of the area is off the map."
        ),
    "Grid coordinates cannot be negative" to (
        "Le coordinate della griglia non possono essere negative." to
            "Grid coordinates cannot be negative."
        ),
    "Grid size cannot be negative" to (
        "Le dimensioni della griglia non possono essere negative." to
            "Grid size cannot be negative."
        ),
    "A grid needs both dimensions, or neither" to (
        "La griglia richiede entrambe le dimensioni, o nessuna." to
            "A grid needs both dimensions, or neither."
        ),
    "A square must cover a positive distance" to (
        "Una casella deve coprire una distanza positiva." to
            "A square has to cover a positive distance."
        ),
    "origin is required" to (
        "Il segnaposto richiede una posizione d'origine." to
            "The token needs an origin position."
        ),
)
