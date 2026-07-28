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
import app.d6d.sheet.WeaponEntry

/**
 * Creature dei template: contenuto interamente originale.
 *
 * Nessun nome, testo o statistica proviene dai manuali commerciali. Sono pero'
 * costruite con le regole del documento: dado vita legato alla taglia, PF medi
 * coerenti coi dadi, bonus di competenza suggerito dal Grado di Sfida e PE presi
 * dalla tabella. Cosi' un DM puo' confrontarle con qualsiasi altro stat block.
 */
internal object TemplateBestiary {

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
        treasureTheme: String = "Nessuno",
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
        name = "Predone di Vallecupa",
        size = CreatureSize.MEDIUM,
        type = "Umanoide",
        alignment = "Caotico Neutrale",
        challengeRating = "1/4",
        proficiencyBonus = 2,
        armorClass = 14,
        hitDiceCount = 2,
        scores = scores(12, 14, 12, 9, 10, 8),
        skillProficiencies = mapOf(Skill.FURTIVITA to Proficiency.PROFICIENT),
        gear = "Armatura di cuoio, scimitarra, tre torce",
        languages = "Comune",
        habitat = "Rovine, colline",
        treasureTheme = "Individuale",
        actions = listOf(
            strike(
                "Scimitarra",
                "Attacco in mischia. Portata 1,5 m.",
                attackBonus = 4,
                diceCount = 1,
                diceSides = 6,
                damageModifier = 2,
                damageType = DamageType.SLASHING,
            ),
        ),
        bonusActions = listOf(
            StatBlockEntry(
                "Sparizione fra le pietre",
                "Compie l'azione Nascondersi se ha almeno mezza copertura fornita dalle macerie.",
            ),
        ),
    )

    val ashHound = creature(
        id = "creatura-mastino-cinereo",
        name = "Mastino cinereo",
        size = CreatureSize.MEDIUM,
        type = "Bestia",
        alignment = "Impassibile",
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
        senses = "Scurovisione 18 m",
        habitat = "Rovine, sottosuolo",
        traits = listOf(
            StatBlockEntry(
                "Fiuto della cenere",
                "Ha vantaggio alle prove di Saggezza (Percezione) basate sull'olfatto.",
            ),
        ),
        actions = listOf(
            strike(
                "Morso",
                "Attacco in mischia. Portata 1,5 m. Se il bersaglio è una creatura Media o più piccola, " +
                    "effettua un tiro salvezza su Forza (CD 12) o cade Prono.",
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
        name = "Ossagrigia, capobanda",
        size = CreatureSize.MEDIUM,
        type = "Umanoide",
        alignment = "Caotico Malvagio",
        challengeRating = "1",
        proficiencyBonus = 2,
        armorClass = 15,
        hitDiceCount = 5,
        scores = scores(16, 12, 14, 10, 11, 13),
        saveProficiencies = mapOf(Ability.STRENGTH to Proficiency.PROFICIENT),
        skillProficiencies = mapOf(Skill.INTIMIDIRE to Proficiency.PROFICIENT),
        gear = "Corazza a scaglie, mazzafrusto, corno da guerra",
        languages = "Comune",
        habitat = "Rovine, colline",
        treasureTheme = "Tesoro del capobanda",
        actions = listOf(
            strike(
                "Mazzafrusto",
                "Attacco in mischia. Portata 1,5 m.",
                attackBonus = 5,
                diceCount = 1,
                diceSides = 8,
                damageModifier = 3,
                damageType = DamageType.BLUDGEONING,
            ),
        ),
        bonusActions = listOf(
            StatBlockEntry(
                "Ordine urlato",
                "Un alleato che può sentirlo entro 9 m compie subito la propria Reazione per attaccare, " +
                    "se ha un bersaglio a portata.",
            ),
        ),
    )

    // --- Il guado di ferro: la banda che tiene il passaggio sul fiume ---

    val ironLancer = creature(
        id = "creatura-lanciere-di-ferro",
        name = "Lanciere di ferro",
        size = CreatureSize.MEDIUM,
        type = "Umanoide",
        alignment = "Legale Neutrale",
        challengeRating = "1",
        proficiencyBonus = 2,
        armorClass = 16,
        hitDiceCount = 4,
        scores = scores(15, 12, 16, 10, 12, 9),
        gear = "Mezza armatura, scudo, picca",
        languages = "Comune",
        habitat = "Fiumi, strade",
        treasureTheme = "Individuale",
        traits = listOf(
            StatBlockEntry(
                "Muro di picche",
                "Se un alleato entro 1,5 m impugna una picca, entrambi hanno +1 alla CA. " +
                    "Il bonus è già escluso dal valore riportato.",
            ),
        ),
        actions = listOf(
            strike(
                "Picca",
                "Attacco in mischia. Portata 3 m.",
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
        name = "Cane di palude",
        size = CreatureSize.MEDIUM,
        type = "Bestia",
        alignment = "Impassibile",
        challengeRating = "1/2",
        proficiencyBonus = 2,
        armorClass = 12,
        hitDiceCount = 3,
        scores = scores(14, 14, 14, 3, 12, 6),
        speeds = MonsterSpeeds(walk = 40, swim = 30),
        skillProficiencies = mapOf(Skill.PERCEZIONE to Proficiency.PROFICIENT),
        habitat = "Paludi, guadi",
        actions = listOf(
            strike(
                "Morso trascinante",
                "Attacco in mischia. Portata 1,5 m. Il bersaglio effettua un tiro salvezza su Forza " +
                    "(CD 12) o viene trascinato di 1,5 m verso l'acqua.",
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
        name = "Vrasca, signora del guado",
        size = CreatureSize.MEDIUM,
        type = "Umanoide",
        alignment = "Neutrale Malvagio",
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
        gear = "Mezza armatura, scudo, alabarda uncinata, rete zavorrata",
        languages = "Comune",
        habitat = "Fiumi, guadi",
        treasureTheme = "Pedaggio del guado",
        traits = listOf(
            StatBlockEntry(
                "Piede sicuro",
                "Il terreno difficile creato dall'acqua bassa non le costa movimento aggiuntivo.",
            ),
        ),
        actions = listOf(
            strike(
                "Alabarda uncinata",
                "Attacco in mischia. Portata 3 m.",
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
                "Pugnale zavorrato",
                "Attacco in mischia con la mano libera.",
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
                "Rete zavorrata",
                "Quando una creatura entro 3 m tenta di superarla, la lancia: tiro salvezza su Destrezza " +
                    "(CD 14) o la creatura è Immobilizzata finché non si libera con una prova di Forza (CD 14).",
            ),
        ),
    )

    // --- La corona spezzata: cio' che è rimasto sveglio sotto le rovine ---

    val ashWarden = creature(
        id = "creatura-custode-di-cenere",
        name = "Custode di cenere",
        size = CreatureSize.LARGE,
        type = "Non morto",
        alignment = "Legale Malvagio",
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
        senses = "Scurovisione 36 m",
        languages = "Comprende il Comune ma non parla",
        habitat = "Rovine, sottosuolo",
        treasureTheme = "Insegne di una corte caduta",
        traits = listOf(
            StatBlockEntry(
                "Armatura di cenere",
                "Quando subisce danni radiosi, fino alla fine del suo turno successivo non beneficia " +
                    "della propria resistenza ai danni necrotici.",
            ),
        ),
        actions = listOf(
            strike(
                "Alabarda cinerea",
                "Attacco in mischia. Portata 3 m. Due colpi dell'alabarda, riportati come un tiro solo.",
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
                "Colpo di rincalzo",
                "Rovescia il calcio dell'arma su una creatura entro 1,5 m.",
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
                "Cenere che soffoca",
                "Quando una creatura entro 3 m lo colpisce, questa effettua un tiro salvezza su Costituzione " +
                    "(CD 16) o è Avvelenata fino alla fine del proprio turno successivo.",
            ),
        ),
    )

    val brokenCrown = creature(
        id = "creatura-vharok",
        name = "Vharok, la Corona Spezzata",
        size = CreatureSize.LARGE,
        type = "Non morto",
        alignment = "Neutrale Malvagio",
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
        senses = "Scurovisione 36 m",
        languages = "Comune, Infernale",
        gear = "Falcione di cenere, diadema spezzato in tre parti",
        habitat = "Rovine, sottosuolo",
        treasureTheme = "Tesoro di una corte sepolta",
        lairXp = 33_000,
        traits = listOf(
            StatBlockEntry(
                "Corona spezzata",
                "Finché almeno una delle tre parti del diadema resta intatta, alla fine di ogni suo turno " +
                    "recupera 15 punti ferita. Ogni parte ha CA 19, 30 punti ferita e immunità ai veleni.",
            ),
            StatBlockEntry(
                "Presenza di cenere",
                "Alla fine di ogni suo turno, ogni creatura entro 6 m subisce 7 (2d6) danni necrotici, " +
                    "o la metà con un tiro salvezza su Costituzione (CD 20) superato.",
            ),
        ),
        actions = listOf(
            strike(
                "Falcione di cenere",
                "Attacco in mischia. Portata 3 m. Sono tre fendenti, riportati come un tiro solo. Su un " +
                    "colpo, il bersaglio non può recuperare punti ferita fino alla fine del turno " +
                    "successivo di Vharok.",
                attackBonus = 14,
                diceCount = 4,
                diceSides = 10,
                damageModifier = 8,
                damageType = DamageType.SLASHING,
                rangeFeet = 10,
            ),
            StatBlockEntry(
                "Ordine della corona",
                "Ogni creatura a scelta entro 18 m effettua un tiro salvezza su Saggezza (CD 20): fallendo " +
                    "subisce 27 (6d8) danni psichici ed è Spaventata fino alla fine del proprio turno successivo.",
            ),
        ),
        bonusActions = listOf(
            StatBlockEntry(
                "Passo di cenere",
                "Si teletrasporta fino a 18 m in uno spazio libero che può vedere, lasciando una nube di " +
                    "cenere nello spazio di partenza.",
            ),
        ),
        legendaryActions = listOf(
            strike(
                "Fendente tardivo",
                "Compie un attacco con il Falcione di cenere.",
                attackBonus = 14,
                diceCount = 2,
                diceSides = 10,
                damageModifier = 8,
                damageType = DamageType.SLASHING,
                rangeFeet = 10,
            ),
            StatBlockEntry(
                "Ondata di cenere",
                "Ogni creatura entro 3 m effettua un tiro salvezza su Forza (CD 20) o viene spinta di 3 m " +
                    "e cade Prona.",
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
}
