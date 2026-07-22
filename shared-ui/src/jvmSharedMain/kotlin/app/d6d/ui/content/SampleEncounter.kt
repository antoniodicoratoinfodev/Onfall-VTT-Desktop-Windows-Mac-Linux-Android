package app.d6d.ui.content

import app.d6d.domain.combat.AbilityDefinition
import app.d6d.domain.combat.ActivationCost
import app.d6d.domain.combat.ActorDefinition
import app.d6d.domain.combat.AutomationStatus
import app.d6d.domain.combat.ConditionType
import app.d6d.domain.combat.D20Mode
import app.d6d.domain.combat.DamageFormula
import app.d6d.domain.combat.DamageType
import app.d6d.domain.combat.ResolutionMethod
import app.d6d.domain.space.MapGrid
import app.d6d.engine.CombatSession

/**
 * Contenuto dimostrativo interamente originale.
 *
 * Non contiene stat block, testi o nomi provenienti dai manuali commerciali: il
 * paragrafo 17 del documento consente di distribuire solo materiale SRD attribuito,
 * contenuti propri o di terze parti con licenza compatibile. Questi sono contenuti
 * propri, quindi distribuibili senza vincoli aggiuntivi.
 */
object SampleEncounter {

    private const val RULESET = "5.2.1"
    private const val VERSION = "1.0.0"
    private const val SOURCE = "content-original"

    private fun attack(
        id: String,
        name: String,
        attackBonus: Int,
        rangeFeet: Int,
        damage: List<DamageFormula>,
        cost: ActivationCost = ActivationCost.ACTION,
        rulesText: String = "",
    ) = AbilityDefinition(
        id,
        VERSION,
        SOURCE,
        RULESET,
        name,
        cost,
        ResolutionMethod.ATTACK_ROLL,
        attackBonus,
        rangeFeet,
        1,
        damage,
        AutomationStatus.AUTOMATED,
        rulesText,
    )

    private fun actor(
        id: String,
        name: String,
        armorClass: Int,
        maxHitPoints: Int,
        currentHitPoints: Int = maxHitPoints,
        speedFeet: Int,
        initiativeModifier: Int,
        constitutionSaveBonus: Int,
        abilities: List<AbilityDefinition>,
        resistances: Set<DamageType> = emptySet(),
        vulnerabilities: Set<DamageType> = emptySet(),
        damageImmunities: Set<DamageType> = emptySet(),
        conditionImmunities: Set<ConditionType> = emptySet(),
    ) = ActorDefinition(
        id,
        VERSION,
        RULESET,
        name,
        armorClass,
        maxHitPoints,
        currentHitPoints.coerceIn(0, maxHitPoints),
        0,
        speedFeet,
        initiativeModifier,
        // Il punteggio statico e' normalmente 10 piu' tutti i modificatori
        // all'iniziativa, e resta un campo distinto dal modificatore.
        10 + initiativeModifier,
        constitutionSaveBonus,
        resistances,
        vulnerabilities,
        damageImmunities,
        conditionImmunities,
        abilities,
    )

    fun party(): List<ActorDefinition> = listOf(
        actor(
            id = "pg-kaelen",
            name = "Kaelen del Vallo",
            armorClass = 18,
            maxHitPoints = 34,
            currentHitPoints = 28,
            speedFeet = 30,
            initiativeModifier = 1,
            constitutionSaveBonus = 5,
            abilities = listOf(
                attack(
                    "arma-spadone", "Spadone", 6, 5,
                    listOf(DamageFormula.dice(DamageType.SLASHING, 2, 6, 4)),
                    rulesText = "Attacco in mischia con arma. Portata 5 piedi.",
                ),
                attack(
                    "arma-giavellotto", "Giavellotto", 5, 30,
                    listOf(DamageFormula.dice(DamageType.PIERCING, 1, 6, 3)),
                    rulesText = "Attacco a distanza. Gittata 30/120 piedi.",
                ),
            ),
        ),
        actor(
            id = "pg-mirethe",
            name = "Mirethe Cantarune",
            armorClass = 13,
            maxHitPoints = 22,
            speedFeet = 30,
            initiativeModifier = 3,
            constitutionSaveBonus = 3,
            abilities = listOf(
                attack(
                    "inc-dardo-runico", "Dardo Runico", 6, 60,
                    listOf(DamageFormula.dice(DamageType.FORCE, 2, 6, 0)),
                    rulesText = "Trucchetto d'attacco a distanza. Non consuma slot.",
                ),
                attack(
                    "arma-bastone", "Bastone", 2, 5,
                    listOf(DamageFormula.dice(DamageType.BLUDGEONING, 1, 6, 0)),
                    rulesText = "Attacco in mischia con arma.",
                ),
            ),
        ),
        actor(
            id = "pg-dorn",
            name = "Dorn Martelloscuro",
            armorClass = 16,
            maxHitPoints = 30,
            speedFeet = 25,
            initiativeModifier = 0,
            constitutionSaveBonus = 4,
            resistances = setOf(DamageType.POISON),
            abilities = listOf(
                attack(
                    "arma-martello", "Martello da Guerra", 5, 5,
                    listOf(DamageFormula.dice(DamageType.BLUDGEONING, 1, 8, 3)),
                    rulesText = "Attacco in mischia con arma.",
                ),
            ),
        ),
        actor(
            id = "pg-sylva",
            name = "Sylva Passoleggero",
            armorClass = 15,
            maxHitPoints = 26,
            speedFeet = 35,
            initiativeModifier = 4,
            constitutionSaveBonus = 2,
            abilities = listOf(
                attack(
                    "arma-arco", "Arco Lungo", 7, 150,
                    listOf(DamageFormula.dice(DamageType.PIERCING, 1, 8, 4)),
                    rulesText = "Attacco a distanza. Gittata 150/600 piedi.",
                ),
                attack(
                    "arma-pugnale", "Pugnale", 6, 5,
                    listOf(DamageFormula.dice(DamageType.PIERCING, 1, 4, 4)),
                    cost = ActivationCost.BONUS_ACTION,
                    rulesText = "Arma Leggera: attacco come Azione Bonus dopo l'Azione Attacco.",
                ),
            ),
        ),
    )

    fun enemies(): List<ActorDefinition> = listOf(
        actor(
            id = "nem-predone-a",
            name = "Predone della Cripta A",
            armorClass = 14,
            maxHitPoints = 20,
            speedFeet = 30,
            initiativeModifier = 2,
            constitutionSaveBonus = 1,
            abilities = listOf(
                attack(
                    "nem-scimitarra", "Scimitarra", 4, 5,
                    listOf(DamageFormula.dice(DamageType.SLASHING, 1, 6, 2)),
                    rulesText = "Attacco in mischia con arma.",
                ),
            ),
        ),
        actor(
            id = "nem-predone-b",
            name = "Predone della Cripta B",
            armorClass = 14,
            maxHitPoints = 20,
            speedFeet = 30,
            initiativeModifier = 2,
            constitutionSaveBonus = 1,
            abilities = listOf(
                attack(
                    "nem-scimitarra", "Scimitarra", 4, 5,
                    listOf(DamageFormula.dice(DamageType.SLASHING, 1, 6, 2)),
                    rulesText = "Attacco in mischia con arma.",
                ),
            ),
        ),
        actor(
            id = "nem-mastino",
            name = "Mastino d'Ombra",
            armorClass = 13,
            maxHitPoints = 16,
            speedFeet = 40,
            initiativeModifier = 3,
            constitutionSaveBonus = 1,
            resistances = setOf(DamageType.NECROTIC),
            vulnerabilities = setOf(DamageType.RADIANT),
            abilities = listOf(
                attack(
                    "nem-morso", "Morso Gelido", 4, 5,
                    listOf(
                        DamageFormula.dice(DamageType.PIERCING, 1, 6, 2),
                        DamageFormula.dice(DamageType.NECROTIC, 1, 4, 0),
                    ),
                    rulesText = "Attacco in mischia. Due componenti di danno di tipo diverso.",
                ),
            ),
        ),
        actor(
            id = "nem-evocatore",
            name = "Evocatore Ammantato",
            armorClass = 12,
            maxHitPoints = 24,
            speedFeet = 30,
            initiativeModifier = 1,
            constitutionSaveBonus = 3,
            abilities = listOf(
                attack(
                    "nem-scarica", "Scarica Ombrosa", 5, 90,
                    listOf(DamageFormula.dice(DamageType.NECROTIC, 2, 8, 0)),
                    rulesText = "Attacco magico a distanza.",
                ),
            ),
        ),
    )

    /**
     * Prepara un incontro gia' avviato.
     *
     * Segue l'ordine imposto dal motore: aggiunta dei combattenti, dichiarazione
     * degli schieramenti, `markReady`, iniziativa per tutti e infine `start`.
     * L'iniziativa usa il punteggio statico, cosi' l'incontro dimostrativo parte
     * sempre uguale e resta riproducibile a parita' di seed.
     */
    fun startedSession(encounterId: String = "cripta-dei-predoni", seed: Long = 20260721L): CombatSession {
        val party = party()
        val enemies = enemies()
        val all = party + enemies
        val session = CombatSession.fromActors(encounterId, seed, all)
        session.setPartyCombatants(party.map { it.id() })
        session.configureMap(MapGrid(20, 15, 5))
        session.markReady()
        all.forEach { session.useStaticInitiative(it.id(), D20Mode.NORMAL) }
        session.start()
        return session
    }
}
