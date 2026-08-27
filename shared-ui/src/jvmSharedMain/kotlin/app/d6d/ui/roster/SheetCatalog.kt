package app.d6d.ui.roster

import app.d6d.domain.campaign.ActorKind
import app.d6d.domain.campaign.ActorTemplate
import app.d6d.domain.catalog.ActorCatalogEntry
import app.d6d.domain.combat.AbilityDefinition
import app.d6d.domain.combat.ActivationCost
import app.d6d.domain.combat.ActorDefinition
import app.d6d.sheet.Ability
import app.d6d.sheet.CatalogAbility
import app.d6d.ui.i18n.AppLocale
import app.d6d.sheet.CharacterSheet
import app.d6d.sheet.CreatureSize
import app.d6d.sheet.MonsterSpeeds
import app.d6d.sheet.MonsterStatBlock
import app.d6d.sheet.StatBlockActorKind
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
fun CharacterSheet.toCatalogEntry(abilityCatalog: List<CatalogAbility> = emptyList()): ActorCatalogEntry {
    // Il nome si legge *dalla definizione*, non si ricalcola. ActorCatalogEntry
    // esige che i due coincidano, e finche' erano due espressioni separate
    // bastava tradurne una perche' una scheda senza nome si salvasse e poi il
    // catalogo non si lasciasse piu' rigenerare. Cosi' non possono divergere.
    val definition = toActorDefinition(
        abilityCatalog = abilityCatalog,
        language = AppLocale.language,
    )
    return ActorCatalogEntry(
        ActorTemplate(id, definition.name, ActorKind.PLAYER_CHARACTER, level.coerceIn(1, 20)),
        definition,
        // Un personaggio giocante appartiene per impostazione predefinita alla squadra.
        true,
        // I personaggi non usano un Grado di Sfida: il costruttore lo esige a zero.
        BigDecimal.ZERO,
        0L,
    )
}

/** Entrata di catalogo derivata da uno stat block. */
fun MonsterStatBlock.toCatalogEntry(): ActorCatalogEntry {
    // Come sopra: una sola espressione per il nome.
    val definition = toActorDefinition(language = AppLocale.language)
    val challengeRating = runCatching { BigDecimal(challengeRating.trim()) }.getOrElse { BigDecimal.ZERO }
    return ActorCatalogEntry(
        ActorTemplate(
            id,
            definition.name,
            if (actorKind == StatBlockActorKind.NPC) ActorKind.NON_PLAYER_CHARACTER else ActorKind.CREATURE,
            if (actorKind == StatBlockActorKind.NPC) classLevel.coerceIn(1, 20) else 0,
        ),
        definition,
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
fun characterSheetFrom(
    definition: ActorDefinition,
    abilityCatalog: List<CatalogAbility> = emptyList(),
): CharacterSheet {
    val dexScore = (10 + 2 * definition.initiativeModifier()).coerceIn(1, 30)
    val conScore = (10 + 2 * definition.constitutionSaveBonus()).coerceIn(1, 30)
    val catalogIds = abilityCatalog.mapTo(mutableSetOf()) { it.id }
    return CharacterSheet(
        id = definition.id(),
        // Promuovere una proiezione a scheda la scrive adesso, nella lingua di
        // adesso: e' li' che nasce il suo testo.
        contentLanguage = AppLocale.language,
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
        weapons = definition.abilities().filterNot { it.id() in catalogIds }.map { it.toWeaponEntry() },
        abilityIds = definition.abilities().map { it.id() }.filter { it in catalogIds },
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
        attackAbility = attackAbility()?.let { Ability.valueOf(it.name) },
        spellOrCantrip = spellOrCantrip(),
        diceCount = dice?.count() ?: 1,
        diceSides = dice?.sides() ?: 6,
        damageModifier = dice?.modifier() ?: 0,
        fixedDamage = if (dice == null) formula?.fixedAmount() ?: 0 else 0,
        damageType = formula?.type() ?: app.d6d.domain.combat.DamageType.SLASHING,
        rangeFeet = rangeFeet(),
        note = rulesText(),
        bonusAction = activationCost() == ActivationCost.BONUS_ACTION,
    )
}
