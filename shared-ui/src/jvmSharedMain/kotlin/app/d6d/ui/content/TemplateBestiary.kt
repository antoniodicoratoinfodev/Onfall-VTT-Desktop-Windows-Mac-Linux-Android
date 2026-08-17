package app.d6d.ui.content

import app.d6d.domain.combat.ConditionType
import app.d6d.domain.combat.DamageType
import app.d6d.sheet.Ability
import app.d6d.sheet.CreatureSize
import app.d6d.sheet.abilityModifier
import app.d6d.sheet.MonsterSpeeds
import app.d6d.sheet.MonsterStatBlock
import app.d6d.sheet.Proficiency
import app.d6d.sheet.Skill
import app.d6d.sheet.StatBlockEntry
import app.d6d.i18n.AppLanguage
import app.d6d.i18n.pick
import app.d6d.sheet.WeaponEntry
import app.d6d.sheet.i18n.distanceLabel

/**
 * Creature dei template: contenuto interamente originale.
 *
 * Nessun nome, testo o statistica proviene dai manuali commerciali. Sono pero'
 * costruite con le regole del documento: dado vita legato alla taglia, PF medi
 * coerenti coi dadi, bonus di competenza suggerito dal Grado di Sfida e PE presi
 * dalla tabella. Cosi' un DM puo' confrontarle con qualsiasi altro stat block.
 *
 * Essendo contenuto nostro si traduce, e le due lingue stanno sulla stessa riga:
 * il nome inglese di una creatura si legge accanto a quello italiano, dove serve
 * per confrontarli. Le distanze nei testi non si traducono ma si convertono, come
 * ovunque nell'applicazione.
 *
 * Un bestiario per lingua, costruito una volta sola: gli stat block sono immutabili
 * e ricostruirli a ogni lettura sarebbe lavoro sprecato.
 */
internal class TemplateBestiary private constructor(private val language: AppLanguage) {

    /** Distanza dentro un testo di regole, nella misura di chi legge. */
    private fun reach(feet: Int): String = distanceLabel(feet, language)

    /** Sceglie fra la forma italiana e quella inglese. */
    private fun say(italian: String, english: String): String = language.pick(italian, english)

    /** PE della tabella del Grado di Sfida, per i gradi usati qui. */
    private fun xpFor(challengeRating: String): Long = when (challengeRating) {
        "1/8" -> 25
        "1/4" -> 50
        "1/2" -> 100
        "1" -> 200
        "2" -> 450
        "3" -> 700
        "4" -> 1_100
        "5" -> 1_800
        "9" -> 5_000
        "20" -> 25_000
        else -> error("Grado di sfida senza PE in tabella: $challengeRating")
    }

    /** Dado vita imposto dalla taglia. */
    private fun hitDieFor(size: CreatureSize): Int = when (size) {
        CreatureSize.TINY -> 4
        CreatureSize.SMALL -> 6
        CreatureSize.MEDIUM -> 8
        CreatureSize.LARGE -> 10
        CreatureSize.HUGE -> 12
        CreatureSize.GARGANTUAN -> 20
    }

    /**
     * Assembla lo stat block calcolando cio' che le regole derivano.
     *
     * PF medi, modificatore dei dadi vita, iniziativa e bonus di competenza non
     * si scrivono a mano: sarebbero quattro occasioni di sbagliare in silenzio.
     */
    private fun creature(
        id: String,
        name: String,
        size: CreatureSize,
        type: String,
        alignment: String,
        challengeRating: String,
        proficiencyBonus: Int,
        armorClass: Int,
        hitDiceCount: Int,
        scores: Map<Ability, Int>,
        speeds: MonsterSpeeds = MonsterSpeeds(),
        saveProficiencies: Map<Ability, Proficiency> = emptyMap(),
        skillProficiencies: Map<Skill, Proficiency> = emptyMap(),
        resistances: Set<DamageType> = emptySet(),
        vulnerabilities: Set<DamageType> = emptySet(),
        damageImmunities: Set<DamageType> = emptySet(),
        conditionImmunities: Set<ConditionType> = emptySet(),
        senses: String = "",
        languages: String = "—",
        gear: String = "",
        habitat: String = "",
        treasureTheme: String = say("Nessuno", "None"),
        lairXp: Long? = null,
        traits: List<StatBlockEntry> = emptyList(),
        actions: List<StatBlockEntry> = emptyList(),
        bonusActions: List<StatBlockEntry> = emptyList(),
        reactions: List<StatBlockEntry> = emptyList(),
        legendaryActions: List<StatBlockEntry> = emptyList(),
    ): MonsterStatBlock {
        val hitDieSides = hitDieFor(size)
        val hitDiceModifier = abilityModifier(scores.getValue(Ability.CONSTITUTION)) * hitDiceCount
        val dexterityModifier = abilityModifier(scores.getValue(Ability.DEXTERITY))
        // Media dei dadi vita arrotondata per difetto, come la riporta lo stat block.
        val average = hitDiceCount * (hitDieSides + 1) / 2 + hitDiceModifier
        return MonsterStatBlock(
            id = id,
            // La creatura nasce nella lingua con cui questo bestiario e' stato
            // costruito: e' cio' che permette di rigenerarla nell'altra.
            contentLanguage = language,
            name = name,
            size = size,
            type = type,
            alignment = alignment,
            armorClass = armorClass,
            initiativeModifier = dexterityModifier,
            initiativeScore = 10 + dexterityModifier,
            averageHitPoints = average,
            hitDiceCount = hitDiceCount,
            hitDiceSides = hitDieSides,
            hitDiceModifier = hitDiceModifier,
            speeds = speeds,
            abilityScores = scores,
            saveProficiencies = saveProficiencies,
            skillProficiencies = skillProficiencies,
            resistances = resistances,
            vulnerabilities = vulnerabilities,
            damageImmunities = damageImmunities,
            conditionImmunities = conditionImmunities,
            gear = gear,
            senses = senses,
            languages = languages,
            challengeRating = challengeRating,
            baseXp = xpFor(challengeRating),
            lairXp = lairXp,
            proficiencyBonus = proficiencyBonus,
            traits = traits,
            actions = actions,
            bonusActions = bonusActions,
            reactions = reactions,
            legendaryActions = legendaryActions,
            habitat = habitat,
            treasureTheme = treasureTheme,
        )
    }

    private fun scores(
        strength: Int,
        dexterity: Int,
        constitution: Int,
        intelligence: Int,
        wisdom: Int,
        charisma: Int,
    ): Map<Ability, Int> = mapOf(
        Ability.STRENGTH to strength,
        Ability.DEXTERITY to dexterity,
        Ability.CONSTITUTION to constitution,
        Ability.INTELLIGENCE to intelligence,
        Ability.WISDOM to wisdom,
        Ability.CHARISMA to charisma,
    )

    /** Attacco eseguibile dal motore: nome, tiro, dado, tipo e portata. */
    private fun strike(
        name: String,
        text: String,
        attackBonus: Int,
        diceCount: Int,
        diceSides: Int,
        damageModifier: Int,
        damageType: DamageType,
        rangeFeet: Int = 5,
        bonusAction: Boolean = false,
    ) = StatBlockEntry(
        name = name,
        text = text,
        attack = WeaponEntry(
            name = name,
            attackBonus = attackBonus,
            diceCount = diceCount,
            diceSides = diceSides,
            damageModifier = damageModifier,
            damageType = damageType,
            rangeFeet = rangeFeet,
            bonusAction = bonusAction,
        ),
    )

    // --- Le rovine di Vallecupa: predoni accampati fra i muri crollati ---

    val raider = creature(
        id = "creatura-predone-vallecupa",
        name = say("Predone di Vallecupa", "Deepvale Raider"),
        size = CreatureSize.MEDIUM,
        type = say("Umanoide", "Humanoid"),
        alignment = say("Caotico Neutrale", "Chaotic Neutral"),
        challengeRating = "1/4",
        proficiencyBonus = 2,
        armorClass = 14,
        hitDiceCount = 2,
        scores = scores(12, 14, 12, 9, 10, 8),
        skillProficiencies = mapOf(Skill.FURTIVITA to Proficiency.PROFICIENT),
        gear = say("Armatura di cuoio, scimitarra, tre torce", "Leather armor, scimitar, three torches"),
        languages = say("Comune", "Common"),
        habitat = say("Rovine, colline", "Ruins, hills"),
        treasureTheme = say("Individuale", "Individual"),
        actions = listOf(
            strike(
                say("Scimitarra", "Scimitar"),
                say("Attacco in mischia. Portata ${reach(5)}.", "Melee attack. Reach ${reach(5)}."),
                attackBonus = 4,
                diceCount = 1,
                diceSides = 6,
                damageModifier = 2,
                damageType = DamageType.SLASHING,
            ),
        ),
        bonusActions = listOf(
            StatBlockEntry(
                say("Sparizione fra le pietre", "Vanish Among the Stones"),
                say(
                    "Compie l'azione Nascondersi se ha almeno mezza copertura fornita dalle macerie.",
                    "Takes the Hide action if it has at least half cover from the rubble.",
                ),
            ),
        ),
    )

    val ashHound = creature(
        id = "creatura-mastino-cinereo",
        name = say("Mastino cinereo", "Ash Hound"),
        size = CreatureSize.MEDIUM,
        type = say("Bestia", "Beast"),
        alignment = say("Impassibile", "Unaligned"),
        challengeRating = "1/2",
        proficiencyBonus = 2,
        armorClass = 13,
        hitDiceCount = 4,
        scores = scores(14, 15, 13, 3, 12, 6),
        speeds = MonsterSpeeds(walk = 40),
        skillProficiencies = mapOf(
            Skill.PERCEZIONE to Proficiency.PROFICIENT,
            Skill.FURTIVITA to Proficiency.PROFICIENT,
        ),
        senses = say("Scurovisione ${reach(60)}", "Darkvision ${reach(60)}"),
        habitat = say("Rovine, sottosuolo", "Ruins, underground"),
        traits = listOf(
            StatBlockEntry(
                say("Fiuto della cenere", "Ash Scent"),
                say(
                    "Ha vantaggio alle prove di Saggezza (Percezione) basate sull'olfatto.",
                    "Has advantage on Wisdom (Perception) checks that rely on smell.",
                ),
            ),
        ),
        actions = listOf(
            strike(
                say("Morso", "Bite"),
                say(
                    "Attacco in mischia. Portata ${reach(5)}. Se il bersaglio è una creatura Media " +
                        "o più piccola, effettua un tiro salvezza su Forza (CD 12) o cade Prono.",
                    "Melee attack. Reach ${reach(5)}. If the target is a Medium or smaller " +
                        "creature, it makes a DC 12 Strength saving throw or falls Prone.",
                ),
                attackBonus = 4,
                diceCount = 1,
                diceSides = 8,
                damageModifier = 2,
                damageType = DamageType.PIERCING,
            ),
        ),
    )

    val raiderChief = creature(
        id = "creatura-ossagrigia",
        name = say("Ossagrigia, capobanda", "Greybone, Warband Chief"),
        size = CreatureSize.MEDIUM,
        type = say("Umanoide", "Humanoid"),
        alignment = say("Caotico Malvagio", "Chaotic Evil"),
        challengeRating = "1",
        proficiencyBonus = 2,
        armorClass = 15,
        hitDiceCount = 5,
        scores = scores(16, 12, 14, 10, 11, 13),
        saveProficiencies = mapOf(Ability.STRENGTH to Proficiency.PROFICIENT),
        skillProficiencies = mapOf(Skill.INTIMIDIRE to Proficiency.PROFICIENT),
        gear = say("Corazza a scaglie, mazzafrusto, corno da guerra", "Scale mail, flail, war horn"),
        languages = say("Comune", "Common"),
        habitat = say("Rovine, colline", "Ruins, hills"),
        treasureTheme = say("Tesoro del capobanda", "Chief's hoard"),
        actions = listOf(
            strike(
                say("Mazzafrusto", "Flail"),
                say("Attacco in mischia. Portata ${reach(5)}.", "Melee attack. Reach ${reach(5)}."),
                attackBonus = 5,
                diceCount = 1,
                diceSides = 8,
                damageModifier = 3,
                damageType = DamageType.BLUDGEONING,
            ),
        ),
        bonusActions = listOf(
            StatBlockEntry(
                say("Ordine urlato", "Barked Order"),
                say(
                    "Un alleato che può sentirlo entro ${reach(30)} compie subito la propria " +
                        "Reazione per attaccare, se ha un bersaglio a portata.",
                    "One ally that can hear it within ${reach(30)} immediately uses its Reaction " +
                        "to attack, if it has a target in range.",
                ),
            ),
        ),
    )

    // --- Il guado di ferro: la banda che tiene il passaggio sul fiume ---

    val ironLancer = creature(
        id = "creatura-lanciere-di-ferro",
        name = say("Lanciere di ferro", "Iron Lancer"),
        size = CreatureSize.MEDIUM,
        type = say("Umanoide", "Humanoid"),
        alignment = say("Legale Neutrale", "Lawful Neutral"),
        challengeRating = "1",
        proficiencyBonus = 2,
        armorClass = 16,
        hitDiceCount = 4,
        scores = scores(15, 12, 16, 10, 12, 9),
        gear = say("Mezza armatura, scudo, picca", "Half plate, shield, pike"),
        languages = say("Comune", "Common"),
        habitat = say("Fiumi, strade", "Rivers, roads"),
        treasureTheme = say("Individuale", "Individual"),
        traits = listOf(
            StatBlockEntry(
                say("Muro di picche", "Pike Wall"),
                say(
                    "Se un alleato entro ${reach(5)} impugna una picca, entrambi hanno +1 alla CA. " +
                        "Il bonus è già escluso dal valore riportato.",
                    "If an ally within ${reach(5)} wields a pike, both gain +1 AC. The bonus is " +
                        "already left out of the value shown.",
                ),
            ),
        ),
        actions = listOf(
            strike(
                say("Picca", "Pike"),
                say("Attacco in mischia. Portata ${reach(10)}.", "Melee attack. Reach ${reach(10)}."),
                attackBonus = 4,
                diceCount = 1,
                diceSides = 10,
                damageModifier = 2,
                damageType = DamageType.PIERCING,
                rangeFeet = 10,
            ),
        ),
    )

    val marshCur = creature(
        id = "creatura-cane-di-palude",
        name = say("Cane di palude", "Marsh Cur"),
        size = CreatureSize.MEDIUM,
        type = say("Bestia", "Beast"),
        alignment = say("Impassibile", "Unaligned"),
        challengeRating = "1/2",
        proficiencyBonus = 2,
        armorClass = 12,
        hitDiceCount = 3,
        scores = scores(14, 14, 14, 3, 12, 6),
        speeds = MonsterSpeeds(walk = 40, swim = 30),
        skillProficiencies = mapOf(Skill.PERCEZIONE to Proficiency.PROFICIENT),
        habitat = say("Paludi, guadi", "Marshes, fords"),
        actions = listOf(
            strike(
                say("Morso trascinante", "Dragging Bite"),
                say(
                    "Attacco in mischia. Portata ${reach(5)}. Il bersaglio effettua un tiro " +
                        "salvezza su Forza (CD 12) o viene trascinato di ${reach(5)} verso l'acqua.",
                    "Melee attack. Reach ${reach(5)}. The target makes a DC 12 Strength saving " +
                        "throw or is dragged ${reach(5)} toward the water.",
                ),
                attackBonus = 4,
                diceCount = 2,
                diceSides = 4,
                damageModifier = 2,
                damageType = DamageType.PIERCING,
            ),
        ),
    )

    val fordCaptain = creature(
        id = "creatura-vrasca",
        name = say("Vrasca, signora del guado", "Vrasca, Lady of the Ford"),
        size = CreatureSize.MEDIUM,
        type = say("Umanoide", "Humanoid"),
        alignment = say("Neutrale Malvagio", "Neutral Evil"),
        challengeRating = "4",
        proficiencyBonus = 2,
        armorClass = 17,
        hitDiceCount = 11,
        scores = scores(18, 14, 16, 12, 13, 14),
        saveProficiencies = mapOf(
            Ability.STRENGTH to Proficiency.PROFICIENT,
            Ability.CONSTITUTION to Proficiency.PROFICIENT,
        ),
        skillProficiencies = mapOf(
            Skill.ATLETICA to Proficiency.PROFICIENT,
            Skill.INTIMIDIRE to Proficiency.PROFICIENT,
        ),
        gear = say(
            "Mezza armatura, scudo, alabarda uncinata, rete zavorrata",
            "Half plate, shield, hooked halberd, weighted net",
        ),
        languages = say("Comune", "Common"),
        habitat = say("Fiumi, guadi", "Rivers, fords"),
        treasureTheme = say("Pedaggio del guado", "Ford toll"),
        traits = listOf(
            StatBlockEntry(
                say("Piede sicuro", "Sure Footing"),
                say(
                    "Il terreno difficile creato dall'acqua bassa non le costa movimento aggiuntivo.",
                    "Difficult terrain made by shallow water costs it no extra movement.",
                ),
            ),
        ),
        actions = listOf(
            strike(
                say("Alabarda uncinata", "Hooked Halberd"),
                say("Attacco in mischia. Portata ${reach(10)}.", "Melee attack. Reach ${reach(10)}."),
                attackBonus = 6,
                diceCount = 2,
                diceSides = 10,
                damageModifier = 4,
                damageType = DamageType.SLASHING,
                rangeFeet = 10,
            ),
        ),
        bonusActions = listOf(
            strike(
                say("Pugnale zavorrato", "Weighted Dagger"),
                say("Attacco in mischia con la mano libera.", "Melee attack with the free hand."),
                attackBonus = 6,
                diceCount = 1,
                diceSides = 4,
                damageModifier = 4,
                damageType = DamageType.PIERCING,
                bonusAction = true,
            ),
        ),
        reactions = listOf(
            StatBlockEntry(
                say("Rete zavorrata", "Weighted Net"),
                say(
                    "Quando una creatura entro ${reach(10)} tenta di superarla, la lancia: tiro " +
                        "salvezza su Destrezza (CD 14) o la creatura è Immobilizzata finché non " +
                        "si libera con una prova di Forza (CD 14).",
                    "When a creature within ${reach(10)} tries to get past her, she throws it: " +
                        "DC 14 Dexterity saving throw or the creature is Restrained until it " +
                        "breaks free with a DC 14 Strength check.",
                ),
            ),
        ),
    )

    // --- La corona spezzata: cio' che è rimasto sveglio sotto le rovine ---

    val ashWarden = creature(
        id = "creatura-custode-di-cenere",
        name = say("Custode di cenere", "Ash Warden"),
        size = CreatureSize.LARGE,
        type = say("Non morto", "Undead"),
        alignment = say("Legale Malvagio", "Lawful Evil"),
        challengeRating = "9",
        proficiencyBonus = 4,
        armorClass = 17,
        hitDiceCount = 16,
        scores = scores(20, 12, 18, 8, 14, 10),
        saveProficiencies = mapOf(
            Ability.CONSTITUTION to Proficiency.PROFICIENT,
            Ability.WISDOM to Proficiency.PROFICIENT,
        ),
        skillProficiencies = mapOf(Skill.PERCEZIONE to Proficiency.PROFICIENT),
        resistances = setOf(DamageType.NECROTIC),
        vulnerabilities = setOf(DamageType.RADIANT),
        damageImmunities = setOf(DamageType.POISON),
        conditionImmunities = setOf(
            ConditionType.CHARMED,
            ConditionType.FRIGHTENED,
            ConditionType.POISONED,
        ),
        senses = say("Scurovisione ${reach(120)}", "Darkvision ${reach(120)}"),
        languages = say("Comprende il Comune ma non parla", "Understands Common but does not speak"),
        habitat = say("Rovine, sottosuolo", "Ruins, underground"),
        treasureTheme = say("Insegne di una corte caduta", "Regalia of a fallen court"),
        traits = listOf(
            StatBlockEntry(
                say("Armatura di cenere", "Ashen Armor"),
                say(
                    "Quando subisce danni radiosi, fino alla fine del suo turno successivo non " +
                        "beneficia della propria resistenza ai danni necrotici.",
                    "When it takes radiant damage, it loses its resistance to necrotic damage " +
                        "until the end of its next turn.",
                ),
            ),
        ),
        actions = listOf(
            strike(
                say("Alabarda cinerea", "Ashen Halberd"),
                say(
                    "Attacco in mischia. Portata ${reach(10)}. Due colpi dell'alabarda, " +
                        "riportati come un tiro solo.",
                    "Melee attack. Reach ${reach(10)}. Two halberd blows, reported as a single roll.",
                ),
                attackBonus = 9,
                diceCount = 3,
                diceSides = 10,
                damageModifier = 5,
                damageType = DamageType.SLASHING,
                rangeFeet = 10,
            ),
        ),
        bonusActions = listOf(
            strike(
                say("Colpo di rincalzo", "Butt Strike"),
                say(
                    "Rovescia il calcio dell'arma su una creatura entro ${reach(5)}.",
                    "Swings the weapon's butt at a creature within ${reach(5)}.",
                ),
                attackBonus = 9,
                diceCount = 2,
                diceSides = 6,
                damageModifier = 5,
                damageType = DamageType.BLUDGEONING,
                bonusAction = true,
            ),
        ),
        reactions = listOf(
            StatBlockEntry(
                say("Cenere che soffoca", "Choking Ash"),
                say(
                    "Quando una creatura entro ${reach(10)} lo colpisce, questa effettua un tiro " +
                        "salvezza su Costituzione (CD 16) o è Avvelenata fino alla fine del " +
                        "proprio turno successivo.",
                    "When a creature within ${reach(10)} hits it, that creature makes a DC 16 " +
                        "Constitution saving throw or is Poisoned until the end of its next turn.",
                ),
            ),
        ),
    )

    val brokenCrown = creature(
        id = "creatura-vharok",
        name = say("Vharok, la Corona Spezzata", "Vharok, the Broken Crown"),
        size = CreatureSize.LARGE,
        type = say("Non morto", "Undead"),
        alignment = say("Neutrale Malvagio", "Neutral Evil"),
        challengeRating = "20",
        proficiencyBonus = 6,
        armorClass = 19,
        hitDiceCount = 29,
        scores = scores(26, 18, 22, 18, 20, 22),
        speeds = MonsterSpeeds(walk = 40, fly = 30, hover = true),
        saveProficiencies = mapOf(
            Ability.DEXTERITY to Proficiency.PROFICIENT,
            Ability.CONSTITUTION to Proficiency.PROFICIENT,
            Ability.WISDOM to Proficiency.PROFICIENT,
            Ability.CHARISMA to Proficiency.PROFICIENT,
        ),
        skillProficiencies = mapOf(
            Skill.PERCEZIONE to Proficiency.PROFICIENT,
            Skill.INTIMIDIRE to Proficiency.PROFICIENT,
        ),
        resistances = setOf(DamageType.COLD),
        vulnerabilities = setOf(DamageType.RADIANT),
        damageImmunities = setOf(DamageType.NECROTIC, DamageType.POISON),
        conditionImmunities = setOf(
            ConditionType.CHARMED,
            ConditionType.EXHAUSTION,
            ConditionType.FRIGHTENED,
            ConditionType.PARALYZED,
            ConditionType.POISONED,
        ),
        senses = say("Scurovisione ${reach(120)}", "Darkvision ${reach(120)}"),
        languages = say("Comune, Infernale", "Common, Infernal"),
        gear = say(
            "Falcione di cenere, diadema spezzato in tre parti",
            "Ashen glaive, a diadem broken into three pieces",
        ),
        habitat = say("Rovine, sottosuolo", "Ruins, underground"),
        treasureTheme = say("Tesoro di una corte sepolta", "Hoard of a buried court"),
        lairXp = 33_000,
        traits = listOf(
            StatBlockEntry(
                say("Corona spezzata", "Broken Crown"),
                say(
                    "Finché almeno una delle tre parti del diadema resta intatta, alla fine di " +
                        "ogni suo turno recupera 15 punti ferita. Ogni parte ha CA 19, 30 punti " +
                        "ferita e immunità ai veleni.",
                    "While at least one of the diadem's three pieces is intact, it regains 15 hit " +
                        "points at the end of each of its turns. Each piece has AC 19, 30 hit " +
                        "points and immunity to poison.",
                ),
            ),
            StatBlockEntry(
                say("Presenza di cenere", "Ashen Presence"),
                say(
                    "Alla fine di ogni suo turno, ogni creatura entro ${reach(20)} subisce " +
                        "7 (2d6) danni necrotici, o la metà con un tiro salvezza su Costituzione " +
                        "(CD 20) superato.",
                    "At the end of each of its turns, every creature within ${reach(20)} takes " +
                        "7 (2d6) necrotic damage, or half as much on a successful DC 20 " +
                        "Constitution saving throw.",
                ),
            ),
        ),
        actions = listOf(
            strike(
                say("Falcione di cenere", "Ashen Glaive"),
                say(
                    "Attacco in mischia. Portata ${reach(10)}. Sono tre fendenti, riportati come " +
                        "un tiro solo. Su un colpo, il bersaglio non può recuperare punti ferita " +
                        "fino alla fine del turno successivo di Vharok.",
                    "Melee attack. Reach ${reach(10)}. Three sweeps, reported as a single roll. " +
                        "On a hit, the target cannot regain hit points until the end of Vharok's " +
                        "next turn.",
                ),
                attackBonus = 14,
                diceCount = 4,
                diceSides = 10,
                damageModifier = 8,
                damageType = DamageType.SLASHING,
                rangeFeet = 10,
            ),
            StatBlockEntry(
                say("Ordine della corona", "Command of the Crown"),
                say(
                    "Ogni creatura a scelta entro ${reach(60)} effettua un tiro salvezza su " +
                        "Saggezza (CD 20): fallendo subisce 27 (6d8) danni psichici ed è " +
                        "Spaventata fino alla fine del proprio turno successivo.",
                    "Each creature it chooses within ${reach(60)} makes a DC 20 Wisdom saving " +
                        "throw: on a failure it takes 27 (6d8) psychic damage and is Frightened " +
                        "until the end of its next turn.",
                ),
            ),
        ),
        bonusActions = listOf(
            StatBlockEntry(
                say("Passo di cenere", "Ash Step"),
                say(
                    "Si teletrasporta fino a ${reach(60)} in uno spazio libero che può vedere, " +
                        "lasciando una nube di cenere nello spazio di partenza.",
                    "Teleports up to ${reach(60)} to an empty space it can see, leaving a cloud " +
                        "of ash behind in the space it left.",
                ),
            ),
        ),
        legendaryActions = listOf(
            strike(
                say("Fendente tardivo", "Late Sweep"),
                say(
                    "Compie un attacco con il Falcione di cenere.",
                    "Makes one attack with the Ashen Glaive.",
                ),
                attackBonus = 14,
                diceCount = 2,
                diceSides = 10,
                damageModifier = 8,
                damageType = DamageType.SLASHING,
                rangeFeet = 10,
            ),
            StatBlockEntry(
                say("Ondata di cenere", "Ash Surge"),
                say(
                    "Ogni creatura entro ${reach(10)} effettua un tiro salvezza su Forza (CD 20) " +
                        "o viene spinta di ${reach(10)} e cade Prona.",
                    "Each creature within ${reach(10)} makes a DC 20 Strength saving throw or is " +
                        "pushed ${reach(10)} away and falls Prone.",
                ),
            ),
        ),
    )

    /** Tutte le creature incluse, nell'ordine in cui compaiono nei template. */
    val all: List<MonsterStatBlock> = listOf(
        raider,
        ashHound,
        raiderChief,
        ironLancer,
        marshCur,
        fordCaptain,
        ashWarden,
        brokenCrown,
    )

    companion object {
        private val byLanguage = mutableMapOf<AppLanguage, TemplateBestiary>()

        /** Il bestiario in una lingua. Costruito al primo uso e poi riusato. */
        fun of(language: AppLanguage): TemplateBestiary = synchronized(byLanguage) {
            byLanguage.getOrPut(language) { TemplateBestiary(language) }
        }
    }
}
