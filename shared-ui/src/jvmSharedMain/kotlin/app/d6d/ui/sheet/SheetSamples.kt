package app.d6d.ui.sheet

import app.d6d.domain.combat.DamageType
import app.d6d.sheet.Ability
import app.d6d.sheet.ArmorClassAdjustment
import app.d6d.sheet.ArmorClassMethod
import app.d6d.sheet.ArmorTraining
import app.d6d.sheet.CharacterSheet
import app.d6d.sheet.CreatureSize
import app.d6d.sheet.MonsterSpeeds
import app.d6d.sheet.MonsterStatBlock
import app.d6d.sheet.Proficiency
import app.d6d.sheet.Skill
import app.d6d.sheet.SpellEntry
import app.d6d.sheet.SpellSlot
import app.d6d.sheet.Spellcasting
import app.d6d.sheet.StatBlockEntry
import app.d6d.sheet.WeaponEntry

/**
 * Schede di esempio, interamente originali.
 *
 * Servono a mostrare la forma compilata di una scheda. Non contengono nomi,
 * testi o statistiche tratti dai manuali commerciali.
 */
object SheetSamples {

    fun character() = CharacterSheet(
        id = "pg-kaelen",
        characterName = "Kaelen del Vallo",
        background = "Sentinella di frontiera",
        className = "Guerriero",
        subclass = "Campione",
        species = "Umano",
        level = 3,
        experiencePoints = 900,
        armorClass = 19,
        armorClassMethod = ArmorClassMethod.CHAIN_MAIL,
        armorClassAdjustments = listOf(
            ArmorClassAdjustment(
                source = "Stile di combattimento: Difesa",
                value = 1,
                id = "stile-difesa",
            ),
        ),
        shieldEquipped = true,
        currentHitPoints = 28,
        maxHitPoints = 34,
        temporaryHitPoints = 0,
        hitDiceMax = 3,
        hitDiceSpent = 1,
        hitDieSides = 10,
        abilityScores = mapOf(
            Ability.STRENGTH to 17,
            Ability.DEXTERITY to 12,
            Ability.CONSTITUTION to 16,
            Ability.INTELLIGENCE to 10,
            Ability.WISDOM to 13,
            Ability.CHARISMA to 8,
        ),
        saveProficiencies = mapOf(
            Ability.STRENGTH to Proficiency.PROFICIENT,
            Ability.CONSTITUTION to Proficiency.PROFICIENT,
        ),
        skillProficiencies = mapOf(
            Skill.ATLETICA to Proficiency.PROFICIENT,
            Skill.PERCEZIONE to Proficiency.PROFICIENT,
            Skill.SOPRAVVIVENZA to Proficiency.PROFICIENT,
        ),
        heroicInspiration = true,
        speedFeet = 30,
        size = CreatureSize.MEDIUM,
        armorTraining = ArmorTraining(light = true, medium = true, heavy = true, shields = true),
        weaponProficiencies = "Armi semplici, armi da guerra",
        toolProficiencies = "Strumenti da fabbro",
        weapons = listOf(
            WeaponEntry("Spadone", 6, 2, 6, 4, DamageType.SLASHING, 5, "Due mani"),
            WeaponEntry("Giavellotto", 5, 1, 6, 3, DamageType.PIERCING, 30, "Gittata 30/120"),
        ),
        classFeatures = "Stile di combattimento: Difesa (+1 alla CA con armatura).\n" +
            "Recuperare Energie: recupera 1d10+3 PF come Azione Bonus, una volta per riposo.\n" +
            "Azione Impetuosa: un'Azione aggiuntiva, una volta per riposo.",
        speciesTraits = "Versatile: un talento Origin aggiuntivo.\nAbile: competenza in un'abilita' a scelta.",
        feats = "Combattente Addestrato (Origin)\nRobusto (General)",
        alignment = "Legale Neutrale",
        languages = "Comune, Nanico, Elfico",
        equipment = "Cotta di maglia, scudo, zaino da avventuriero, corda di canapa (15 m), " +
            "razioni per 5 giorni, torce (10)",
        appearance = "Alto, spalle larghe, cicatrice sullo zigomo sinistro. Porta il mantello grigio del Vallo.",
        backstory = "Ha servito dodici anni sulle mura di frontiera prima che la guarnigione venisse sciolta.",
        attunements = listOf("Anello di protezione minore", "", ""),
    )

    fun monster() = MonsterStatBlock(
        id = "nem-mastino",
        name = "Mastino d'Ombra",
        size = CreatureSize.MEDIUM,
        type = "Aberrazione",
        tags = "",
        alignment = "Neutrale Malvagio",
        armorClass = 13,
        initiativeModifier = 3,
        initiativeScore = 13,
        averageHitPoints = 16,
        hitDiceCount = 3,
        hitDiceSides = 8,
        hitDiceModifier = 3,
        speeds = MonsterSpeeds(walk = 40, climb = 20),
        abilityScores = mapOf(
            Ability.STRENGTH to 12,
            Ability.DEXTERITY to 16,
            Ability.CONSTITUTION to 13,
            Ability.INTELLIGENCE to 6,
            Ability.WISDOM to 12,
            Ability.CHARISMA to 7,
        ),
        skillProficiencies = mapOf(
            Skill.PERCEZIONE to Proficiency.PROFICIENT,
            Skill.FURTIVITA to Proficiency.EXPERTISE,
        ),
        resistances = setOf(DamageType.NECROTIC),
        vulnerabilities = setOf(DamageType.RADIANT),
        conditionImmunities = setOf(app.d6d.domain.combat.ConditionType.FRIGHTENED),
        gear = "",
        senses = "Scurovisione 18 m",
        languages = "—",
        challengeRating = "1",
        baseXp = 200,
        lairXp = 250,
        proficiencyBonus = 2,
        traits = listOf(
            StatBlockEntry(
                "Furtivita' d'Ombra",
                "Mentre si trova in oscurita' leggera o totale, puo' compiere l'azione Nascondersi come Azione Bonus.",
            ),
        ),
        actions = listOf(
            StatBlockEntry(
                "Morso Gelido",
                "Attacco in mischia. Portata 1,5 m.",
                WeaponEntry("Morso Gelido", 4, 1, 6, 2, DamageType.PIERCING, 5),
            ),
        ),
        bonusActions = listOf(
            StatBlockEntry("Scatto nell'Ombra", "Si teletrasporta fino a 9 m in uno spazio in ombra che puo' vedere."),
        ),
        habitat = "Sottosuolo, rovine",
        treasureTheme = "Nessuno",
    )
}
