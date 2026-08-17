package app.d6d.content.srd521it

import app.d6d.i18n.AppLanguage
import app.d6d.rules.character.Ability
import app.d6d.rules.character.ArmorTrainingGrant
import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.ChoiceDefinition
import app.d6d.rules.character.ChoiceKind
import app.d6d.rules.character.ClassDefinition
import app.d6d.rules.character.ClassLevelDefinition
import app.d6d.rules.character.EffectCondition
import app.d6d.rules.character.EffectTarget
import app.d6d.rules.character.RecoveryPeriod
import app.d6d.rules.character.RuleEffect
import app.d6d.rules.character.ResourceDefinition
import app.d6d.rules.character.ResourceFormula
import app.d6d.rules.character.ResourceMaximum
import app.d6d.rules.character.Skill
import app.d6d.rules.character.SpellGrant
import app.d6d.rules.character.SpellcastingKind
import app.d6d.rules.character.WeaponCategory
import app.d6d.rules.character.WeaponProperty
import app.d6d.rules.character.WeaponTrainingGrant

private const val PREFIX = "srd521-it"

/** Forma selezionabile dei quattro addestramenti nelle armi usati dallo SRD. */
private val SIMPLE_WEAPONS = WeaponTrainingGrant(setOf(WeaponCategory.SIMPLE))

private val ALL_WEAPONS =
    WeaponTrainingGrant(setOf(WeaponCategory.SIMPLE, WeaponCategory.MARTIAL))

/** Ladro: armi semplici più le armi da guerra accurate o leggere. */
private val ROGUE_WEAPONS = WeaponTrainingGrant(
    categories = setOf(WeaponCategory.SIMPLE, WeaponCategory.MARTIAL),
    martialPropertyFilter = setOf(WeaponProperty.FINESSE, WeaponProperty.LIGHT),
)

/** Monaco: armi semplici più le armi da guerra leggere. */
private val MONK_WEAPONS = WeaponTrainingGrant(
    categories = setOf(WeaponCategory.SIMPLE, WeaponCategory.MARTIAL),
    martialPropertyFilter = setOf(WeaponProperty.LIGHT),
)

/**
 * Armi con cui il personaggio parte. Lo SRD offre pacchetti già composti oppure
 * la stessa cifra in monete: qui la scelta è diretta fra le armi che la classe
 * sa impugnare, così le voci finiscono subito fra le capacità da combattimento.
 */
private const val STARTING_WEAPON_COUNT = 2

private fun startingWeaponChoice(
    classSlug: String,
    grant: WeaponTrainingGrant,
): ChoiceDefinition = ChoiceDefinition(
    id = choiceId(classSlug, 1, "armi-iniziali"),
    title = "Scegli $STARTING_WEAPON_COUNT armi iniziali fra quelle della classe",
    kind = ChoiceKind.STARTING_WEAPON,
    count = STARTING_WEAPON_COUNT,
    optionIds = SrdWeapons.trainedBy(grant).map { it.id },
)

/**
 * Definizioni strutturate delle dodici classi dell'SRD 5.2.1.
 *
 * Alcuni pool dipendono dal catalogo (talenti, incantesimi, armi, bestie e
 * suppliche). I loro ID stabili sono esposti da [dynamicPoolIds], così UI e
 * resolver possono popolarli filtrando il compendio senza duplicare le regole.
 */
object SrdClasses {
    val dynamicPoolIds: Set<String> = setOf(
        "$PREFIX:pool:feats:general",
        "$PREFIX:pool:feats:epic-or-other",
        "$PREFIX:pool:tools:musical-instruments",
        "$PREFIX:pool:tools:artisan-or-musical",
        "$PREFIX:pool:tools:any",
        "$PREFIX:pool:languages:standard",
        "$PREFIX:pool:beasts:druido:wild-shape",
        "$PREFIX:pool:eldritch-invocations:warlock",
        "$PREFIX:pool:spells:bardo:magical-discoveries",
        "$PREFIX:pool:spells:bardo:magical-secrets",
        "$PREFIX:pool:spells:mago:evocation",
        "$PREFIX:pool:spells:warlock:6",
        "$PREFIX:pool:spells:warlock:7",
        "$PREFIX:pool:spells:warlock:8",
        "$PREFIX:pool:spells:warlock:9",
    ) + CharacterClassId.entries.flatMap { classId ->
        listOf(
            "$PREFIX:pool:skills:${classId.contentId}:proficient",
            "$PREFIX:pool:weapons:mastery:${classId.contentId}",
            "$PREFIX:pool:spells:${classId.contentId}:cantrip",
            "$PREFIX:pool:spells:${classId.contentId}:available",
        )
    }

    private val italian: List<ClassDefinition> = listOf(
        barbarian(),
        bard(),
        cleric(),
        druid(),
        fighter(),
        rogue(),
        wizard(),
        monk(),
        paladin(),
        ranger(),
        sorcerer(),
        warlock(),
    )

    private val english: List<ClassDefinition> by lazy {
        italian.map { it.translatedTo(AppLanguage.ENGLISH) }
    }

    /** Le dodici classi nella lingua richiesta; la struttura e' la stessa. */
    fun all(language: AppLanguage = AppLanguage.ITALIAN): List<ClassDefinition> = when (language) {
        AppLanguage.ITALIAN -> italian
        AppLanguage.ENGLISH -> english
    }
}

val srdClasses: List<ClassDefinition>
    get() = SrdClasses.all(AppLanguage.ITALIAN)

private fun barbarian(): ClassDefinition {
    val classSlug = "barbaro"
    val rage = resourceId(classSlug, "ira")
    val subclass = subclassId("cammino-del-berserker")
    val rageMaximums = ints(2, 2, 3, 3, 3, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 6, 6, 6, 6)

    return ClassDefinition(
        id = CharacterClassId.BARBARIAN,
        name = "Barbaro",
        primaryAbilities = setOf(Ability.STRENGTH),
        hitDieSides = 12,
        fixedHitPointsPerLevel = 7,
        savingThrowProficiencies = setOf(Ability.STRENGTH, Ability.CONSTITUTION),
        skillChoice = skillChoice(
            classSlug = classSlug,
            count = 2,
            skills = skills(
                Skill.ADDESTRARE_ANIMALI,
                Skill.ATLETICA,
                Skill.INTIMIDIRE,
                Skill.NATURA,
                Skill.PERCEZIONE,
                Skill.SOPRAVVIVENZA,
            ),
        ),
        weaponTraining = "Armi semplici e da guerra",
        weaponTrainingGrant = ALL_WEAPONS,
        startingWeaponChoice = startingWeaponChoice(classSlug, ALL_WEAPONS),
        armorTraining = ArmorTrainingGrant(light = true, medium = true, shields = true),
        startingEquipment = "A scelta tra A e B: (A) un'ascia bipenne, 4 asce, una dotazione da " +
            "esploratore e 15 mo; oppure (B) 75 mo.",
        multiclassWeaponTraining = "Armi da guerra",
        multiclassArmorTraining = ArmorTrainingGrant(shields = true),
        subclassIds = listOf(subclass),
        levels = classLevels(
            classSlug = classSlug,
            featureRows = rows(
                "difesa-senza-armatura|ira|padronanza-d-armi",
                "attacco-irruento|percezione-del-pericolo",
                "conoscenza-primordiale|sottoclasse|berserker-frenesia",
                "aumento-dei-punteggi-di-caratteristica",
                "attacco-extra|movimento-veloce",
                "berserker-ira-incontenibile",
                "balzo-istintivo|istinto-ferino",
                "aumento-dei-punteggi-di-caratteristica",
                "colpo-brutale",
                "berserker-ritorsione",
                "ira-implacabile",
                "aumento-dei-punteggi-di-caratteristica",
                "colpo-brutale-migliorato",
                "berserker-presenza-intimidatoria",
                "ira-persistente",
                "aumento-dei-punteggi-di-caratteristica",
                "colpo-brutale-migliorato-livello-17",
                "potenza-indomabile",
                "dono-epico",
                "campione-primordiale",
            ),
            choices = advancementChoices(
                classSlug = classSlug,
                abilityScoreLevels = setOf(4, 8, 12, 16),
                extra = listOf(
                    1 to weaponMasteryChoice(classSlug, level = 1, count = 2),
                    3 to choice(
                        classSlug,
                        level = 3,
                        slug = "conoscenza-primordiale",
                        title = "Conoscenza primordiale: scegli un'altra abilità da barbaro",
                        kind = ChoiceKind.SKILL_PROFICIENCY,
                        count = 1,
                        optionIds = skills(
                            Skill.ADDESTRARE_ANIMALI,
                            Skill.ATLETICA,
                            Skill.INTIMIDIRE,
                            Skill.NATURA,
                            Skill.PERCEZIONE,
                            Skill.SOPRAVVIVENZA,
                        ).map(::skillId),
                    ),
                    3 to subclassChoice(classSlug, 3, subclass),
                    4 to weaponMasteryChoice(classSlug, level = 4, count = 1),
                    10 to weaponMasteryChoice(classSlug, level = 10, count = 1),
                ),
            ),
            resourcesAtLevel = { level ->
                listOf(ResourceMaximum(rage, rageMaximums[level - 1]))
            },
            effectsAtLevel = { level ->
                if (level < 5) {
                    emptyList()
                } else {
                    listOf(
                        RuleEffect(
                            target = EffectTarget.SPEED_FEET,
                            amount = 10,
                            condition = EffectCondition.NOT_WEARING_HEAVY_ARMOR,
                            source = "Movimento veloce",
                            group = "$PREFIX:effect:barbaro:movimento-veloce",
                        ),
                    )
                }
            },
        ),
        resources = listOf(
            ResourceDefinition(
                id = rage,
                name = "Ira",
                recovery = RecoveryPeriod.SHORT_OR_LONG_REST,
                shortRestRecovery = 1,
                description = "Recupera un utilizzo con un riposo breve e tutti con un riposo lungo.",
            ),
            ResourceDefinition(
                id = resourceId(classSlug, "ira-persistente"),
                name = "Ira persistente: ripristino",
                recovery = RecoveryPeriod.LONG_REST,
                formula = ResourceFormula.FIXED,
                availableFromClassLevel = 15,
                description = "Una volta per riposo lungo, ripristina tutti gli utilizzi di Ira.",
            ),
            ResourceDefinition(
                id = resourceId(classSlug, "presenza-intimidatoria"),
                name = "Presenza intimidatoria",
                recovery = RecoveryPeriod.LONG_REST,
                formula = ResourceFormula.FIXED,
                availableFromClassLevel = 14,
                requiredOptionId = subclass,
                description = "Dopo l'uso gratuito può essere riutilizzata spendendo un'Ira.",
            ),
        ),
    )
}

private fun bard(): ClassDefinition {
    val classSlug = "bardo"
    val inspiration = resourceId(classSlug, "ispirazione-bardica")
    val subclass = subclassId("collegio-della-sapienza")
    val inspirationDice = ints(
        6, 6, 6, 6,
        8, 8, 8, 8, 8,
        10, 10, 10, 10, 10,
        12, 12, 12, 12, 12, 12,
    )

    return ClassDefinition(
        id = CharacterClassId.BARD,
        name = "Bardo",
        primaryAbilities = setOf(Ability.CHARISMA),
        hitDieSides = 8,
        fixedHitPointsPerLevel = 5,
        savingThrowProficiencies = setOf(Ability.DEXTERITY, Ability.CHARISMA),
        skillChoice = skillChoice(classSlug, count = 3, skills = Skill.entries),
        weaponTraining = "Armi semplici",
        weaponTrainingGrant = SIMPLE_WEAPONS,
        startingWeaponChoice = startingWeaponChoice(classSlug, SIMPLE_WEAPONS),
        armorTraining = ArmorTrainingGrant(light = true),
        toolChoice = poolChoice(
            id = "$PREFIX:choice:$classSlug:initial:musical-instruments",
            title = "Scegli tre strumenti musicali",
            kind = ChoiceKind.TOOL_PROFICIENCY,
            count = 3,
            poolId = "$PREFIX:pool:tools:musical-instruments",
        ),
        multiclassSkillChoice = skillChoice(
            classSlug = classSlug,
            count = 1,
            skills = Skill.entries,
            phase = "multiclass",
        ),
        multiclassToolChoice = poolChoice(
            id = "$PREFIX:choice:$classSlug:multiclass:musical-instrument",
            title = "Scegli uno strumento musicale",
            kind = ChoiceKind.TOOL_PROFICIENCY,
            count = 1,
            poolId = "$PREFIX:pool:tools:musical-instruments",
        ),
        startingEquipment = "A scelta tra A e B: (A) un'armatura di cuoio, 2 pugnali, uno " +
            "strumento musicale a scelta, una dotazione da intrattenitore e 19 mo; oppure (B) 90 mo.",
        multiclassArmorTraining = ArmorTrainingGrant(light = true),
        subclassIds = listOf(subclass),
        spellcastingAbility = Ability.CHARISMA,
        spellcastingKind = SpellcastingKind.STANDARD,
        levels = classLevels(
            classSlug = classSlug,
            featureRows = rows(
                "incantesimi|ispirazione-bardica",
                "maestria|factotum",
                "sottoclasse|sapienza-competenze-bonus|sapienza-parole-taglienti",
                "aumento-dei-punteggi-di-caratteristica",
                "fonte-di-ispirazione",
                "sapienza-scoperte-magiche",
                "controfascino",
                "aumento-dei-punteggi-di-caratteristica",
                "maestria",
                "segreti-magici",
                "",
                "aumento-dei-punteggi-di-caratteristica",
                "",
                "sapienza-abilita-impareggiabile",
                "",
                "aumento-dei-punteggi-di-caratteristica",
                "",
                "ispirazione-superiore",
                "dono-epico",
                "parole-della-creazione",
            ),
            choices = advancementChoices(
                classSlug = classSlug,
                abilityScoreLevels = setOf(4, 8, 12, 16),
                extra = listOf(
                    2 to expertiseChoice(classSlug, level = 2, count = 2),
                    3 to subclassChoice(classSlug, 3, subclass),
                    6 to poolChoice(
                        id = choiceId(classSlug, 6, "scoperte-magiche"),
                        title = "Scoperte magiche: scegli due incantesimi",
                        kind = ChoiceKind.MAGICAL_DISCOVERY,
                        count = 2,
                        poolId = "$PREFIX:pool:spells:bardo:magical-discoveries",
                        description = "Incantesimi da chierico, druido o mago di un livello lanciabile.",
                    ),
                    9 to expertiseChoice(classSlug, level = 9, count = 2),
                ),
            ),
            cantrips = ints(2, 2, 2, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4),
            prepared = fullPrepared,
            preparedSpellPoolAtLevel = { level ->
                if (level >= 10) {
                    "$PREFIX:pool:spells:bardo:magical-secrets"
                } else {
                    null
                }
            },
            slots = fullCasterSlots,
            spellGrantsAtLevel = { level ->
                if (level == 20) {
                    listOf(spellGrant("Parola del potere guarire", "Parola del potere uccidere"))
                } else {
                    emptyList()
                }
            },
            resourcesAtLevel = { level ->
                listOf(ResourceMaximum(inspiration, maximum = 0, dieSides = inspirationDice[level - 1]))
            },
        ),
        resources = listOf(
            ResourceDefinition(
                id = inspiration,
                name = "Ispirazione bardica",
                recovery = RecoveryPeriod.LONG_REST,
                formula = ResourceFormula.ABILITY_MODIFIER,
                ability = Ability.CHARISMA,
                minimum = 1,
                fullShortRestRecoveryFromLevel = 5,
                description = "Utilizzi pari al modificatore di Carisma (minimo 1). Dal 5º livello " +
                    "si recuperano con un riposo breve o lungo; prima del 5º, solo con un riposo lungo.",
            ),
        ),
    )
}

private fun cleric(): ClassDefinition {
    val classSlug = "chierico"
    val channelDivinity = resourceId(classSlug, "incanalare-divinita")
    val subclass = subclassId("dominio-della-vita")
    val channelMaximums = ints(0, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4)

    return ClassDefinition(
        id = CharacterClassId.CLERIC,
        name = "Chierico",
        primaryAbilities = setOf(Ability.WISDOM),
        hitDieSides = 8,
        fixedHitPointsPerLevel = 5,
        savingThrowProficiencies = setOf(Ability.WISDOM, Ability.CHARISMA),
        skillChoice = skillChoice(
            classSlug,
            count = 2,
            skills = skills(Skill.INTUIZIONE, Skill.MEDICINA, Skill.PERSUASIONE, Skill.RELIGIONE, Skill.STORIA),
        ),
        weaponTraining = "Armi semplici",
        weaponTrainingGrant = SIMPLE_WEAPONS,
        startingWeaponChoice = startingWeaponChoice(classSlug, SIMPLE_WEAPONS),
        armorTraining = ArmorTrainingGrant(light = true, medium = true, shields = true),
        startingEquipment = "A scelta tra A e B: (A) un giaco di maglia, uno scudo, una mazza, un " +
            "simbolo sacro, una dotazione da sacerdote e 7 mo; oppure (B) 110 mo.",
        multiclassArmorTraining = ArmorTrainingGrant(light = true, medium = true, shields = true),
        subclassIds = listOf(subclass),
        spellcastingAbility = Ability.WISDOM,
        spellcastingKind = SpellcastingKind.STANDARD,
        levels = classLevels(
            classSlug = classSlug,
            featureRows = rows(
                "incantesimi|ordine-divino",
                "incanalare-divinita",
                "sottoclasse|vita-discepolo-della-vita|vita-incantesimi-del-dominio|vita-preservare-vita",
                "aumento-dei-punteggi-di-caratteristica",
                "bruciare-i-non-morti",
                "vita-guaritore-benedetto",
                "colpi-benedetti",
                "aumento-dei-punteggi-di-caratteristica",
                "",
                "intervento-divino",
                "",
                "aumento-dei-punteggi-di-caratteristica",
                "",
                "colpi-benedetti-migliorati",
                "",
                "aumento-dei-punteggi-di-caratteristica",
                "vita-guarigione-suprema",
                "",
                "dono-epico",
                "intervento-divino-superiore",
            ),
            choices = advancementChoices(
                classSlug = classSlug,
                abilityScoreLevels = setOf(4, 8, 12, 16),
                extra = listOf(
                    1 to choice(
                        classSlug,
                        level = 1,
                        slug = "ordine-divino",
                        title = "Scegli un Ordine divino",
                        kind = ChoiceKind.CLASS_OPTION,
                        count = 1,
                        optionIds = featureOptions(classSlug, "ordine-protettore", "ordine-taumaturgo"),
                    ),
                    3 to subclassChoice(classSlug, 3, subclass),
                    7 to choice(
                        classSlug,
                        level = 7,
                        slug = "colpi-benedetti",
                        title = "Scegli un'opzione di Colpi benedetti",
                        kind = ChoiceKind.CLASS_OPTION,
                        count = 1,
                        optionIds = featureOptions(classSlug, "colpo-divino", "incantesimi-potenti"),
                    ),
                ),
            ),
            cantrips = ints(3, 3, 3, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5),
            prepared = fullPrepared,
            slots = fullCasterSlots,
            spellGrantsAtLevel = { level ->
                when (level) {
                    3 -> listOf(
                        spellGrant(
                            "Aiuto",
                            "Benedizione",
                            "Cura ferite",
                            "Ristorare inferiore",
                            requiredOptionId = subclass,
                        ),
                    )
                    5 -> listOf(
                        spellGrant(
                            "Parola guaritrice di massa",
                            "Rinascita",
                            requiredOptionId = subclass,
                        ),
                    )
                    7 -> listOf(
                        spellGrant(
                            "Aura di vita",
                            "Interdizione alla morte",
                            requiredOptionId = subclass,
                        ),
                    )
                    9 -> listOf(
                        spellGrant(
                            "Ristorare superiore",
                            "Cura ferite di massa",
                            requiredOptionId = subclass,
                        ),
                    )
                    else -> emptyList()
                }
            },
            resourcesAtLevel = { level ->
                listOf(ResourceMaximum(channelDivinity, channelMaximums[level - 1]))
            },
        ),
        resources = listOf(
            ResourceDefinition(
                id = channelDivinity,
                name = "Incanalare divinità",
                recovery = RecoveryPeriod.SHORT_OR_LONG_REST,
                shortRestRecovery = 1,
                description = "Recupera un utilizzo con un riposo breve e tutti con un riposo lungo.",
            ),
            ResourceDefinition(
                id = resourceId(classSlug, "intervento-divino"),
                name = "Intervento divino",
                recovery = RecoveryPeriod.LONG_REST,
                formula = ResourceFormula.FIXED,
                availableFromClassLevel = 10,
                description = "Un utilizzo per riposo lungo; al 20º Desiderio può imporre " +
                    "un recupero speciale di 2d4 riposi lunghi.",
            ),
        ),
    )
}

private fun druid(): ClassDefinition {
    val classSlug = "druido"
    val wildShape = resourceId(classSlug, "forma-selvatica")
    val subclass = subclassId("circolo-della-terra")
    val wildShapeMaximums = ints(0, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4)

    return ClassDefinition(
        id = CharacterClassId.DRUID,
        name = "Druido",
        primaryAbilities = setOf(Ability.WISDOM),
        hitDieSides = 8,
        fixedHitPointsPerLevel = 5,
        savingThrowProficiencies = setOf(Ability.INTELLIGENCE, Ability.WISDOM),
        skillChoice = skillChoice(
            classSlug,
            count = 2,
            skills = skills(
                Skill.ADDESTRARE_ANIMALI,
                Skill.ARCANO,
                Skill.INTUIZIONE,
                Skill.MEDICINA,
                Skill.NATURA,
                Skill.PERCEZIONE,
                Skill.RELIGIONE,
                Skill.SOPRAVVIVENZA,
            ),
        ),
        weaponTraining = "Armi semplici",
        weaponTrainingGrant = SIMPLE_WEAPONS,
        startingWeaponChoice = startingWeaponChoice(classSlug, SIMPLE_WEAPONS),
        armorTraining = ArmorTrainingGrant(light = true, shields = true),
        toolChoice = fixedToolChoice(classSlug, "initial", "Borsa da erborista", "borsa-da-erborista"),
        startingEquipment = "A scelta tra A e B: (A) un'armatura di cuoio, uno scudo, un falcetto, " +
            "un focus druidico (bastone ferrato), una dotazione da esploratore, una borsa da " +
            "erborista e 9 mo; oppure (B) 50 mo.",
        multiclassArmorTraining = ArmorTrainingGrant(light = true, shields = true),
        subclassIds = listOf(subclass),
        spellcastingAbility = Ability.WISDOM,
        spellcastingKind = SpellcastingKind.STANDARD,
        levels = classLevels(
            classSlug = classSlug,
            featureRows = rows(
                "druidico|incantesimi|ordine-primordiale",
                "compagno-selvatico|forma-selvatica",
                "sottoclasse|terra-incantesimi-del-circolo|terra-ausilio-dalla-terra",
                "aumento-dei-punteggi-di-caratteristica",
                "rinascita-selvatica",
                "terra-recupero-naturale",
                "furia-elementale",
                "aumento-dei-punteggi-di-caratteristica",
                "",
                "terra-interdizione-della-natura",
                "",
                "aumento-dei-punteggi-di-caratteristica",
                "",
                "terra-rifugio-della-natura",
                "furia-elementale-migliorata",
                "aumento-dei-punteggi-di-caratteristica",
                "",
                "incantesimi-bestiali",
                "dono-epico",
                "arcidruido",
            ),
            choices = advancementChoices(
                classSlug = classSlug,
                abilityScoreLevels = setOf(4, 8, 12, 16),
                extra = listOf(
                    1 to choice(
                        classSlug,
                        level = 1,
                        slug = "ordine-primordiale",
                        title = "Scegli un Ordine primordiale",
                        kind = ChoiceKind.CLASS_OPTION,
                        count = 1,
                        optionIds = featureOptions(classSlug, "ordine-mago", "ordine-custode"),
                    ),
                    2 to poolChoice(
                        id = choiceId(classSlug, 2, "forme-bestiali"),
                        title = "Scegli quattro forme bestiali (GS massimo 1/4, senza volo)",
                        kind = ChoiceKind.CLASS_OPTION,
                        count = 4,
                        poolId = "$PREFIX:pool:beasts:druido:wild-shape",
                    ),
                    3 to subclassChoice(classSlug, 3, subclass),
                    3 to choice(
                        classSlug,
                        level = 3,
                        slug = "terra",
                        title = "Scegli il tipo di terra del Circolo",
                        kind = ChoiceKind.CLASS_OPTION,
                        count = 1,
                        optionIds = featureOptions(
                            classSlug,
                            "terra-arida",
                            "terra-polare",
                            "terra-temperata",
                            "terra-tropicale",
                        ),
                        description = "La scelta può essere cambiata al termine di un riposo lungo.",
                    ),
                    4 to poolChoice(
                        id = choiceId(classSlug, 4, "forme-bestiali"),
                        title = "Scegli due forme bestiali aggiuntive (GS massimo 1/2, senza volo)",
                        kind = ChoiceKind.CLASS_OPTION,
                        count = 2,
                        poolId = "$PREFIX:pool:beasts:druido:wild-shape",
                    ),
                    7 to choice(
                        classSlug,
                        level = 7,
                        slug = "furia-elementale",
                        title = "Scegli un'opzione di Furia elementale",
                        kind = ChoiceKind.CLASS_OPTION,
                        count = 1,
                        optionIds = featureOptions(classSlug, "incantesimi-potenti", "colpo-primordiale"),
                    ),
                    8 to poolChoice(
                        id = choiceId(classSlug, 8, "forme-bestiali"),
                        title = "Scegli due forme bestiali aggiuntive (GS massimo 1, volo consentito)",
                        kind = ChoiceKind.CLASS_OPTION,
                        count = 2,
                        poolId = "$PREFIX:pool:beasts:druido:wild-shape",
                    ),
                ),
            ),
            cantrips = ints(2, 2, 2, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4),
            prepared = fullPrepared,
            slots = fullCasterSlots,
            spellGrantsAtLevel = { level ->
                buildList {
                    if (level == 1) add(spellGrant("Parlare con gli animali"))
                    val landSpells = when (level) {
                        3 -> mapOf(
                            "terra-arida" to arrayOf("Dardo di fuoco", "Mani brucianti", "Sfocatura"),
                            "terra-polare" to arrayOf("Blocca persone", "Nube di nebbia", "Raggio di gelo"),
                            "terra-temperata" to arrayOf("Passo velato", "Sonno", "Stretta folgorante"),
                            "terra-tropicale" to arrayOf("Fiotto acido", "Raggio di infermità", "Ragnatela"),
                        )
                        5 -> mapOf(
                            "terra-arida" to arrayOf("Palla di fuoco"),
                            "terra-polare" to arrayOf("Tempesta di nevischio"),
                            "terra-temperata" to arrayOf("Fulmine"),
                            "terra-tropicale" to arrayOf("Nube maleodorante"),
                        )
                        7 -> mapOf(
                            "terra-arida" to arrayOf("Inaridire"),
                            "terra-polare" to arrayOf("Tempesta di ghiaccio"),
                            "terra-temperata" to arrayOf("Libertà di movimento"),
                            "terra-tropicale" to arrayOf("Metamorfosi"),
                        )
                        9 -> mapOf(
                            "terra-arida" to arrayOf("Muro di pietra"),
                            "terra-polare" to arrayOf("Cono di freddo"),
                            "terra-temperata" to arrayOf("Traslazione arborea"),
                            "terra-tropicale" to arrayOf("Piaga degli insetti"),
                        )
                        else -> emptyMap()
                    }
                    landSpells.forEach { (land, names) ->
                        add(
                            spellGrant(
                                *names,
                                requiredOptionId = featureId(classSlug, land),
                            ),
                        )
                    }
                }
            },
            resourcesAtLevel = { level ->
                listOf(ResourceMaximum(wildShape, wildShapeMaximums[level - 1]))
            },
            languageGrantsAtLevel = { level ->
                if (level == 1) listOf("Druidico") else emptyList()
            },
        ),
        resources = listOf(
            ResourceDefinition(
                id = wildShape,
                name = "Forma selvatica",
                recovery = RecoveryPeriod.SHORT_OR_LONG_REST,
                shortRestRecovery = 1,
                description = "Recupera un utilizzo con un riposo breve e tutti con un riposo lungo.",
            ),
            ResourceDefinition(
                id = resourceId(classSlug, "rinascita-selvatica"),
                name = "Rinascita selvatica: slot gratuito",
                recovery = RecoveryPeriod.LONG_REST,
                formula = ResourceFormula.FIXED,
                availableFromClassLevel = 5,
                description = "Una volta per riposo lungo, spendi Forma selvatica per ottenere " +
                    "uno slot di 1º livello.",
            ),
            ResourceDefinition(
                id = resourceId(classSlug, "recupero-naturale-slot"),
                name = "Recupero naturale: recupero slot",
                recovery = RecoveryPeriod.LONG_REST,
                formula = ResourceFormula.FIXED,
                availableFromClassLevel = 6,
                requiredOptionId = subclass,
                description = "Recupera slot per livelli totali fino a metà del livello da Druido " +
                    "arrotondata per eccesso; nessuno slot di 6º o superiore.",
            ),
            ResourceDefinition(
                id = resourceId(classSlug, "recupero-naturale-lancio"),
                name = "Recupero naturale: lancio gratuito",
                recovery = RecoveryPeriod.LONG_REST,
                formula = ResourceFormula.FIXED,
                availableFromClassLevel = 6,
                requiredOptionId = subclass,
            ),
            ResourceDefinition(
                id = resourceId(classSlug, "mago-della-natura"),
                name = "Mago della natura",
                recovery = RecoveryPeriod.LONG_REST,
                formula = ResourceFormula.FIXED,
                availableFromClassLevel = 20,
            ),
        ),
    )
}

private fun fighter(): ClassDefinition {
    val classSlug = "guerriero"
    val secondWind = resourceId(classSlug, "recuperare-energie")
    val actionSurge = resourceId(classSlug, "azione-impetuosa")
    val indomitable = resourceId(classSlug, "indomabile")
    val subclass = subclassId("campione")
    val secondWindMaximums = ints(2, 2, 2, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4)
    val actionSurgeMaximums = ints(0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2)
    val indomitableMaximums = ints(0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3)

    return ClassDefinition(
        id = CharacterClassId.FIGHTER,
        name = "Guerriero",
        primaryAbilities = setOf(Ability.STRENGTH, Ability.DEXTERITY),
        multiclassPrerequisiteGroups = listOf(setOf(Ability.STRENGTH, Ability.DEXTERITY)),
        hitDieSides = 10,
        fixedHitPointsPerLevel = 6,
        savingThrowProficiencies = setOf(Ability.STRENGTH, Ability.CONSTITUTION),
        skillChoice = skillChoice(
            classSlug,
            count = 2,
            skills = skills(
                Skill.ACROBAZIA,
                Skill.ADDESTRARE_ANIMALI,
                Skill.ATLETICA,
                Skill.INTIMIDIRE,
                Skill.INTUIZIONE,
                Skill.PERCEZIONE,
                Skill.PERSUASIONE,
                Skill.SOPRAVVIVENZA,
                Skill.STORIA,
            ),
        ),
        weaponTraining = "Armi semplici e da guerra",
        weaponTrainingGrant = ALL_WEAPONS,
        startingWeaponChoice = startingWeaponChoice(classSlug, ALL_WEAPONS),
        armorTraining = ArmorTrainingGrant(light = true, medium = true, heavy = true, shields = true),
        startingEquipment = "A scelta tra A, B e C: (A) una cotta di maglia, uno spadone, un " +
            "mazzafrusto, 8 giavellotti, una dotazione da avventuriero e 4 mo; (B) un'armatura di " +
            "cuoio borchiato, una scimitarra, una spada corta, un arco lungo, 20 frecce, una " +
            "faretra, una dotazione da avventuriero e 11 mo; oppure (C) 155 mo.",
        multiclassWeaponTraining = "Armi da guerra",
        multiclassArmorTraining = ArmorTrainingGrant(light = true, medium = true, shields = true),
        subclassIds = listOf(subclass),
        levels = classLevels(
            classSlug = classSlug,
            featureRows = rows(
                "padronanza-d-armi|recuperare-energie|stile-di-combattimento",
                "azione-impetuosa|mente-tattica",
                "sottoclasse|campione-critico-migliorato|campione-atleta-straordinario",
                "aumento-dei-punteggi-di-caratteristica",
                "attacco-extra|spostamento-tattico",
                "aumento-dei-punteggi-di-caratteristica",
                "campione-stile-di-combattimento-aggiuntivo",
                "aumento-dei-punteggi-di-caratteristica",
                "indomabile|signore-delle-tattiche",
                "campione-guerriero-eroico",
                "due-attacchi-extra",
                "aumento-dei-punteggi-di-caratteristica",
                "attacchi-studiati|indomabile",
                "aumento-dei-punteggi-di-caratteristica",
                "campione-critico-superiore",
                "aumento-dei-punteggi-di-caratteristica",
                "azione-impetuosa|indomabile",
                "campione-sopravvissuto",
                "dono-epico",
                "tre-attacchi-extra",
            ),
            choices = advancementChoices(
                classSlug = classSlug,
                abilityScoreLevels = setOf(4, 6, 8, 12, 14, 16),
                extra = listOf(
                    1 to fightingStyleChoice(classSlug, level = 1),
                    1 to weaponMasteryChoice(classSlug, level = 1, count = 3),
                    3 to subclassChoice(classSlug, 3, subclass),
                    4 to weaponMasteryChoice(classSlug, level = 4, count = 1),
                    7 to fightingStyleChoice(
                        classSlug,
                        level = 7,
                        slug = "stile-di-combattimento-aggiuntivo",
                    ),
                    10 to weaponMasteryChoice(classSlug, level = 10, count = 1),
                    16 to weaponMasteryChoice(classSlug, level = 16, count = 1),
                ),
            ),
            resourcesAtLevel = { level ->
                listOf(
                    ResourceMaximum(secondWind, secondWindMaximums[level - 1], dieSides = 10),
                    ResourceMaximum(actionSurge, actionSurgeMaximums[level - 1]),
                    ResourceMaximum(indomitable, indomitableMaximums[level - 1]),
                )
            },
        ),
        resources = listOf(
            ResourceDefinition(
                id = secondWind,
                name = "Recuperare energie",
                recovery = RecoveryPeriod.SHORT_OR_LONG_REST,
                shortRestRecovery = 1,
                description = "Recupera un utilizzo con un riposo breve e tutti con un riposo lungo.",
            ),
            ResourceDefinition(
                id = actionSurge,
                name = "Azione impetuosa",
                recovery = RecoveryPeriod.SHORT_OR_LONG_REST,
            ),
            ResourceDefinition(
                id = indomitable,
                name = "Indomabile",
                recovery = RecoveryPeriod.LONG_REST,
            ),
        ),
    )
}

private fun rogue(): ClassDefinition {
    val classSlug = "ladro"
    val strokeOfLuck = resourceId(classSlug, "colpo-di-fortuna")
    val subclass = subclassId("furfante")
    val rogueSkills = skills(
        Skill.ACROBAZIA,
        Skill.ATLETICA,
        Skill.FURTIVITA,
        Skill.INDAGARE,
        Skill.INGANNO,
        Skill.INTIMIDIRE,
        Skill.INTUIZIONE,
        Skill.PERCEZIONE,
        Skill.PERSUASIONE,
        Skill.RAPIDITA_DI_MANO,
    )

    return ClassDefinition(
        id = CharacterClassId.ROGUE,
        name = "Ladro",
        primaryAbilities = setOf(Ability.DEXTERITY),
        hitDieSides = 8,
        fixedHitPointsPerLevel = 5,
        savingThrowProficiencies = setOf(Ability.DEXTERITY, Ability.INTELLIGENCE),
        skillChoice = skillChoice(classSlug, count = 4, skills = rogueSkills),
        weaponTraining = "Armi semplici e armi da guerra con la proprietà accurata o leggera",
        weaponTrainingGrant = ROGUE_WEAPONS,
        startingWeaponChoice = startingWeaponChoice(classSlug, ROGUE_WEAPONS),
        armorTraining = ArmorTrainingGrant(light = true),
        toolChoice = fixedToolChoice(classSlug, "initial", "Arnesi da scasso", "arnesi-da-scasso"),
        multiclassSkillChoice = skillChoice(
            classSlug = classSlug,
            count = 1,
            skills = rogueSkills,
            phase = "multiclass",
        ),
        multiclassToolChoice = fixedToolChoice(
            classSlug,
            "multiclass",
            "Arnesi da scasso",
            "arnesi-da-scasso",
        ),
        startingEquipment = "A scelta tra A e B: (A) un'armatura di cuoio, 2 pugnali, una spada " +
            "corta, un arco corto, 20 frecce, una faretra, arnesi da scasso, una dotazione da " +
            "scassinatore e 8 mo; oppure (B) 100 mo.",
        multiclassArmorTraining = ArmorTrainingGrant(light = true),
        subclassIds = listOf(subclass),
        levels = classLevels(
            classSlug = classSlug,
            featureRows = rows(
                "maestria|attacco-furtivo|gergo-ladresco|padronanza-d-armi",
                "azione-scaltra",
                "sottoclasse|mira-ferma|furfante-mani-veloci|furfante-lavoro-al-secondo-piano",
                "aumento-dei-punteggi-di-caratteristica",
                "colpo-astuto|schivata-prodigiosa",
                "maestria",
                "elusione|dote-affidabile",
                "aumento-dei-punteggi-di-caratteristica",
                "furfante-furtivita-suprema",
                "aumento-dei-punteggi-di-caratteristica",
                "colpo-astuto-migliorato",
                "aumento-dei-punteggi-di-caratteristica",
                "furfante-usare-oggetto-magico",
                "colpi-infidi",
                "mente-sfuggente",
                "aumento-dei-punteggi-di-caratteristica",
                "furfante-riflessi-da-furfante",
                "inafferrabile",
                "dono-epico",
                "colpo-di-fortuna",
            ),
            choices = advancementChoices(
                classSlug = classSlug,
                abilityScoreLevels = setOf(4, 8, 10, 12, 16),
                extra = listOf(
                    1 to expertiseChoice(classSlug, level = 1, count = 2),
                    1 to weaponMasteryChoice(classSlug, level = 1, count = 2),
                    3 to subclassChoice(classSlug, 3, subclass),
                    6 to expertiseChoice(classSlug, level = 6, count = 2),
                ),
            ),
            resourcesAtLevel = { level ->
                listOf(ResourceMaximum(strokeOfLuck, if (level == 20) 1 else 0))
            },
            savingThrowGrantsAtLevel = { level ->
                if (level == 15) setOf(Ability.WISDOM, Ability.CHARISMA) else emptySet()
            },
            languageGrantsAtLevel = { level ->
                if (level == 1) listOf("Gergo ladresco") else emptyList()
            },
        ),
        resources = listOf(
            ResourceDefinition(
                id = strokeOfLuck,
                name = "Colpo di fortuna",
                recovery = RecoveryPeriod.SHORT_OR_LONG_REST,
            ),
        ),
    )
}

private fun wizard(): ClassDefinition {
    val classSlug = "mago"
    val arcaneRecovery = resourceId(classSlug, "recupero-arcano")
    val subclass = subclassId("invocatore")
    val wizardPrepared = ints(4, 5, 6, 7, 9, 10, 11, 12, 14, 15, 16, 16, 17, 18, 19, 21, 22, 23, 24, 25)

    return ClassDefinition(
        id = CharacterClassId.WIZARD,
        name = "Mago",
        primaryAbilities = setOf(Ability.INTELLIGENCE),
        hitDieSides = 6,
        fixedHitPointsPerLevel = 4,
        savingThrowProficiencies = setOf(Ability.INTELLIGENCE, Ability.WISDOM),
        skillChoice = skillChoice(
            classSlug,
            count = 2,
            skills = skills(
                Skill.ARCANO,
                Skill.INDAGARE,
                Skill.INTUIZIONE,
                Skill.MEDICINA,
                Skill.NATURA,
                Skill.RELIGIONE,
                Skill.STORIA,
            ),
        ),
        weaponTraining = "Armi semplici",
        weaponTrainingGrant = SIMPLE_WEAPONS,
        startingWeaponChoice = startingWeaponChoice(classSlug, SIMPLE_WEAPONS),
        startingEquipment = "A scelta tra A e B: (A) 2 pugnali, un focus arcano (bastone ferrato), " +
            "una veste, un libro degli incantesimi, una dotazione da studioso e 5 mo; oppure (B) 55 mo.",
        subclassIds = listOf(subclass),
        spellcastingAbility = Ability.INTELLIGENCE,
        spellcastingKind = SpellcastingKind.SPELLBOOK,
        levels = classLevels(
            classSlug = classSlug,
            featureRows = rows(
                "incantesimi|adepto-dei-rituali|recupero-arcano",
                "studioso",
                "sottoclasse|invocatore-sapiente|trucchetto-potente",
                "aumento-dei-punteggi-di-caratteristica",
                "memorizzare-incantesimi",
                "invocatore-plasmare-incantesimi",
                "",
                "aumento-dei-punteggi-di-caratteristica",
                "",
                "invocatore-invocazione-potente",
                "",
                "aumento-dei-punteggi-di-caratteristica",
                "",
                "invocatore-saturazione-magica",
                "",
                "aumento-dei-punteggi-di-caratteristica",
                "",
                "maestria-negli-incantesimi",
                "dono-epico",
                "incantesimi-personali",
            ),
            choices = advancementChoices(
                classSlug = classSlug,
                abilityScoreLevels = setOf(4, 8, 12, 16),
                extra = buildList {
                    add(
                        2 to choice(
                            classSlug,
                            level = 2,
                            slug = "studioso",
                            title = "Studioso: scegli una competenza in cui ottenere Maestria",
                            kind = ChoiceKind.EXPERTISE,
                            count = 1,
                            optionIds = skills(
                                Skill.ARCANO,
                                Skill.INDAGARE,
                                Skill.MEDICINA,
                                Skill.NATURA,
                                Skill.RELIGIONE,
                                Skill.STORIA,
                            ).map(::skillId),
                        ),
                    )
                    add(3 to subclassChoice(classSlug, 3, subclass))
                    add(3 to evocationSpellChoice(level = 3, count = 2, maximumSpellLevel = 2))
                    listOf(5, 7, 9, 11, 13, 15, 17).forEach { level ->
                        add(
                            level to evocationSpellChoice(
                                level = level,
                                count = 1,
                                maximumSpellLevel = (level + 1) / 2,
                            ),
                        )
                    }
                    add(
                        18 to poolChoice(
                            id = choiceId(classSlug, 18, "maestria-incantesimo-1"),
                            title = "Maestria negli incantesimi: scegli un incantesimo di 1º livello",
                            kind = ChoiceKind.ALWAYS_PREPARED_SPELL,
                            count = 1,
                            poolId = "$PREFIX:pool:spells:mago:1",
                        ),
                    )
                    add(
                        18 to poolChoice(
                            id = choiceId(classSlug, 18, "maestria-incantesimo-2"),
                            title = "Maestria negli incantesimi: scegli un incantesimo di 2º livello",
                            kind = ChoiceKind.ALWAYS_PREPARED_SPELL,
                            count = 1,
                            poolId = "$PREFIX:pool:spells:mago:2",
                        ),
                    )
                    add(
                        20 to poolChoice(
                            id = choiceId(classSlug, 20, "incantesimi-personali"),
                            title = "Scegli due Incantesimi personali di 3º livello",
                            kind = ChoiceKind.ALWAYS_PREPARED_SPELL,
                            count = 2,
                            poolId = "$PREFIX:pool:spells:mago:3",
                        ),
                    )
                },
            ),
            cantrips = ints(3, 3, 3, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5),
            prepared = wizardPrepared,
            spellbookAdditions = ints(6, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2),
            slots = fullCasterSlots,
            resourcesAtLevel = {
                listOf(ResourceMaximum(arcaneRecovery, maximum = 1))
            },
        ),
        resources = listOf(
            ResourceDefinition(
                id = arcaneRecovery,
                name = "Recupero arcano",
                recovery = RecoveryPeriod.LONG_REST,
            ),
            ResourceDefinition(
                id = resourceId(classSlug, "incantesimo-personale-1"),
                name = "Incantesimo personale I",
                recovery = RecoveryPeriod.SHORT_OR_LONG_REST,
                formula = ResourceFormula.FIXED,
                availableFromClassLevel = 20,
                description = "Lancio gratuito separato per il primo Incantesimo personale.",
            ),
            ResourceDefinition(
                id = resourceId(classSlug, "incantesimo-personale-2"),
                name = "Incantesimo personale II",
                recovery = RecoveryPeriod.SHORT_OR_LONG_REST,
                formula = ResourceFormula.FIXED,
                availableFromClassLevel = 20,
                description = "Lancio gratuito separato per il secondo Incantesimo personale.",
            ),
        ),
    )
}

private fun monk(): ClassDefinition {
    val classSlug = "monaco"
    val focus = resourceId(classSlug, "punti-concentrazione")
    val subclass = subclassId("guerriero-della-mano-aperta")
    val focusMaximums = ints(0, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20)

    return ClassDefinition(
        id = CharacterClassId.MONK,
        name = "Monaco",
        primaryAbilities = setOf(Ability.DEXTERITY, Ability.WISDOM),
        multiclassPrerequisiteGroups = listOf(setOf(Ability.DEXTERITY), setOf(Ability.WISDOM)),
        hitDieSides = 8,
        fixedHitPointsPerLevel = 5,
        savingThrowProficiencies = setOf(Ability.STRENGTH, Ability.DEXTERITY),
        skillChoice = skillChoice(
            classSlug,
            count = 2,
            skills = skills(
                Skill.ACROBAZIA,
                Skill.ATLETICA,
                Skill.FURTIVITA,
                Skill.INTUIZIONE,
                Skill.RELIGIONE,
                Skill.STORIA,
            ),
        ),
        weaponTraining = "Armi semplici e armi da guerra con la proprietà leggera",
        weaponTrainingGrant = MONK_WEAPONS,
        startingWeaponChoice = startingWeaponChoice(classSlug, MONK_WEAPONS),
        toolChoice = poolChoice(
            id = "$PREFIX:choice:$classSlug:initial:tool",
            title = "Scegli uno strumento da artigiano o musicale",
            kind = ChoiceKind.TOOL_PROFICIENCY,
            count = 1,
            poolId = "$PREFIX:pool:tools:artisan-or-musical",
        ),
        startingEquipment = "A scelta tra A e B: (A) una lancia, 5 pugnali, lo strumento scelto, " +
            "una dotazione da esploratore e 11 mo; oppure (B) 50 mo.",
        subclassIds = listOf(subclass),
        levels = classLevels(
            classSlug = classSlug,
            featureRows = rows(
                "arti-marziali|difesa-senza-armatura",
                "concentrazione-da-monaco|metabolismo-straordinario|movimento-senza-armatura",
                "devia-attacchi|sottoclasse|mano-aperta-tecnica-della-mano-aperta",
                "aumento-dei-punteggi-di-caratteristica|caduta-lenta",
                "attacco-extra|colpo-stordente",
                "colpi-potenziati|mano-aperta-integrita-del-corpo",
                "elusione",
                "aumento-dei-punteggi-di-caratteristica",
                "movimento-acrobatico",
                "autorigenerazione|concentrazione-superiore",
                "mano-aperta-passo-lesto",
                "aumento-dei-punteggi-di-caratteristica",
                "deviare-energia",
                "esperto-di-sopravvivenza-disciplinato",
                "concentrazione-perfetta",
                "aumento-dei-punteggi-di-caratteristica",
                "mano-aperta-palmo-tremante",
                "difesa-superiore",
                "dono-epico",
                "corpo-e-mente",
            ),
            choices = advancementChoices(
                classSlug = classSlug,
                abilityScoreLevels = setOf(4, 8, 12, 16),
                extra = listOf(3 to subclassChoice(classSlug, 3, subclass)),
            ),
            resourcesAtLevel = { level ->
                listOf(
                    ResourceMaximum(
                        resourceId = focus,
                        maximum = focusMaximums[level - 1],
                        dieSides = martialArtsDie(level),
                    ),
                )
            },
            savingThrowGrantsAtLevel = { level ->
                if (level == 14) Ability.entries.toSet() else emptySet()
            },
            // Il Movimento senza armatura cresce a scaglioni e non si somma: gli
            // scalini condividono il gruppo, quindi vale sempre il piu' alto.
            effectsAtLevel = { level ->
                unarmoredMovementBonus(level)?.let { bonus ->
                    listOf(
                        RuleEffect(
                            target = EffectTarget.SPEED_FEET,
                            amount = bonus,
                            condition = EffectCondition.UNARMORED_WITHOUT_SHIELD,
                            source = "Movimento senza armatura",
                            group = "$PREFIX:effect:monaco:movimento-senza-armatura",
                        ),
                    )
                }.orEmpty()
            },
        ),
        resources = listOf(
            ResourceDefinition(
                id = focus,
                name = "Punti concentrazione",
                recovery = RecoveryPeriod.SHORT_OR_LONG_REST,
                description = "Il dado associato è il dado di Arti marziali del livello corrente.",
            ),
            ResourceDefinition(
                id = resourceId(classSlug, "metabolismo-straordinario"),
                name = "Metabolismo straordinario",
                recovery = RecoveryPeriod.LONG_REST,
                formula = ResourceFormula.FIXED,
                availableFromClassLevel = 2,
            ),
            ResourceDefinition(
                id = resourceId(classSlug, "integrita-del-corpo"),
                name = "Integrità del corpo",
                recovery = RecoveryPeriod.LONG_REST,
                formula = ResourceFormula.ABILITY_MODIFIER,
                ability = Ability.WISDOM,
                minimum = 1,
                availableFromClassLevel = 6,
                requiredOptionId = subclass,
            ),
        ),
    )
}

private fun paladin(): ClassDefinition {
    val classSlug = "paladino"
    val layOnHands = resourceId(classSlug, "imposizione-delle-mani")
    val channelDivinity = resourceId(classSlug, "incanalare-divinita")
    val subclass = subclassId("giuramento-di-devozione")
    val channelMaximums = ints(0, 0, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3)
    val prepared = ints(2, 3, 4, 5, 6, 6, 7, 7, 9, 9, 10, 10, 11, 11, 12, 12, 14, 14, 15, 15)

    return ClassDefinition(
        id = CharacterClassId.PALADIN,
        name = "Paladino",
        primaryAbilities = setOf(Ability.STRENGTH, Ability.CHARISMA),
        multiclassPrerequisiteGroups = listOf(setOf(Ability.STRENGTH), setOf(Ability.CHARISMA)),
        hitDieSides = 10,
        fixedHitPointsPerLevel = 6,
        savingThrowProficiencies = setOf(Ability.WISDOM, Ability.CHARISMA),
        skillChoice = skillChoice(
            classSlug,
            count = 2,
            skills = skills(
                Skill.ATLETICA,
                Skill.INTIMIDIRE,
                Skill.INTUIZIONE,
                Skill.MEDICINA,
                Skill.PERSUASIONE,
                Skill.RELIGIONE,
            ),
        ),
        weaponTraining = "Armi semplici e da guerra",
        weaponTrainingGrant = ALL_WEAPONS,
        startingWeaponChoice = startingWeaponChoice(classSlug, ALL_WEAPONS),
        armorTraining = ArmorTrainingGrant(light = true, medium = true, heavy = true, shields = true),
        startingEquipment = "A scelta tra A e B: (A) una cotta di maglia, uno scudo, una spada " +
            "lunga, 6 giavellotti, un simbolo sacro, una dotazione da sacerdote e 9 mo; oppure (B) 150 mo.",
        multiclassWeaponTraining = "Armi da guerra",
        multiclassArmorTraining = ArmorTrainingGrant(light = true, medium = true, shields = true),
        subclassIds = listOf(subclass),
        spellcastingAbility = Ability.CHARISMA,
        spellcastingKind = SpellcastingKind.HALF_CASTER,
        levels = classLevels(
            classSlug = classSlug,
            featureRows = rows(
                "imposizione-delle-mani|incantesimi|padronanza-d-armi",
                "stile-di-combattimento|punizione-del-paladino",
                "incanalare-divinita|sottoclasse|devozione-incantesimi-del-giuramento|devozione-arma-consacrata",
                "aumento-dei-punteggi-di-caratteristica",
                "attacco-extra|fido-destriero",
                "aura-di-protezione",
                "devozione-aura-di-devozione",
                "aumento-dei-punteggi-di-caratteristica",
                "abiurare-nemici",
                "aura-di-coraggio",
                "colpi-radiosi",
                "aumento-dei-punteggi-di-caratteristica",
                "",
                "tocco-rigenerante",
                "devozione-punizione-protettiva",
                "aumento-dei-punteggi-di-caratteristica",
                "",
                "ampliamento-dell-aura",
                "dono-epico",
                "devozione-nube-sacra",
            ),
            choices = advancementChoices(
                classSlug = classSlug,
                abilityScoreLevels = setOf(4, 8, 12, 16),
                extra = listOf(
                    1 to weaponMasteryChoice(classSlug, level = 1, count = 2),
                    2 to fightingStyleChoice(
                        classSlug,
                        level = 2,
                        additionalOptionIds = listOf(featureId(classSlug, "guerriero-benedetto")),
                    ),
                    3 to subclassChoice(classSlug, 3, subclass),
                ),
            ),
            prepared = prepared,
            slots = halfCasterSlots,
            spellGrantsAtLevel = { level ->
                buildList {
                    when (level) {
                        2 -> add(spellGrant("Punizione divina"))
                        3 -> add(
                            spellGrant(
                                "Protezione dal bene e dal male",
                                "Scudo della fede",
                                requiredOptionId = subclass,
                            ),
                        )
                        5 -> {
                            add(spellGrant("Trova cavalcatura"))
                            add(spellGrant("Aiuto", "Zona di verità", requiredOptionId = subclass))
                        }
                        9 -> add(
                            spellGrant(
                                "Faro di speranza",
                                "Dissolvi magie",
                                requiredOptionId = subclass,
                            ),
                        )
                        13 -> add(
                            spellGrant(
                                "Libertà di movimento",
                                "Guardiano della fede",
                                requiredOptionId = subclass,
                            ),
                        )
                        17 -> add(
                            spellGrant(
                                "Comunione",
                                "Colpo infuocato",
                                requiredOptionId = subclass,
                            ),
                        )
                    }
                }
            },
            resourcesAtLevel = { level ->
                listOf(
                    ResourceMaximum(layOnHands, maximum = level * 5),
                    ResourceMaximum(channelDivinity, channelMaximums[level - 1]),
                )
            },
        ),
        resources = listOf(
            ResourceDefinition(
                id = layOnHands,
                name = "Imposizione delle mani",
                recovery = RecoveryPeriod.LONG_REST,
                formula = ResourceFormula.CLASS_LEVEL_TIMES_MULTIPLIER,
                multiplier = 5,
            ),
            ResourceDefinition(
                id = channelDivinity,
                name = "Incanalare divinità",
                recovery = RecoveryPeriod.SHORT_OR_LONG_REST,
                shortRestRecovery = 1,
                description = "Recupera un utilizzo con un riposo breve e tutti con un riposo lungo.",
            ),
            ResourceDefinition(
                id = resourceId(classSlug, "punizione-gratuita"),
                name = "Punizione del paladino: lancio gratuito",
                recovery = RecoveryPeriod.LONG_REST,
                formula = ResourceFormula.FIXED,
                availableFromClassLevel = 2,
            ),
            ResourceDefinition(
                id = resourceId(classSlug, "fido-destriero"),
                name = "Fido destriero: lancio gratuito",
                recovery = RecoveryPeriod.LONG_REST,
                formula = ResourceFormula.FIXED,
                availableFromClassLevel = 5,
            ),
            ResourceDefinition(
                id = resourceId(classSlug, "nube-sacra"),
                name = "Nube sacra",
                recovery = RecoveryPeriod.LONG_REST,
                formula = ResourceFormula.FIXED,
                availableFromClassLevel = 20,
                requiredOptionId = subclass,
                description = "Dopo l'uso gratuito può essere riattivata spendendo uno slot di 5º.",
            ),
        ),
    )
}

private fun ranger(): ClassDefinition {
    val classSlug = "ranger"
    val favoredEnemy = resourceId(classSlug, "nemico-prescelto")
    val subclass = subclassId("cacciatore")
    val favoredEnemyMaximums = ints(2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 6, 6, 6, 6)
    val prepared = ints(2, 3, 4, 5, 6, 6, 7, 7, 9, 9, 10, 10, 11, 11, 12, 12, 14, 14, 15, 15)
    val rangerSkills = skills(
        Skill.ADDESTRARE_ANIMALI,
        Skill.ATLETICA,
        Skill.FURTIVITA,
        Skill.INDAGARE,
        Skill.INTUIZIONE,
        Skill.NATURA,
        Skill.PERCEZIONE,
        Skill.SOPRAVVIVENZA,
    )

    return ClassDefinition(
        id = CharacterClassId.RANGER,
        name = "Ranger",
        primaryAbilities = setOf(Ability.DEXTERITY, Ability.WISDOM),
        multiclassPrerequisiteGroups = listOf(setOf(Ability.DEXTERITY), setOf(Ability.WISDOM)),
        hitDieSides = 10,
        fixedHitPointsPerLevel = 6,
        savingThrowProficiencies = setOf(Ability.STRENGTH, Ability.DEXTERITY),
        skillChoice = skillChoice(classSlug, count = 3, skills = rangerSkills),
        weaponTraining = "Armi semplici e da guerra",
        weaponTrainingGrant = ALL_WEAPONS,
        startingWeaponChoice = startingWeaponChoice(classSlug, ALL_WEAPONS),
        armorTraining = ArmorTrainingGrant(light = true, medium = true, shields = true),
        multiclassSkillChoice = skillChoice(
            classSlug = classSlug,
            count = 1,
            skills = rangerSkills,
            phase = "multiclass",
        ),
        startingEquipment = "A scelta tra A e B: (A) un'armatura di cuoio borchiato, una " +
            "scimitarra, una spada corta, un arco lungo, 20 frecce, una faretra, un focus druidico " +
            "(rametto di vischio), una dotazione da esploratore e 7 mo; oppure (B) 150 mo.",
        multiclassWeaponTraining = "Armi da guerra",
        multiclassArmorTraining = ArmorTrainingGrant(light = true, medium = true, shields = true),
        subclassIds = listOf(subclass),
        spellcastingAbility = Ability.WISDOM,
        spellcastingKind = SpellcastingKind.HALF_CASTER,
        levels = classLevels(
            classSlug = classSlug,
            featureRows = rows(
                "incantesimi|nemico-prescelto|padronanza-d-armi",
                "esploratore-esperto|stile-di-combattimento",
                "sottoclasse|cacciatore-sapienza-del-cacciatore|cacciatore-preda-del-cacciatore",
                "aumento-dei-punteggi-di-caratteristica",
                "attacco-extra",
                "girovago",
                "cacciatore-tattiche-difensive",
                "aumento-dei-punteggi-di-caratteristica",
                "maestria-livello-9",
                "instancabile",
                "cacciatore-preda-del-cacciatore-superiore",
                "aumento-dei-punteggi-di-caratteristica",
                "cacciatore-implacabile",
                "velo-della-natura",
                "cacciatore-difesa-del-cacciatore-superiore",
                "aumento-dei-punteggi-di-caratteristica",
                "precisione-del-cacciatore",
                "sensi-ferini",
                "dono-epico",
                "sterminatore-di-nemici",
            ),
            choices = advancementChoices(
                classSlug = classSlug,
                abilityScoreLevels = setOf(4, 8, 12, 16),
                extra = listOf(
                    1 to weaponMasteryChoice(classSlug, level = 1, count = 2),
                    2 to expertiseChoice(classSlug, level = 2, count = 1),
                    2 to choice(
                        classSlug,
                        level = 2,
                        slug = "lingue",
                        title = "Esploratore esperto: scegli due lingue standard",
                        kind = ChoiceKind.LANGUAGE_PROFICIENCY,
                        count = 2,
                        optionIds = standardLanguageIds,
                    ),
                    2 to fightingStyleChoice(
                        classSlug,
                        level = 2,
                        additionalOptionIds = listOf(featureId(classSlug, "guerriero-druidico")),
                    ),
                    3 to subclassChoice(classSlug, 3, subclass),
                    3 to choice(
                        classSlug,
                        level = 3,
                        slug = "preda-del-cacciatore",
                        title = "Preda del Cacciatore: scegli un'opzione",
                        kind = ChoiceKind.CLASS_OPTION,
                        count = 1,
                        optionIds = listOf(
                            "$PREFIX:subclass-feature:ranger:devastatore-dellorda",
                            "$PREFIX:subclass-feature:ranger:sterminatore-di-colossi",
                        ),
                        description = "Puoi sostituire l'opzione con l'altra al termine di un " +
                            "riposo breve o lungo.",
                    ),
                    7 to choice(
                        classSlug,
                        level = 7,
                        slug = "tattiche-difensive",
                        title = "Tattiche difensive: scegli un'opzione",
                        kind = ChoiceKind.CLASS_OPTION,
                        count = 1,
                        optionIds = listOf(
                            "$PREFIX:subclass-feature:ranger:difesa-dal-multiattacco",
                            "$PREFIX:subclass-feature:ranger:sfuggire-allorda",
                        ),
                        description = "Puoi sostituire l'opzione con l'altra al termine di un " +
                            "riposo breve o lungo.",
                    ),
                    9 to expertiseChoice(classSlug, level = 9, count = 2),
                ),
            ),
            prepared = prepared,
            slots = halfCasterSlots,
            spellGrantsAtLevel = { level ->
                if (level == 1) listOf(spellGrant("Marchio del cacciatore")) else emptyList()
            },
            resourcesAtLevel = { level ->
                listOf(ResourceMaximum(favoredEnemy, favoredEnemyMaximums[level - 1]))
            },
        ),
        resources = listOf(
            ResourceDefinition(
                id = favoredEnemy,
                name = "Nemico prescelto",
                recovery = RecoveryPeriod.LONG_REST,
                description = "Lanci gratuiti di Marchio del cacciatore.",
            ),
            ResourceDefinition(
                id = resourceId(classSlug, "instancabile"),
                name = "Instancabile: punti ferita temporanei",
                recovery = RecoveryPeriod.LONG_REST,
                formula = ResourceFormula.ABILITY_MODIFIER,
                ability = Ability.WISDOM,
                minimum = 1,
                availableFromClassLevel = 10,
            ),
            ResourceDefinition(
                id = resourceId(classSlug, "velo-della-natura"),
                name = "Velo della natura",
                recovery = RecoveryPeriod.LONG_REST,
                formula = ResourceFormula.ABILITY_MODIFIER,
                ability = Ability.WISDOM,
                minimum = 1,
                availableFromClassLevel = 14,
            ),
        ),
    )
}

private fun sorcerer(): ClassDefinition {
    val classSlug = "stregone"
    val sorceryPoints = resourceId(classSlug, "punti-stregoneria")
    val innateSorcery = resourceId(classSlug, "stregoneria-innata")
    val subclass = subclassId("stregoneria-draconica")
    val prepared = ints(2, 4, 6, 7, 9, 10, 11, 12, 14, 15, 16, 16, 17, 17, 18, 18, 19, 20, 21, 22)
    val sorceryPointMaximums = ints(0, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20)

    return ClassDefinition(
        id = CharacterClassId.SORCERER,
        name = "Stregone",
        primaryAbilities = setOf(Ability.CHARISMA),
        hitDieSides = 6,
        fixedHitPointsPerLevel = 4,
        savingThrowProficiencies = setOf(Ability.CONSTITUTION, Ability.CHARISMA),
        skillChoice = skillChoice(
            classSlug,
            count = 2,
            skills = skills(
                Skill.ARCANO,
                Skill.INGANNO,
                Skill.INTIMIDIRE,
                Skill.INTUIZIONE,
                Skill.PERSUASIONE,
                Skill.RELIGIONE,
            ),
        ),
        weaponTraining = "Armi semplici",
        weaponTrainingGrant = SIMPLE_WEAPONS,
        startingWeaponChoice = startingWeaponChoice(classSlug, SIMPLE_WEAPONS),
        startingEquipment = "A scelta tra A e B: (A) una lancia, 2 pugnali, un focus arcano " +
            "(cristallo), una dotazione da avventuriero e 28 mo; oppure (B) 50 mo.",
        subclassIds = listOf(subclass),
        spellcastingAbility = Ability.CHARISMA,
        spellcastingKind = SpellcastingKind.STANDARD,
        levels = classLevels(
            classSlug = classSlug,
            featureRows = rows(
                "incantesimi|stregoneria-innata",
                "fonte-di-magia|metamagia",
                "sottoclasse|draconica-resilienza-draconica|draconica-incantesimi-draconici",
                "aumento-dei-punteggi-di-caratteristica",
                "ripristino-stregonesco",
                "draconica-affinita-elementale",
                "stregoneria-incarnata",
                "aumento-dei-punteggi-di-caratteristica",
                "",
                "metamagia",
                "",
                "aumento-dei-punteggi-di-caratteristica",
                "",
                "draconica-ali-di-drago",
                "",
                "aumento-dei-punteggi-di-caratteristica",
                "metamagia",
                "draconica-seguace-draconico",
                "dono-epico",
                "apoteosi-arcana",
            ),
            choices = advancementChoices(
                classSlug = classSlug,
                abilityScoreLevels = setOf(4, 8, 12, 16),
                extra = listOf(
                    2 to metamagicChoice(classSlug, level = 2, count = 2),
                    3 to subclassChoice(classSlug, 3, subclass),
                    6 to choice(
                        classSlug,
                        level = 6,
                        slug = "affinita-elementale",
                        title = "Scegli il tipo di danno dell'Affinità elementale",
                        kind = ChoiceKind.CLASS_OPTION,
                        count = 1,
                        optionIds = featureOptions(
                            classSlug,
                            "affinita-acido",
                            "affinita-freddo",
                            "affinita-fulmine",
                            "affinita-fuoco",
                            "affinita-veleno",
                        ),
                    ),
                    10 to metamagicChoice(classSlug, level = 10, count = 2),
                    17 to metamagicChoice(classSlug, level = 17, count = 2),
                ),
            ),
            cantrips = ints(4, 4, 4, 5, 5, 5, 5, 5, 5, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6),
            prepared = prepared,
            slots = fullCasterSlots,
            spellGrantsAtLevel = { level ->
                when (level) {
                    3 -> listOf(
                        spellGrant(
                            "Alterare se stesso",
                            "Globo cromatico",
                            "Comando",
                            "Soffio del drago",
                            requiredOptionId = subclass,
                        ),
                    )
                    5 -> listOf(spellGrant("Paura", "Volare", requiredOptionId = subclass))
                    7 -> listOf(
                        spellGrant(
                            "Occhio arcano",
                            "Charme sui mostri",
                            requiredOptionId = subclass,
                        ),
                    )
                    9 -> listOf(
                        spellGrant(
                            "Conoscenza delle leggende",
                            "Richiama drago",
                            requiredOptionId = subclass,
                        ),
                    )
                    else -> emptyList()
                }
            },
            resourcesAtLevel = { level ->
                listOf(
                    ResourceMaximum(sorceryPoints, sorceryPointMaximums[level - 1]),
                    ResourceMaximum(innateSorcery, maximum = 2),
                )
            },
        ),
        resources = listOf(
            ResourceDefinition(
                id = sorceryPoints,
                name = "Punti stregoneria",
                recovery = RecoveryPeriod.LONG_REST,
            ),
            ResourceDefinition(
                id = innateSorcery,
                name = "Stregoneria innata",
                recovery = RecoveryPeriod.LONG_REST,
            ),
            ResourceDefinition(
                id = resourceId(classSlug, "ripristino-stregonesco"),
                name = "Ripristino stregonesco",
                recovery = RecoveryPeriod.LONG_REST,
                formula = ResourceFormula.FIXED,
                availableFromClassLevel = 5,
                description = "Durante un riposo breve recupera punti stregoneria pari a metà " +
                    "del livello da Stregone, arrotondata per difetto.",
            ),
            ResourceDefinition(
                id = resourceId(classSlug, "ali-di-drago"),
                name = "Ali di drago",
                recovery = RecoveryPeriod.LONG_REST,
                formula = ResourceFormula.FIXED,
                availableFromClassLevel = 14,
                requiredOptionId = subclass,
                description = "Dopo l'uso gratuito può essere riattivata spendendo 3 punti stregoneria.",
            ),
            ResourceDefinition(
                id = resourceId(classSlug, "seguace-draconico"),
                name = "Seguace draconico: lancio gratuito",
                recovery = RecoveryPeriod.LONG_REST,
                formula = ResourceFormula.FIXED,
                availableFromClassLevel = 18,
                requiredOptionId = subclass,
            ),
        ),
    )
}

private fun warlock(): ClassDefinition {
    val classSlug = "warlock"
    val pactSlots = resourceId(classSlug, "slot-magia-del-patto")
    val arcanum6 = resourceId(classSlug, "arcanum-mistico-6")
    val arcanum7 = resourceId(classSlug, "arcanum-mistico-7")
    val arcanum8 = resourceId(classSlug, "arcanum-mistico-8")
    val arcanum9 = resourceId(classSlug, "arcanum-mistico-9")
    val subclass = subclassId("patrono-immondo")
    val prepared = ints(2, 3, 4, 5, 6, 7, 8, 9, 10, 10, 11, 11, 12, 12, 13, 13, 14, 14, 15, 15)
    val pactSlotCounts = ints(1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4)
    val pactSlotLevels = ints(1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5)

    return ClassDefinition(
        id = CharacterClassId.WARLOCK,
        name = "Warlock",
        primaryAbilities = setOf(Ability.CHARISMA),
        hitDieSides = 8,
        fixedHitPointsPerLevel = 5,
        savingThrowProficiencies = setOf(Ability.WISDOM, Ability.CHARISMA),
        skillChoice = skillChoice(
            classSlug,
            count = 2,
            skills = skills(
                Skill.ARCANO,
                Skill.INDAGARE,
                Skill.INGANNO,
                Skill.INTIMIDIRE,
                Skill.NATURA,
                Skill.RELIGIONE,
                Skill.STORIA,
            ),
        ),
        weaponTraining = "Armi semplici",
        weaponTrainingGrant = SIMPLE_WEAPONS,
        startingWeaponChoice = startingWeaponChoice(classSlug, SIMPLE_WEAPONS),
        armorTraining = ArmorTrainingGrant(light = true),
        startingEquipment = "A scelta tra A e B: (A) un'armatura di cuoio, un falcetto, 2 pugnali, " +
            "un focus arcano (globo), un libro (scienze occulte), una dotazione da studioso e 15 mo; " +
            "oppure (B) 100 mo.",
        multiclassArmorTraining = ArmorTrainingGrant(light = true),
        subclassIds = listOf(subclass),
        spellcastingAbility = Ability.CHARISMA,
        spellcastingKind = SpellcastingKind.PACT_MAGIC,
        levels = classLevels(
            classSlug = classSlug,
            featureRows = rows(
                "suppliche-occulte|magia-del-patto",
                "scaltrezza-magica",
                "sottoclasse|immondo-benedizione-dell-oscuro|immondo-incantesimi-immondi",
                "aumento-dei-punteggi-di-caratteristica",
                "",
                "immondo-fortuna-dell-oscuro",
                "",
                "aumento-dei-punteggi-di-caratteristica",
                "contatta-patrono",
                "immondo-resilienza-immonda",
                "arcanum-mistico-6",
                "aumento-dei-punteggi-di-caratteristica",
                "arcanum-mistico-7",
                "immondo-scagliare-all-inferno",
                "arcanum-mistico-8",
                "aumento-dei-punteggi-di-caratteristica",
                "arcanum-mistico-9",
                "",
                "dono-epico",
                "maestro-dell-occulto",
            ),
            choices = advancementChoices(
                classSlug = classSlug,
                abilityScoreLevels = setOf(4, 8, 12, 16),
                extra = listOf(
                    1 to invocationChoice(classSlug, level = 1, count = 1),
                    2 to invocationChoice(classSlug, level = 2, count = 2),
                    3 to subclassChoice(classSlug, 3, subclass),
                    5 to invocationChoice(classSlug, level = 5, count = 2),
                    7 to invocationChoice(classSlug, level = 7, count = 1),
                    9 to invocationChoice(classSlug, level = 9, count = 1),
                    10 to choice(
                        classSlug,
                        level = 10,
                        slug = "resilienza-immonda",
                        title = "Resilienza immonda: scegli un tipo di danno",
                        kind = ChoiceKind.CLASS_OPTION,
                        count = 1,
                        optionIds = fiendResilienceDamageIds,
                        description = "Puoi cambiare il tipo al termine di un riposo breve o lungo.",
                    ),
                    11 to arcanumChoice(level = 11, spellLevel = 6),
                    12 to invocationChoice(classSlug, level = 12, count = 1),
                    13 to arcanumChoice(level = 13, spellLevel = 7),
                    15 to invocationChoice(classSlug, level = 15, count = 1),
                    15 to arcanumChoice(level = 15, spellLevel = 8),
                    17 to arcanumChoice(level = 17, spellLevel = 9),
                    18 to invocationChoice(classSlug, level = 18, count = 1),
                ),
            ),
            cantrips = ints(2, 2, 2, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4),
            prepared = prepared,
            pactSlotCounts = pactSlotCounts,
            pactSlotLevels = pactSlotLevels,
            spellGrantsAtLevel = { level ->
                buildList {
                    if (level == 1) {
                        add(
                            spellGrant(
                                "Trova famiglio",
                                requiredOptionId = featureId(classSlug, "patto-della-catena"),
                            ),
                        )
                    }
                    if (level == 9) add(spellGrant("Contattare altri piani"))
                    when (level) {
                        3 -> add(
                            spellGrant(
                                "Comando",
                                "Mani brucianti",
                                "Raggio rovente",
                                "Suggestione",
                                requiredOptionId = subclass,
                            ),
                        )
                        5 -> add(
                            spellGrant(
                                "Nube maleodorante",
                                "Palla di fuoco",
                                requiredOptionId = subclass,
                            ),
                        )
                        7 -> add(
                            spellGrant(
                                "Muro di fuoco",
                                "Scudo di fuoco",
                                requiredOptionId = subclass,
                            ),
                        )
                        9 -> add(
                            spellGrant(
                                "Costrizione",
                                "Piaga degli insetti",
                                requiredOptionId = subclass,
                            ),
                        )
                    }
                }
            },
            resourcesAtLevel = { level ->
                listOf(
                    ResourceMaximum(pactSlots, pactSlotCounts[level - 1]),
                    ResourceMaximum(arcanum6, if (level >= 11) 1 else 0),
                    ResourceMaximum(arcanum7, if (level >= 13) 1 else 0),
                    ResourceMaximum(arcanum8, if (level >= 15) 1 else 0),
                    ResourceMaximum(arcanum9, if (level >= 17) 1 else 0),
                )
            },
        ),
        resources = listOf(
            ResourceDefinition(
                id = pactSlots,
                name = "Slot di Magia del patto",
                recovery = RecoveryPeriod.SHORT_OR_LONG_REST,
            ),
            ResourceDefinition(arcanum6, "Arcanum mistico (6º)", RecoveryPeriod.LONG_REST),
            ResourceDefinition(arcanum7, "Arcanum mistico (7º)", RecoveryPeriod.LONG_REST),
            ResourceDefinition(arcanum8, "Arcanum mistico (8º)", RecoveryPeriod.LONG_REST),
            ResourceDefinition(arcanum9, "Arcanum mistico (9º)", RecoveryPeriod.LONG_REST),
            ResourceDefinition(
                id = resourceId(classSlug, "scaltrezza-magica"),
                name = "Scaltrezza magica",
                recovery = RecoveryPeriod.LONG_REST,
                formula = ResourceFormula.FIXED,
                availableFromClassLevel = 2,
                description = "Recupera metà degli slot del Patto arrotondata per eccesso; " +
                    "al 20º livello li recupera tutti.",
            ),
            ResourceDefinition(
                id = resourceId(classSlug, "contatta-patrono"),
                name = "Contatta patrono: lancio gratuito",
                recovery = RecoveryPeriod.LONG_REST,
                formula = ResourceFormula.FIXED,
                availableFromClassLevel = 9,
            ),
            ResourceDefinition(
                id = resourceId(classSlug, "fortuna-dell-oscuro"),
                name = "Fortuna dell'Oscuro",
                recovery = RecoveryPeriod.LONG_REST,
                formula = ResourceFormula.ABILITY_MODIFIER,
                ability = Ability.CHARISMA,
                minimum = 1,
                availableFromClassLevel = 6,
                requiredOptionId = subclass,
            ),
            ResourceDefinition(
                id = resourceId(classSlug, "scagliare-all-inferno"),
                name = "Scagliare all'Inferno",
                recovery = RecoveryPeriod.LONG_REST,
                formula = ResourceFormula.FIXED,
                availableFromClassLevel = 14,
                requiredOptionId = subclass,
                description = "Dopo l'uso gratuito può essere riattivata spendendo uno slot del Patto.",
            ),
            ResourceDefinition(
                id = resourceId(classSlug, "dono-degli-abissi"),
                name = "Dono degli abissi: lancio gratuito",
                recovery = RecoveryPeriod.LONG_REST,
                formula = ResourceFormula.FIXED,
                availableFromClassLevel = 5,
                requiredOptionId = featureId(classSlug, "dono-degli-abissi"),
            ),
            ResourceDefinition(
                id = resourceId(classSlug, "dono-del-protettore"),
                name = "Dono del protettore",
                recovery = RecoveryPeriod.LONG_REST,
                formula = ResourceFormula.FIXED,
                availableFromClassLevel = 9,
                requiredOptionId = featureId(classSlug, "dono-del-protettore"),
            ),
        ),
    )
}

private fun classLevels(
    classSlug: String,
    featureRows: List<String>,
    choices: Map<Int, List<ChoiceDefinition>> = emptyMap(),
    cantrips: List<Int> = List(20) { 0 },
    prepared: List<Int> = List(20) { 0 },
    preparedSpellPoolAtLevel: (Int) -> String? = { null },
    spellbookAdditions: List<Int> = List(20) { 0 },
    slots: List<List<Int>> = List(20) { emptyList() },
    pactSlotCounts: List<Int> = List(20) { 0 },
    pactSlotLevels: List<Int> = List(20) { 0 },
    spellGrantsAtLevel: (Int) -> List<SpellGrant> = { emptyList() },
    resourcesAtLevel: (Int) -> List<ResourceMaximum> = { emptyList() },
    savingThrowGrantsAtLevel: (Int) -> Set<Ability> = { emptySet() },
    languageGrantsAtLevel: (Int) -> List<String> = { emptyList() },
    effectsAtLevel: (Int) -> List<RuleEffect> = { emptyList() },
): List<ClassLevelDefinition> {
    require(featureRows.size == 20) { "$classSlug deve avere esattamente 20 righe di privilegi." }
    require(cantrips.size == 20) { "$classSlug deve avere 20 valori di trucchetti." }
    require(prepared.size == 20) { "$classSlug deve avere 20 valori di incantesimi preparati." }
    require(spellbookAdditions.size == 20) { "$classSlug deve avere 20 valori per il libro." }
    require(slots.size == 20) { "$classSlug deve avere 20 righe di slot." }
    require(pactSlotCounts.size == 20) { "$classSlug deve avere 20 conteggi di slot del patto." }
    require(pactSlotLevels.size == 20) { "$classSlug deve avere 20 livelli di slot del patto." }
    require(choices.keys.all { it in 1..20 }) { "Scelta fuori dai livelli 1..20 per $classSlug." }

    return (1..20).map { level ->
        ClassLevelDefinition(
            level = level,
            featureIds = featureRows[level - 1]
                .split('|')
                .filter(String::isNotBlank)
                .map { featureId(classSlug, it) },
            choices = choices[level].orEmpty(),
            cantripsKnown = cantrips[level - 1],
            preparedSpellLimit = prepared[level - 1],
            preparedSpellPoolId = preparedSpellPoolAtLevel(level),
            spellbookAdditions = spellbookAdditions[level - 1],
            spellSlots = slots[level - 1],
            pactSlotCount = pactSlotCounts[level - 1],
            pactSlotLevel = pactSlotLevels[level - 1],
            spellGrants = spellGrantsAtLevel(level),
            resourceMaximums = resourcesAtLevel(level),
            savingThrowProficiencyGrants = savingThrowGrantsAtLevel(level),
            languageProficiencyGrants = languageGrantsAtLevel(level),
            effects = effectsAtLevel(level),
        )
    }
}

private fun advancementChoices(
    classSlug: String,
    abilityScoreLevels: Set<Int>,
    extra: List<Pair<Int, ChoiceDefinition>>,
): Map<Int, List<ChoiceDefinition>> {
    val choices = buildList {
        addAll(extra)
        abilityScoreLevels.sorted().forEach { level ->
            add(
                level to poolChoice(
                    id = choiceId(classSlug, level, "aumento-o-talento"),
                    title = "Scegli Aumento dei punteggi di caratteristica o un talento Generale",
                    kind = ChoiceKind.FEAT,
                    count = 1,
                    poolId = "$PREFIX:pool:feats:general",
                ),
            )
        }
        add(
            19 to poolChoice(
                id = choiceId(classSlug, 19, "dono-epico"),
                title = "Scegli un Dono epico o un altro talento di cui possiedi i prerequisiti",
                kind = ChoiceKind.EPIC_BOON,
                count = 1,
                poolId = "$PREFIX:pool:feats:epic-or-other",
            ),
        )
    }
    return choices.groupBy(keySelector = { it.first }, valueTransform = { it.second })
}

private fun choice(
    classSlug: String,
    level: Int,
    slug: String,
    title: String,
    kind: ChoiceKind,
    count: Int,
    optionIds: List<String>,
    description: String = "",
): ChoiceDefinition = ChoiceDefinition(
    id = choiceId(classSlug, level, slug),
    title = title,
    kind = kind,
    count = count,
    optionIds = optionIds,
    description = description,
)

private fun poolChoice(
    id: String,
    title: String,
    kind: ChoiceKind,
    count: Int,
    poolId: String,
    description: String = "",
): ChoiceDefinition = ChoiceDefinition(
    id = id,
    title = title,
    kind = kind,
    count = count,
    poolId = poolId,
    description = description,
)

private fun skillChoice(
    classSlug: String,
    count: Int,
    skills: List<Skill>,
    phase: String = "initial",
): ChoiceDefinition = ChoiceDefinition(
    id = "$PREFIX:choice:$classSlug:$phase:skills",
    title = if (count == 1) "Scegli una competenza in abilità" else "Scegli $count competenze in abilità",
    kind = ChoiceKind.SKILL_PROFICIENCY,
    count = count,
    optionIds = skills.map(::skillId),
)

private fun fixedToolChoice(
    classSlug: String,
    phase: String,
    title: String,
    toolSlug: String,
): ChoiceDefinition = ChoiceDefinition(
    id = "$PREFIX:choice:$classSlug:$phase:tool",
    title = "Ottieni competenza: $title",
    kind = ChoiceKind.TOOL_PROFICIENCY,
    count = 1,
    optionIds = listOf("$PREFIX:tool:$toolSlug"),
)

private fun subclassChoice(
    classSlug: String,
    level: Int,
    subclassId: String,
): ChoiceDefinition = choice(
    classSlug = classSlug,
    level = level,
    slug = "sottoclasse",
    title = "Scegli la sottoclasse",
    kind = ChoiceKind.SUBCLASS,
    count = 1,
    optionIds = listOf(subclassId),
)

private fun expertiseChoice(
    classSlug: String,
    level: Int,
    count: Int,
): ChoiceDefinition = poolChoice(
    id = choiceId(classSlug, level, "maestria"),
    title = if (count == 1) {
        "Scegli una competenza in cui ottenere Maestria"
    } else {
        "Scegli $count competenze in cui ottenere Maestria"
    },
    kind = ChoiceKind.EXPERTISE,
    count = count,
    poolId = "$PREFIX:pool:skills:$classSlug:proficient",
)

private fun weaponMasteryChoice(
    classSlug: String,
    level: Int,
    count: Int,
): ChoiceDefinition = choice(
    classSlug = classSlug,
    level = level,
    slug = "padronanza-d-armi",
    title = if (count == 1) "Scegli una Padronanza d'arma" else "Scegli $count Padronanze d'arma",
    kind = ChoiceKind.WEAPON_MASTERY,
    count = count,
    optionIds = weaponMasteryNamesFor(classSlug).map {
        "$PREFIX:weapon:${it.toContentSlug()}"
    },
)

private fun weaponMasteryNamesFor(classSlug: String): List<String> = when (classSlug) {
    "barbaro" -> simpleMeleeWeapons + martialMeleeWeapons
    "ladro" -> simpleWeapons + listOf(
        "Balestra a mano",
        "Frusta",
        "Scimitarra",
        "Spada corta",
        "Stocco",
    )
    else -> allWeapons
}

private fun fightingStyleChoice(
    classSlug: String,
    level: Int,
    slug: String = "stile-di-combattimento",
    additionalOptionIds: List<String> = emptyList(),
): ChoiceDefinition = choice(
    classSlug = classSlug,
    level = level,
    slug = slug,
    title = "Scegli uno Stile di combattimento",
    kind = ChoiceKind.FIGHTING_STYLE,
    count = 1,
    optionIds = fightingStyleIds + additionalOptionIds,
    description = "Uno stile già posseduto non può essere scelto di nuovo.",
)

private fun metamagicChoice(
    classSlug: String,
    level: Int,
    count: Int,
): ChoiceDefinition = choice(
    classSlug = classSlug,
    level = level,
    slug = "metamagia",
    title = "Scegli $count opzioni di Metamagia",
    kind = ChoiceKind.METAMAGIC,
    count = count,
    optionIds = metamagicIds,
    description = "Le opzioni già possedute non possono essere scelte di nuovo.",
)

private fun invocationChoice(
    classSlug: String,
    level: Int,
    count: Int,
): ChoiceDefinition = poolChoice(
    id = choiceId(classSlug, level, "suppliche-occulte"),
    title = if (count == 1) "Scegli una Supplica occulta" else "Scegli $count Suppliche occulte",
    kind = ChoiceKind.ELDRITCH_INVOCATION,
    count = count,
    poolId = "$PREFIX:pool:eldritch-invocations:warlock",
    description = "Il pool deve filtrare prerequisiti, ripetibilità e suppliche già possedute.",
)

private fun arcanumChoice(level: Int, spellLevel: Int): ChoiceDefinition = poolChoice(
    id = choiceId("warlock", level, "arcanum-mistico-$spellLevel"),
    title = "Scegli l'Arcanum mistico di ${spellLevel}º livello",
    kind = ChoiceKind.ALWAYS_PREPARED_SPELL,
    count = 1,
    poolId = "$PREFIX:pool:spells:warlock:$spellLevel",
)

private fun evocationSpellChoice(
    level: Int,
    count: Int,
    maximumSpellLevel: Int,
): ChoiceDefinition = poolChoice(
    id = choiceId("mago", level, "invocatore-sapiente"),
    title = if (count == 1) {
        "Invocatore sapiente: aggiungi un incantesimo di Invocazione " +
            "(fino al ${maximumSpellLevel}º)"
    } else {
        "Invocatore sapiente: aggiungi $count incantesimi di Invocazione " +
            "(fino al ${maximumSpellLevel}º)"
    },
    kind = ChoiceKind.SPELLBOOK_SPELL,
    count = count,
    poolId = "$PREFIX:pool:spells:mago:evocation",
)

private fun spellGrant(
    vararg spellNames: String,
    requiredOptionId: String? = null,
): SpellGrant = SpellGrant(
    spellIds = spellNames.map { "$PREFIX:spell:${it.toContentSlug()}" },
    requiredOptionId = requiredOptionId,
)

private fun martialArtsDie(level: Int): Int = when (level) {
    in 1..4 -> 6
    in 5..10 -> 8
    in 11..16 -> 10
    else -> 12
}

/**
 * Colonna Movimento senza armatura della tabella Privilegi del monaco, in piedi.
 *
 * Il documento la scrive in metri (+3 m, +4,5 m, +6 m, +7,5 m, +9 m); qui restano
 * piedi perche' e' l'unita' con cui il motore misura la griglia, e l'interfaccia
 * riporta comunque entrambe. Nullo prima del 2º livello: non c'e' ancora bonus.
 */
private fun unarmoredMovementBonus(level: Int): Int? = when (level) {
    in 1..1 -> null
    in 2..5 -> 10
    in 6..9 -> 15
    in 10..13 -> 20
    in 14..17 -> 25
    else -> 30
}

private fun rows(vararg rows: String): List<String> = rows.toList()

private fun ints(vararg values: Int): List<Int> = values.toList()

private fun skills(vararg values: Skill): List<Skill> = values.toList()

private fun skillId(skill: Skill): String =
    "$PREFIX:skill:${skill.name.lowercase().replace('_', '-')}"

private fun featureOptions(classSlug: String, vararg slugs: String): List<String> =
    slugs.map { featureId(classSlug, it) }

private fun featureId(classSlug: String, slug: String): String =
    "$PREFIX:feature:$classSlug:$slug"

private fun subclassId(slug: String): String = "$PREFIX:subclass:$slug"

private fun resourceId(classSlug: String, slug: String): String =
    "$PREFIX:resource:$classSlug:$slug"

private fun choiceId(classSlug: String, level: Int, slug: String): String =
    "$PREFIX:choice:$classSlug:$level:$slug"

private val fightingStyleIds = listOf(
    "$PREFIX:feat:fighting-style:armi-possenti",
    "$PREFIX:feat:fighting-style:due-armi",
    "$PREFIX:feat:fighting-style:difesa",
    "$PREFIX:feat:fighting-style:tiro",
)

private val metamagicIds = listOf(
    "$PREFIX:metamagic:incantesimo-celato",
    "$PREFIX:metamagic:incantesimo-distante",
    "$PREFIX:metamagic:incantesimo-esteso",
    "$PREFIX:metamagic:incantesimo-intensificato",
    "$PREFIX:metamagic:incantesimo-mirato",
    "$PREFIX:metamagic:incantesimo-potenziato",
    "$PREFIX:metamagic:incantesimo-preciso",
    "$PREFIX:metamagic:incantesimo-raddoppiato",
    "$PREFIX:metamagic:incantesimo-rapido",
    "$PREFIX:metamagic:incantesimo-trasmutato",
)

private val standardLanguageIds = listOf(
    "Comune",
    "Lingua dei Segni Comune",
    "Draconico",
    "Nanico",
    "Elfico",
    "Gigante",
    "Gnomesco",
    "Goblin",
    "Halfling",
    "Orchesco",
).map { "$PREFIX:language:${it.toContentSlug()}" }

private val fiendResilienceDamageIds = listOf(
    "acido",
    "contundente",
    "freddo",
    "fuoco",
    "fulmine",
    "necrotico",
    "perforante",
    "veleno",
    "psichico",
    "radioso",
    "tagliente",
    "tuono",
).map { "$PREFIX:damage:$it" }

private val simpleMeleeWeapons = listOf(
    "Ascia",
    "Bastone ferrato",
    "Falcetto",
    "Giavellotto",
    "Lancia",
    "Martello leggero",
    "Mazza",
    "Pugnale",
    "Randello pesante",
    "Randello",
)

private val simpleWeapons = simpleMeleeWeapons + listOf(
    "Arco corto",
    "Balestra leggera",
    "Dardo",
    "Fionda",
)

private val martialMeleeWeapons = listOf(
    "Alabarda",
    "Ascia bipenne",
    "Ascia da battaglia",
    "Falcione",
    "Frusta",
    "Lancia da cavaliere",
    "Maglio",
    "Martello da guerra",
    "Mazza chiodata",
    "Mazzafrusto",
    "Picca",
    "Piccone da guerra",
    "Scimitarra",
    "Spada corta",
    "Spada lunga",
    "Spadone",
    "Stocco",
    "Tridente",
)

private val allWeapons = simpleWeapons + martialMeleeWeapons + listOf(
    "Arco lungo",
    "Balestra a mano",
    "Balestra pesante",
    "Cerbottana",
    "Moschetto",
    "Pistola",
)

private val fullPrepared = ints(
    4, 5, 6, 7, 9, 10, 11, 12, 14, 15,
    16, 16, 17, 17, 18, 18, 19, 20, 21, 22,
)

private val fullCasterSlots: List<List<Int>> = listOf(
    ints(2),
    ints(3),
    ints(4, 2),
    ints(4, 3),
    ints(4, 3, 2),
    ints(4, 3, 3),
    ints(4, 3, 3, 1),
    ints(4, 3, 3, 2),
    ints(4, 3, 3, 3, 1),
    ints(4, 3, 3, 3, 2),
    ints(4, 3, 3, 3, 2, 1),
    ints(4, 3, 3, 3, 2, 1),
    ints(4, 3, 3, 3, 2, 1, 1),
    ints(4, 3, 3, 3, 2, 1, 1),
    ints(4, 3, 3, 3, 2, 1, 1, 1),
    ints(4, 3, 3, 3, 2, 1, 1, 1),
    ints(4, 3, 3, 3, 2, 1, 1, 1, 1),
    ints(4, 3, 3, 3, 3, 1, 1, 1, 1),
    ints(4, 3, 3, 3, 3, 2, 1, 1, 1),
    ints(4, 3, 3, 3, 3, 2, 2, 1, 1),
)

private val halfCasterSlots: List<List<Int>> = listOf(
    ints(2),
    ints(2),
    ints(3),
    ints(3),
    ints(4, 2),
    ints(4, 2),
    ints(4, 3),
    ints(4, 3),
    ints(4, 3, 2),
    ints(4, 3, 2),
    ints(4, 3, 3),
    ints(4, 3, 3),
    ints(4, 3, 3, 1),
    ints(4, 3, 3, 1),
    ints(4, 3, 3, 2),
    ints(4, 3, 3, 2),
    ints(4, 3, 3, 3, 1),
    ints(4, 3, 3, 3, 1),
    ints(4, 3, 3, 3, 2),
    ints(4, 3, 3, 3, 2),
)
