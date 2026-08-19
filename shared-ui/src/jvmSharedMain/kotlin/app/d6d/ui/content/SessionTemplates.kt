package app.d6d.ui.content

import app.d6d.domain.combat.CombatantSetup
import app.d6d.domain.combat.D20Mode
import app.d6d.domain.space.GridPosition
import app.d6d.domain.space.MapGrid
import app.d6d.engine.CombatSession
import app.d6d.i18n.AppLanguage
import app.d6d.i18n.pick
import app.d6d.sheet.CharacterSheet
import app.d6d.sheet.MonsterStatBlock
import app.d6d.ui.roster.squaresPerSide
import app.d6d.ui.i18n.AppLocale

/** Un gruppo di creature uguali schierate nello stesso incontro. */
internal data class TemplateOpponent(
    val statBlock: MonsterStatBlock,
    val quantity: Int = 1,
)

/**
 * Una partita gia' pronta, inclusa con l'app.
 *
 * Un template non e' una sessione salvata: e' il suo stampo. Sceglierlo nella
 * procedura Nuova partita ne compila i partecipanti, il nome e la griglia, poi
 * la partita che ne nasce e' una qualsiasi — si modifica e si salva come le
 * altre, e il template resta intatto per la volta dopo.
 */
internal data class SessionTemplate(
    val id: String,
    private val language: AppLanguage,
    val name: String,
    val partyLevel: Int,
    /** Una riga sola, quella che si legge nel selettore. */
    val summary: String,
    val description: String,
    private val partyPlans: List<TemplateCharacterPlan>,
    val opponents: List<TemplateOpponent>,
    val gridColumns: Int,
    val gridRows: Int,
    val feetPerSquare: Int = 5,
) {

    /**
     * Le schede della squadra, costruite una volta sola.
     *
     * Costruirle significa far salire di livello quattro personaggi un passo alla
     * volta: al 20º sono ottanta avanzamenti, che non vanno rifatti a ogni lettura.
     */
    val party: List<CharacterSheet> by lazy {
        partyPlans.map { plan -> TemplateCharacters.build(plan, language) }
    }

    /** Dimensione della squadra senza materializzare le costose schede di livello alto. */
    val partyCount: Int get() = partyPlans.size

    /**
     * Identificativo e nome di ogni personaggio della squadra, senza costruirne
     * la scheda. Serve a chi deve solo *riconoscere* un nome — per esempio per
     * ridirlo in un'altra lingua — e non ha motivo di pagare ottanta
     * avanzamenti per leggere una stringa.
     */
    val partyNames: Map<String, String> get() = partyPlans.associate { it.id to it.name }

    /**
     * Costruisce soltanto le schede che non sono gia' installate nel Compendio.
     * In particolare evita di far avanzare di nuovo i personaggi di livello 20 a
     * ogni avvio, quando i loro documenti sono gia' presenti su disco.
     */
    internal fun buildMissingParty(knownIds: Set<String>): List<CharacterSheet> =
        partyPlans.asSequence()
            .filterNot { it.id in knownIds }
            .map { plan -> TemplateCharacters.build(plan, language) }
            .toList()

    val monsters: List<MonsterStatBlock> get() = opponents.map { it.statBlock }

    /** Numero di token avversari, contando le copie. */
    val opponentCount: Int get() = opponents.sumOf { it.quantity }

    /**
     * Incontro gia' avviato, nell'ordine imposto dal motore.
     *
     * L'iniziativa usa il punteggio statico invece di un tiro: un contenuto
     * incluso deve aprirsi identico su ogni installazione, altrimenti due tavoli
     * che parlano della stessa partita non si capiscono.
     */
    fun startedSession(seed: Long = DEFAULT_SEED): CombatSession {
        val setups = mutableListOf<CombatantSetup>()
        val footprints = mutableMapOf<String, Int>()
        party.forEach { sheet ->
            // Senza il catalogo SRD resterebbero le sole armi: incantesimi e azioni
            // di classe della scheda non diventerebbero comandi giocabili.
            setups += CombatantSetup(
                sheet.id,
                sheet.toActorDefinition(abilityCatalog = srdCatalogFor(language)),
            )
            footprints[sheet.id] = sheet.size.squaresPerSide
        }
        val opponentIds = mutableListOf<String>()
        opponents.forEach { opponent ->
            val definition = opponent.statBlock.toActorDefinition(language = AppLocale.language)
            repeat(opponent.quantity) { index ->
                val instanceId = if (opponent.quantity == 1) {
                    definition.id()
                } else {
                    "${definition.id()}-${index + 1}"
                }
                setups += CombatantSetup(instanceId, definition)
                footprints[instanceId] = opponent.statBlock.size.squaresPerSide
                opponentIds += instanceId
            }
        }
        val session = CombatSession.fromCombatants(name, seed, setups)
        session.setPartyCombatants(party.map { it.id })
        val grid = MapGrid(gridColumns, gridRows, feetPerSquare)
        session.configureMap(grid)
        deploy(session, grid, party.map { it.id }, opponentIds, footprints)
        session.markReady()
        setups.forEach { session.useStaticInitiative(it.instanceId(), D20Mode.NORMAL) }
        session.start()
        return session
    }

    /**
     * Schiera i due gruppi ai lati della mappa.
     *
     * Una partita inclusa deve essere giocabile appena aperta: una griglia vuota
     * costringerebbe a trascinare otto segnaposti prima ancora di cominciare. Le
     * posizioni sono fisse, non casuali, cosi' la stessa partita si apre uguale.
     */
    private fun deploy(
        session: CombatSession,
        grid: MapGrid,
        allies: List<String>,
        opponents: List<String>,
        footprints: Map<String, Int>,
    ) {
        fun column(index: Int, side: Int, fromLeft: Boolean): Int = if (fromLeft) {
            (1 + index * (side + 1)).coerceAtMost(grid.columns() - side)
        } else {
            (grid.columns() - side - index * (side + 1)).coerceAtLeast(0)
        }

        listOf(allies to true, opponents to false).forEach { (group, fromLeft) ->
            var row = 1
            var band = 0
            group.forEach { id ->
                val side = footprints[id] ?: 1
                if (row + side > grid.rows()) {
                    row = 1
                    band += 1
                }
                session.placeCombatant(id, GridPosition(column(band, side, fromLeft), row), side)
                row += side + 1
            }
        }
    }

    private companion object {
        /** Seme fisso: la stessa partita inclusa deve tirare gli stessi dadi ovunque. */
        const val DEFAULT_SEED = 20260728L
    }
}

/**
 * Le tre partite incluse con l'app, una per grado di esperienza.
 *
 * Coprono i tre momenti in cui un tavolo si trova davvero: il primo scontro, la
 * squadra ormai rodata e l'ultima notte della campagna. Le squadre usano fra
 * tutte le dodici classi dello SRD, e gli avversari sono tarati sui PE
 * dell'incontro, non sul gusto del momento.
 */
internal class SessionTemplates private constructor(private val language: AppLanguage) {

    private val bestiary = TemplateBestiary.of(language)
    private val parties = TemplateParties.of(language)
    private fun say(italian: String, english: String): String = language.pick(italian, english)

    val ruins = SessionTemplate(
        id = "template-vallecupa",
        language = language,
        name = say("Le rovine di Vallecupa", "The Ruins of Deepvale"),
        partyLevel = 1,
        summary = say(
            "Quattro esordienti contro i predoni accampati fra i muri crollati.",
            "Four newcomers against the raiders camped among the fallen walls.",
        ),
        description = say(
            "Il primo incontro di una campagna: nessun potere che cambia le regole, " +
                "solo una squadra al 1º livello, un capobanda che urla ordini e un cane che " +
                "morde alle spalle. Serve a prendere confidenza con turni, azioni e mappa.",
            "A campaign's first fight: nothing that bends the rules, just a 1st-level party, " +
                "a chief barking orders and a hound that bites from behind. It is here to get " +
                "you used to turns, actions and the map.",
        ),
        partyPlans = parties.novices,
        opponents = listOf(
            TemplateOpponent(bestiary.raider, quantity = 2),
            TemplateOpponent(bestiary.ashHound),
            TemplateOpponent(bestiary.raiderChief),
        ),
        gridColumns = 20,
        gridRows = 15,
    )

    val ford = SessionTemplate(
        id = "template-guado",
        language = language,
        name = say("Il guado di ferro", "The Iron Ford"),
        partyLevel = 4,
        summary = say(
            "Una squadra rodata al 4º livello deve passare un guado tenuto da mercenari.",
            "A seasoned 4th-level party has to cross a ford held by mercenaries.",
        ),
        description = say(
            "La squadra ha la prima sottoclasse e sa cosa sa fare. Dall'altra parte " +
                "dell'acqua bassa c'è chi riscuote il pedaggio con l'alabarda: due lancieri in " +
                "formazione, i cani sulle secche e una comandante che non arretra.",
            "The party has its first subclass and knows what it can do. On the far side of the " +
                "shallows somebody collects the toll with a halberd: two lancers in formation, " +
                "the hounds on the shoals, and a captain who does not give ground.",
        ),
        partyPlans = parties.veterans,
        opponents = listOf(
            TemplateOpponent(bestiary.ironLancer, quantity = 2),
            TemplateOpponent(bestiary.marshCur, quantity = 2),
            TemplateOpponent(bestiary.fordCaptain),
        ),
        gridColumns = 24,
        gridRows = 18,
    )

    val crown = SessionTemplate(
        id = "template-corona",
        language = language,
        name = say("La corona spezzata", "The Broken Crown"),
        partyLevel = 20,
        summary = say(
            "Il tetto dello SRD: quattro leggende contro ciò che è rimasto sveglio là sotto.",
            "The ceiling of the SRD: four legends against whatever stayed awake down there.",
        ),
        description = say(
            "Ultimo scontro di una campagna. La squadra è al 20º livello, con i Doni " +
                "epici già presi; Vharok si rialza finché la sua corona non è distrutta, e i " +
                "suoi custodi non lasciano che ci si arrivi comodamente.",
            "A campaign's last fight. The party is 20th level with its Epic Boons already " +
                "taken; Vharok gets back up until his crown is destroyed, and his wardens do " +
                "not make getting there comfortable.",
        ),
        partyPlans = parties.legends,
        opponents = listOf(
            TemplateOpponent(bestiary.ashWarden, quantity = 2),
            TemplateOpponent(bestiary.brokenCrown),
        ),
        gridColumns = 30,
        gridRows = 22,
    )

    val all: List<SessionTemplate> = listOf(ruins, ford, crown)

    /** Il template proposto all'avvio, prima che l'utente scelga il suo. */
    val default: SessionTemplate get() = ruins

    companion object {
        private val byLanguage = mutableMapOf<AppLanguage, SessionTemplates>()

        /**
         * Le partite incluse in una lingua.
         *
         * Costruirle non e' gratis — la squadra del 20º livello sale ottanta
         * livelli — quindi ogni lingua si costruisce una volta sola.
         */
        fun of(language: AppLanguage): SessionTemplates = synchronized(byLanguage) {
            byLanguage.getOrPut(language) { SessionTemplates(language) }
        }
    }
}
