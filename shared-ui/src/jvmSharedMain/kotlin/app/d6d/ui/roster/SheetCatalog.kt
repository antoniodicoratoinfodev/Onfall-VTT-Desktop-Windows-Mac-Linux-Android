package app.d6d.ui.roster

import app.d6d.domain.campaign.ActorKind
import app.d6d.domain.campaign.ActorTemplate
import app.d6d.domain.catalog.ActorCatalogEntry
import app.d6d.domain.combat.AbilityDefinition
import app.d6d.domain.combat.ActivationCost
import app.d6d.domain.combat.ActorDefinition
import app.d6d.sheet.Ability
import app.d6d.sheet.CharacterSheet
import app.d6d.sheet.CreatureSize
import app.d6d.sheet.MonsterSpeeds
import app.d6d.sheet.MonsterStatBlock
import app.d6d.sheet.Proficiency
import app.d6d.sheet.StatBlockEntry
import app.d6d.sheet.WeaponEntry
import java.math.BigDecimal

/**
 * Caselle per lato occupate da una taglia sulla griglia.
 *
 * Fino a Media si sta in una casella; Grande occupa 2x2, Enorme 3x3, Mastodontica
 * 4x4. E' il ponte fra la taglia autorevole della scheda e la geometria della mappa.
 */
val CreatureSize.squaresPerSide: Int
    get() = when (this) {
        CreatureSize.TINY, CreatureSize.SMALL, CreatureSize.MEDIUM -> 1
        CreatureSize.LARGE -> 2
        CreatureSize.HUGE -> 3
        CreatureSize.GARGANTUAN -> 4
    }

/**
 * Ponte fra le schede e il catalogo da combattimento.
 *
 * La scheda e' la fonte autorevole; il catalogo (`ActorCatalogEntry`) e' una sua
 * proiezione. Queste funzioni tengono le due rappresentazioni allineate in un solo
 * punto, cosi' nessuno le puo' far divergere modificandole separatamente.
 */

/** Entrata di catalogo derivata da una scheda di personaggio. */
fun CharacterSheet.toCatalogEntry(): ActorCatalogEntry {
    // Nome e definizione devono coincidere: uso la stessa forma di ripiego che
    // usa toActorDefinition, altrimenti il costruttore di ActorCatalogEntry rifiuta.
    val safeName = characterName.ifBlank { "Senza nome" }
    return ActorCatalogEntry(
        ActorTemplate(id, safeName, ActorKind.PLAYER_CHARACTER, level.coerceIn(1, 20)),
        toActorDefinition(),
        // Un personaggio giocante appartiene per impostazione predefinita alla squadra.
        true,
        // I personaggi non usano un Grado di Sfida: il costruttore lo esige a zero.
        BigDecimal.ZERO,
        0L,
    )
}

/** Entrata di catalogo derivata da uno stat block. */
fun MonsterStatBlock.toCatalogEntry(): ActorCatalogEntry {
    val safeName = name.ifBlank { "Creatura senza nome" }
    val challengeRating = runCatching { BigDecimal(challengeRating.trim()) }.getOrElse { BigDecimal.ZERO }
    return ActorCatalogEntry(
        ActorTemplate(id, safeName, ActorKind.CREATURE, 0),
        toActorDefinition(),
        false,
        challengeRating.max(BigDecimal.ZERO),
        baseXp.coerceAtLeast(0),
    )
}

/**
 * Ricostruisce una scheda di personaggio da una proiezione da combattimento.
 *
 * Serve a promuovere contenuto leggero a scheda completa senza degradarne le
 * statistiche: i valori di combattimento vengono conservati esattamente.
 *
 * I campi che la scheda calcola invece di memorizzare — modificatore d'iniziativa,
 * tiro salvezza su Costituzione — non sono impostabili direttamente. Vengono
 * riottenuti scegliendo i punteggi di caratteristica che li producono: Destrezza
 * pari a 10 + due volte il modificatore d'iniziativa, Costituzione pari a 10 + due
 * volte il bonus al tiro salvezza. Cosi' la scheda derivata riproduce la stessa
 * iniziativa e lo stesso tiro salvezza di partenza.
 */
fun characterSheetFrom(definition: ActorDefinition): CharacterSheet {
    val dexScore = (10 + 2 * definition.initiativeModifier()).coerceIn(1, 30)
    val conScore = (10 + 2 * definition.constitutionSaveBonus()).coerceIn(1, 30)
    return CharacterSheet(
        id = definition.id(),
        characterName = definition.name(),
        level = 1,
        armorClass = definition.armorClass(),
        currentHitPoints = definition.currentHitPoints(),
        maxHitPoints = definition.maxHitPoints(),
        temporaryHitPoints = definition.temporaryHitPoints(),
        speedFeet = definition.speedFeet(),
        abilityScores = mapOf(
            Ability.STRENGTH to 10,
            Ability.DEXTERITY to dexScore,
            Ability.CONSTITUTION to conScore,
            Ability.INTELLIGENCE to 10,
            Ability.WISDOM to 10,
            Ability.CHARISMA to 10,
        ),
        // Con punteggio scelto e nessuna competenza, il tiro salvezza su Costituzione
        // vale il solo modificatore, che e' il bonus originale.
        saveProficiencies = emptyMap(),
        weapons = definition.abilities().map { it.toWeaponEntry() },
    )
}

/** Ricostruisce uno stat block da una proiezione da combattimento. */
fun monsterStatBlockFrom(
    definition: ActorDefinition,
    challengeRating: String = "1",
    baseXp: Long = 100,
): MonsterStatBlock = MonsterStatBlock(
    id = definition.id(),
    name = definition.name(),
    size = CreatureSize.MEDIUM,
    armorClass = definition.armorClass(),
    initiativeModifier = definition.initiativeModifier(),
    initiativeScore = definition.initiativeScore(),
    averageHitPoints = definition.maxHitPoints(),
    hitDiceCount = 1,
    hitDiceSides = 8,
    speeds = MonsterSpeeds(walk = definition.speedFeet()),
    abilityScores = mapOf(
        Ability.STRENGTH to 10,
        Ability.DEXTERITY to (10 + 2 * definition.initiativeModifier()).coerceIn(1, 30),
        Ability.CONSTITUTION to (10 + 2 * definition.constitutionSaveBonus()).coerceIn(1, 30),
        Ability.INTELLIGENCE to 10,
        Ability.WISDOM to 10,
        Ability.CHARISMA to 10,
    ),
    saveProficiencies = if (definition.constitutionSaveBonus() != 0) {
        mapOf(Ability.CONSTITUTION to Proficiency.PROFICIENT)
    } else {
        emptyMap()
    },
    resistances = definition.resistances().toSet(),
    vulnerabilities = definition.vulnerabilities().toSet(),
    damageImmunities = definition.damageImmunities().toSet(),
    conditionImmunities = definition.conditionImmunities().toSet(),
    challengeRating = challengeRating,
    baseXp = baseXp,
    actions = definition.abilities().map { ability ->
        StatBlockEntry(ability.name(), ability.rulesText(), ability.toWeaponEntry())
    },
)

/** Converte una capacita' d'attacco in una riga della tabella armi. */
private fun AbilityDefinition.toWeaponEntry(): WeaponEntry {
    val formula = damage().firstOrNull()
    val dice = formula?.dice()
    return WeaponEntry(
        name = name(),
        attackBonus = attackBonus(),
        diceCount = dice?.count() ?: 1,
        diceSides = dice?.sides() ?: 6,
        damageModifier = dice?.modifier() ?: (formula?.fixedAmount() ?: 0),
        damageType = formula?.type() ?: app.d6d.domain.combat.DamageType.SLASHING,
        rangeFeet = rangeFeet(),
        note = rulesText(),
        bonusAction = activationCost() == ActivationCost.BONUS_ACTION,
    )
}
