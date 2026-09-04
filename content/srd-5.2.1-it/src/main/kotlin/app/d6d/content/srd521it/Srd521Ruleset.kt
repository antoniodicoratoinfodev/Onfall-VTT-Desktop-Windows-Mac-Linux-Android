package app.d6d.content.srd521it

import app.d6d.domain.combat.ConditionType
import app.d6d.domain.combat.DamageType
import app.d6d.i18n.AppLanguage
import app.d6d.rules.character.Ability
import app.d6d.rules.character.BackgroundDefinition
import app.d6d.rules.character.ClassDefinition
import app.d6d.rules.character.EquipmentPackageDefinition
import app.d6d.rules.character.RuleElementDefinition
import app.d6d.rules.character.RuleElementKind
import app.d6d.rules.character.Skill
import app.d6d.rules.character.WeaponDefinition
import app.d6d.rules.model.CoreRuleIds
import app.d6d.rules.model.LocalizedRuleText
import app.d6d.rules.model.RuleAutomationLevel
import app.d6d.rules.model.RuleEntity
import app.d6d.rules.model.RuleKind
import app.d6d.rules.model.RulesetOrigin
import app.d6d.rules.model.RulesetRevision
import app.d6d.rules.model.RulesetRuntimeConfig

/**
 * Proiezione completa e bilingue del contenuto incluso nel catalogo generico dei regolamenti.
 * L'oggetto risultante è immutabile: la personalizzazione parte sempre da un fork.
 */
object Srd521Ruleset {
    private const val PROJECT_ID = "onfall:srd521"
    private const val REVISION_ID = "onfall:srd521:5.2.1"

    val revision: RulesetRevision by lazy { build() }

    private fun build(): RulesetRevision {
        val italian = Srd521ItContent.packFor(AppLanguage.ITALIAN)
        val english = Srd521ItContent.packFor(AppLanguage.ENGLISH)
        val enClasses = english.classes.associateBy { it.id }
        val enElements = english.elements.associateBy { it.id }
        val enWeapons = english.weapons.associateBy { it.id }
        val enBackgrounds = english.backgrounds.associateBy { it.id }
        val enEquipment = english.equipmentPackages.associateBy { it.id }
        val source = "SRD 5.2.1"
        val license = italian.manifest.license

        val entities = buildList {
            addAll(coreRules(source, license))
            addAll(abilities(source, license))
            addAll(skills(source, license))
            addAll(damageTypes(source, license))
            addAll(conditions(source, license))
            italian.classes.forEach { add(classEntity(it, enClasses.getValue(it.id), source, license)) }
            italian.elements.forEach { add(elementEntity(it, enElements.getValue(it.id), source, license)) }
            italian.weapons.forEach { add(weaponEntity(it, enWeapons.getValue(it.id), source, license)) }
            italian.backgrounds.forEach { add(backgroundEntity(it, enBackgrounds.getValue(it.id), source, license)) }
            italian.equipmentPackages.forEach {
                add(equipmentEntity(it, enEquipment.getValue(it.id), source, license))
            }
        }

        return RulesetRevision.create(
            PROJECT_ID,
            REVISION_ID,
            "5.2.1",
            "SRD 5.2.1",
            "Regolamento standard incluso in Onfall / Standard ruleset bundled with Onfall",
            RulesetOrigin.BUNDLED_STANDARD,
            "",
            RulesetRuntimeConfig.standardSrd521(),
            entities,
            "2025-04-22T00:00:00Z",
        )
    }

    private fun coreRules(source: String, license: String): List<RuleEntity> = listOf(
        core(
            CoreRuleIds.D20_TEST,
            RuleKind.ROLL,
            "Prova d20",
            "D20 Test",
            "Tira un d20, applica bonus, penalità e modificatori, quindi confronta il totale con la CD.",
            "Roll a d20, apply bonuses, penalties, and modifiers, then compare the total with the DC.",
            mapOf("dieSides" to "20", "tieMeetsDifficulty" to "true"),
            source,
            license,
        ),
        core(
            CoreRuleIds.CRITICAL_HIT,
            RuleKind.CORE_MECHANIC,
            "Colpo critico e fallimento naturale",
            "Critical Hit and Natural Failure",
            "Un 20 naturale è un colpo critico; un 1 naturale manca sempre con le regole standard.",
            "A natural 20 is a critical hit; a natural 1 always misses under the standard rules.",
            RulesetRuntimeConfig.standardSrd521().attributesFor(CoreRuleIds.CRITICAL_HIT),
            source,
            license,
        ),
        core(
            CoreRuleIds.EXHAUSTION,
            RuleKind.CONDITION,
            "Indebolimento",
            "Exhaustion",
            "Ogni livello penalizza le prove d20 di 2 e riduce la velocità di 5 piedi; al livello 6 il personaggio muore.",
            "Each level penalizes d20 tests by 2 and reduces Speed by 5 feet; level 6 causes death.",
            RulesetRuntimeConfig.standardSrd521().attributesFor(CoreRuleIds.EXHAUSTION),
            source,
            license,
        ),
        entity(
            CoreRuleIds.PROFICIENCY,
            RuleKind.PROGRESSION,
            "Bonus di competenza",
            "Proficiency Bonus",
            "Il bonus parte da +2, cresce di 1 ogni quattro livelli e raggiunge +6.",
            "The bonus starts at +2, increases by 1 every four levels, and reaches +6.",
            RuleAutomationLevel.ASSISTED,
            RulesetRuntimeConfig.standardSrd521().attributesFor(CoreRuleIds.PROFICIENCY),
            listOf("base", "core", "progression"), source, license,
        ),
        core(
            "onfall:core:combat:initiative",
            RuleKind.ACTION_ECONOMY,
            "Iniziativa e round",
            "Initiative and Rounds",
            "L'iniziativa ordina i turni. Un round termina quando ogni partecipante ha completato il proprio turno.",
            "Initiative orders turns. A round ends when every participant has completed a turn.",
            mapOf("dieSides" to "20"), source, license,
        ),
        core(
            CoreRuleIds.TURN_ECONOMY,
            RuleKind.ACTION_ECONOMY,
            "Economia del turno",
            "Turn Economy",
            "Ogni turno dispone normalmente di movimento, un'Azione, un'Azione Bonus e una Reazione per round.",
            "A turn normally provides movement, one Action, one Bonus Action, and one Reaction per round.",
            mapOf("actions" to "1", "bonusActions" to "1", "reactions" to "1"), source, license,
        ),
        core(
            CoreRuleIds.HIT_POINTS,
            RuleKind.HEALTH_MODEL,
            "Punti Ferita e Punti Ferita Temporanei",
            "Hit Points and Temporary Hit Points",
            "I danni consumano prima i punti ferita temporanei; le cure non superano i punti ferita massimi.",
            "Damage consumes temporary Hit Points first; healing cannot exceed maximum Hit Points.",
            emptyMap(), source, license,
        ),
        core(
            CoreRuleIds.DEATH_SAVES,
            RuleKind.HEALTH_MODEL,
            "Tiri salvezza contro morte",
            "Death Saving Throws",
            "A 0 punti ferita, tre successi stabilizzano e tre fallimenti causano la morte.",
            "At 0 Hit Points, three successes stabilize and three failures cause death.",
            mapOf("successes" to "3", "failures" to "3"), source, license,
        ),
        core(
            CoreRuleIds.CONCENTRATION,
            RuleKind.SAVE,
            "Concentrazione",
            "Concentration",
            "Subire danni mentre si mantiene la concentrazione richiede un tiro salvezza di Costituzione.",
            "Taking damage while concentrating requires a Constitution saving throw.",
            mapOf("minimumDc" to "10", "damageDivisor" to "2"), source, license,
        ),
        core(
            "onfall:core:movement:grid",
            RuleKind.MOVEMENT,
            "Movimento sulla griglia",
            "Grid Movement",
            "La velocità determina la distanza disponibile nel turno; terreni e condizioni possono modificarla.",
            "Speed determines the distance available on a turn; terrain and conditions can modify it.",
            mapOf("defaultCellFeet" to "5"), source, license,
        ),
        entity(
            "onfall:srd521:table:experience-thresholds",
            RuleKind.TABLE,
            "Soglie dei punti esperienza",
            "Experience Point Thresholds",
            "Tabella cumulativa che collega i punti esperienza al livello del personaggio.",
            "Cumulative table mapping Experience Points to character level.",
            RuleAutomationLevel.FULL,
            mapOf(
                "rows" to listOf(
                    0, 300, 900, 2_700, 6_500, 14_000, 23_000, 34_000, 48_000, 64_000,
                    85_000, 100_000, 120_000, 140_000, 165_000, 195_000, 225_000,
                    265_000, 305_000, 355_000,
                ).mapIndexed { index, xp -> "$xp=${index + 1}" }.joinToString(";"),
                "lookup" to "FLOOR",
                "valueType" to "NUMBER",
            ),
            listOf("avanzamento", "experience", "table"),
            source,
            license,
        ),
        entity(
            "onfall:srd521:progression:characters",
            RuleKind.PROGRESSION,
            "Avanzamento dei personaggi",
            "Character Advancement",
            "L'avanzamento standard usa venti livelli e le soglie PE collegate.",
            "Standard advancement uses twenty levels and the linked XP thresholds.",
            RuleAutomationLevel.FULL,
            mapOf(
                "minimumLevel" to "1",
                "maximumCharacterLevel" to "20",
                "enforceExperienceThresholds" to "true",
                "experienceTableRef" to "onfall:srd521:table:experience-thresholds",
            ),
            listOf("avanzamento", "experience", "progression"),
            source,
            license,
        ),
    )

    private fun abilities(source: String, license: String): List<RuleEntity> = Ability.entries.map { ability ->
        val english = when (ability) {
            Ability.STRENGTH -> "Strength"
            Ability.DEXTERITY -> "Dexterity"
            Ability.CONSTITUTION -> "Constitution"
            Ability.INTELLIGENCE -> "Intelligence"
            Ability.WISDOM -> "Wisdom"
            Ability.CHARISMA -> "Charisma"
            else -> ability.italianLabel
        }
        entity(
            "onfall:core:ability:${ability.name.lowercase()}", RuleKind.STAT,
            ability.italianLabel, english,
            "Caratteristica fondamentale usata da prove, tiri salvezza e capacità.",
            "Core ability used by checks, saving throws, and features.",
            RuleAutomationLevel.FULL,
            mapOf(
                "statId" to ability.name,
                "abbreviation" to ability.abbreviation,
                "defaultFormula" to "10",
                "minimumFormula" to "1",
                "maximumFormula" to "30",
                "advancementMaximum" to "20",
                "modifierFormula" to "floor((\${score} - 10) / 2)",
            ),
            listOf("caratteristica", "ability"), source, license,
        )
    }

    private fun skills(source: String, license: String): List<RuleEntity> = Skill.entries.map { skill ->
        entity(
            "onfall:core:skill:${skill.name.lowercase()}", RuleKind.SKILL,
            skill.italianLabel, englishSkill(skill),
            "Abilità associata a ${skill.ability.italianLabel}.",
            "Skill associated with ${englishAbility(skill.ability)}.",
            RuleAutomationLevel.FULL,
            mapOf(
                "skillId" to skill.name,
                "ability" to skill.ability.name,
                "statRef" to skill.ability.name,
            ),
            listOf("abilità", "skill"), source, license,
        )
    }

    private fun damageTypes(source: String, license: String): List<RuleEntity> = DamageType.values().toList()
        .filterNot { it == DamageType.UNTYPED }
        .map { type ->
            val labels = damageLabels.getValue(type)
            entity(
                "onfall:core:damage:${type.name().lowercase()}", RuleKind.DAMAGE_TYPE,
                labels.first, labels.second,
                "Tipo di danno riconosciuto da resistenze, vulnerabilità e immunità.",
                "Damage type recognized by resistances, vulnerabilities, and immunities.",
                RuleAutomationLevel.FULL,
                mapOf("damageTypeId" to type.name()),
                listOf("danno", "damage"), source, license,
            )
        }

    private fun conditions(source: String, license: String): List<RuleEntity> = ConditionType.values().toList()
        .filterNot { it == ConditionType.CUSTOM || it == ConditionType.EXHAUSTION }
        .map { condition ->
            val labels = conditionLabels.getValue(condition)
            entity(
                "onfall:core:condition:${condition.name().lowercase()}", RuleKind.CONDITION,
                labels.first, labels.second,
                "Condizione standard applicabile ai partecipanti al combattimento.",
                "Standard condition applicable to combat participants.",
                RuleAutomationLevel.ASSISTED,
                mapOf("conditionId" to condition.name(), "maximumStacks" to "1"),
                listOf("condizione", "condition"), source, license,
            )
        }

    private fun classEntity(
        it: ClassDefinition,
        en: ClassDefinition,
        source: String,
        license: String,
    ): RuleEntity = entity(
        "srd521:class:${it.id.name.lowercase()}", RuleKind.CLASS,
        it.name, en.name,
        "Classe completa con progressione dal livello 1 al 20, privilegi, risorse e scelte.",
        "Complete class with levels 1–20, features, resources, and choices.",
        RuleAutomationLevel.ASSISTED,
        linkedMapOf(
            "classId" to it.id.name,
            "hitDieSides" to it.hitDieSides.toString(),
            "fixedHitPointsPerLevel" to it.fixedHitPointsPerLevel.toString(),
            "primaryAbilities" to it.primaryAbilities.joinToString(",") { value -> value.name },
            "multiclassPrerequisiteGroups" to it.multiclassPrerequisiteGroups.joinToString(";") { group ->
                group.joinToString(",") { value -> value.name }
            },
            "savingThrowProficiencies" to it.savingThrowProficiencies.joinToString(",") { value -> value.name },
            "spellcastingKind" to it.spellcastingKind.name,
            "spellcastingAbility" to (it.spellcastingAbility?.name ?: ""),
            "maximumLevel" to it.maximumLevel.toString(),
            "skillChoiceCount" to it.skillChoice.count.toString(),
            "weaponCategories" to it.weaponTrainingGrant.categories.joinToString(",") { value -> value.name },
            "martialWeaponProperties" to it.weaponTrainingGrant.martialPropertyFilter
                .joinToString(",") { value -> value.name },
            "armorTrainingLight" to it.armorTraining.light.toString(),
            "armorTrainingMedium" to it.armorTraining.medium.toString(),
            "armorTrainingHeavy" to it.armorTraining.heavy.toString(),
            "armorTrainingShields" to it.armorTraining.shields.toString(),
            "multiclassArmorTrainingLight" to it.multiclassArmorTraining.light.toString(),
            "multiclassArmorTrainingMedium" to it.multiclassArmorTraining.medium.toString(),
            "multiclassArmorTrainingHeavy" to it.multiclassArmorTraining.heavy.toString(),
            "multiclassArmorTrainingShields" to it.multiclassArmorTraining.shields.toString(),
            "subclassIds" to it.subclassIds.joinToString(","),
            "subclassLevel" to it.subclassLevel.toString(),
            "weaponTraining" to it.weaponTraining,
            "startingEquipment" to it.startingEquipment,
            "levelFeatureIds" to it.levels.joinToString(";") { level ->
                "${level.level}:${level.featureIds.joinToString(",")}" 
            },
            "resourceIds" to it.resources.joinToString(",") { value -> value.id },
        ),
        listOf("classe", "class"), source, license,
    )

    private fun elementEntity(
        it: RuleElementDefinition,
        en: RuleElementDefinition,
        source: String,
        license: String,
    ): RuleEntity = entity(
        it.id, it.kind.toRuleKind(), it.name, en.name, it.description, en.description,
        if (it.effects.isNotEmpty() || it.resourceId != null) RuleAutomationLevel.ASSISTED else RuleAutomationLevel.MANUAL,
        buildMap {
            put("elementKind", it.kind.name)
            if (it.activation.isNotBlank()) put("activation", it.activation)
            if (it.prerequisite.isNotBlank()) put("prerequisite", it.prerequisite)
            it.resourceId?.let { resourceId -> put("resourceId", resourceId) }
            if (it.resourceCost > 0) put("resourceCost", it.resourceCost.toString())
            if (it.classEligibility.isNotEmpty()) {
                put("classEligibility", it.classEligibility.joinToString(",") { value ->
                    "${value.classId.name}:${value.minimumLevel}"
                })
            }
            it.requiredOptionId?.let { requiredOptionId -> put("requiredOptionId", requiredOptionId) }
            it.spell?.let { spell ->
                put("spellLevel", spell.level.toString())
                put("school", spell.school)
                put("castingTime", spell.castingTime)
                put("range", spell.range)
                put("components", spell.components)
                put("duration", spell.duration)
                put("ritual", spell.ritual.toString())
                put("concentration", spell.concentration.toString())
            }
            if (it.grantedSpellIds.isNotEmpty()) put("grantedSpellIds", it.grantedSpellIds.joinToString(","))
        },
        listOf(it.kind.name.lowercase()), source, license, it.sourcePage,
    )

    private fun weaponEntity(
        it: WeaponDefinition,
        en: WeaponDefinition,
        source: String,
        license: String,
    ): RuleEntity = entity(
        it.id, RuleKind.ITEM, it.name, en.name,
        "Arma ${it.category.italianLabel.lowercase()} con padronanza ${it.mastery}.",
        "${en.category.name.lowercase().replaceFirstChar(Char::uppercase)} weapon with ${en.mastery} mastery.",
        RuleAutomationLevel.ASSISTED,
        linkedMapOf(
            "itemType" to "WEAPON",
            "category" to it.category.name,
            "reach" to it.reach.name,
            "diceCount" to it.diceCount.toString(),
            "diceSides" to it.diceSides.toString(),
            "fixedDamage" to it.fixedDamage.toString(),
            "damageType" to it.damageType.name(),
            "mastery" to it.mastery,
            "properties" to it.properties.joinToString(",") { value -> value.name },
            "normalRangeFeet" to it.normalRangeFeet.toString(),
            "longRangeFeet" to it.longRangeFeet.toString(),
            "versatileDiceSides" to it.versatileDiceSides.toString(),
        ),
        listOf("arma", "weapon", "item"), source, license,
    )

    private fun backgroundEntity(
        it: BackgroundDefinition,
        en: BackgroundDefinition,
        source: String,
        license: String,
    ): RuleEntity = entity(
        it.id, RuleKind.BACKGROUND, it.name, en.name,
        it.description.ifBlank { "Background con caratteristiche, competenze, talento ed equipaggiamento iniziale." },
        en.description.ifBlank { "Background with abilities, proficiencies, a feat, and starting equipment." },
        RuleAutomationLevel.ASSISTED,
        linkedMapOf(
            "abilityOptions" to it.abilityOptions.joinToString(",") { value -> value.name },
            "featId" to it.featId,
            "skillProficiencies" to it.skillProficiencies.joinToString(",") { value -> value.name },
            "toolChoiceId" to it.toolChoice.id,
            "equipmentChoiceId" to it.equipmentChoice.id,
            "magicInitiateListId" to (it.magicInitiateListId ?: ""),
        ),
        listOf("background"), source, license, it.sourcePage,
    )

    private fun equipmentEntity(
        it: EquipmentPackageDefinition,
        en: EquipmentPackageDefinition,
        source: String,
        license: String,
    ): RuleEntity = entity(
        it.id, RuleKind.ITEM, it.name, en.name, it.description, en.description,
        RuleAutomationLevel.ASSISTED,
        linkedMapOf(
            "itemType" to "EQUIPMENT_PACKAGE",
            "weaponIds" to it.weaponIds.joinToString(","),
            "itemNames" to it.itemNames.joinToString(" | "),
            "armor" to (it.armor?.name ?: ""),
            "shield" to it.shield.toString(),
            "goldPieces" to it.goldPieces.toString(),
        ),
        listOf("equipaggiamento", "equipment", "item"), source, license,
    )

    private fun core(
        id: String,
        kind: RuleKind,
        italianName: String,
        englishName: String,
        italianDescription: String,
        englishDescription: String,
        attributes: Map<String, String>,
        source: String,
        license: String,
    ) = entity(
        id, kind, italianName, englishName, italianDescription, englishDescription,
        RuleAutomationLevel.FULL, attributes, listOf("base", "core"), source, license,
    )

    private fun entity(
        id: String,
        kind: RuleKind,
        italianName: String,
        englishName: String,
        italianDescription: String,
        englishDescription: String,
        automation: RuleAutomationLevel,
        attributes: Map<String, String>,
        tags: List<String>,
        source: String,
        license: String,
        sourcePage: Int = 0,
    ) = RuleEntity(
        id,
        kind,
        RulesetOrigin.BUNDLED_STANDARD,
        LocalizedRuleText.bilingual(italianName, englishName),
        LocalizedRuleText.bilingual(italianDescription, englishDescription),
        "",
        true,
        automation,
        attributes,
        tags,
        source,
        license,
        sourcePage,
    )

    private fun RuleElementKind.toRuleKind(): RuleKind = when (this) {
        RuleElementKind.CLASS_FEATURE -> RuleKind.FEATURE
        RuleElementKind.SUBCLASS_FEATURE -> RuleKind.FEATURE
        RuleElementKind.ORIGIN_FEAT,
        RuleElementKind.GENERAL_FEAT,
        RuleElementKind.FIGHTING_STYLE_FEAT,
        RuleElementKind.EPIC_BOON_FEAT,
        -> RuleKind.FEAT
        RuleElementKind.CANTRIP,
        RuleElementKind.SPELL,
        -> RuleKind.SPELL
        RuleElementKind.COMMON_ACTION -> RuleKind.ACTION
        else -> RuleKind.CUSTOM
    }

    private fun englishAbility(value: Ability): String = when (value) {
        Ability.STRENGTH -> "Strength"
        Ability.DEXTERITY -> "Dexterity"
        Ability.CONSTITUTION -> "Constitution"
        Ability.INTELLIGENCE -> "Intelligence"
        Ability.WISDOM -> "Wisdom"
        Ability.CHARISMA -> "Charisma"
        else -> value.italianLabel
    }

    private fun englishSkill(value: Skill): String = englishSkills[value] ?: value.italianLabel

    private val englishSkills = mapOf(
        Skill.ATLETICA to "Athletics", Skill.ACROBAZIA to "Acrobatics", Skill.FURTIVITA to "Stealth",
        Skill.RAPIDITA_DI_MANO to "Sleight of Hand", Skill.ARCANO to "Arcana", Skill.INDAGARE to "Investigation",
        Skill.NATURA to "Nature", Skill.RELIGIONE to "Religion", Skill.STORIA to "History",
        Skill.ADDESTRARE_ANIMALI to "Animal Handling", Skill.INTUIZIONE to "Insight", Skill.MEDICINA to "Medicine",
        Skill.PERCEZIONE to "Perception", Skill.SOPRAVVIVENZA to "Survival", Skill.INGANNO to "Deception",
        Skill.INTIMIDIRE to "Intimidation", Skill.INTRATTENERE to "Performance", Skill.PERSUASIONE to "Persuasion",
    )

    private val damageLabels = mapOf(
        DamageType.ACID to ("Acido" to "Acid"), DamageType.BLUDGEONING to ("Contundente" to "Bludgeoning"),
        DamageType.COLD to ("Freddo" to "Cold"), DamageType.FIRE to ("Fuoco" to "Fire"),
        DamageType.FORCE to ("Forza" to "Force"), DamageType.LIGHTNING to ("Fulmine" to "Lightning"),
        DamageType.NECROTIC to ("Necrotico" to "Necrotic"), DamageType.PIERCING to ("Perforante" to "Piercing"),
        DamageType.POISON to ("Veleno" to "Poison"), DamageType.PSYCHIC to ("Psichico" to "Psychic"),
        DamageType.RADIANT to ("Radioso" to "Radiant"), DamageType.SLASHING to ("Tagliente" to "Slashing"),
        DamageType.THUNDER to ("Tuono" to "Thunder"),
    )

    private val conditionLabels = mapOf(
        ConditionType.BLINDED to ("Accecato" to "Blinded"), ConditionType.CHARMED to ("Affascinato" to "Charmed"),
        ConditionType.DEAFENED to ("Assordato" to "Deafened"), ConditionType.FRIGHTENED to ("Spaventato" to "Frightened"),
        ConditionType.GRAPPLED to ("Afferrato" to "Grappled"), ConditionType.INCAPACITATED to ("Incapacitato" to "Incapacitated"),
        ConditionType.INVISIBLE to ("Invisibile" to "Invisible"), ConditionType.PARALYZED to ("Paralizzato" to "Paralyzed"),
        ConditionType.PETRIFIED to ("Pietrificato" to "Petrified"), ConditionType.POISONED to ("Avvelenato" to "Poisoned"),
        ConditionType.PRONE to ("Prono" to "Prone"), ConditionType.RESTRAINED to ("Trattenuto" to "Restrained"),
        ConditionType.STUNNED to ("Stordito" to "Stunned"), ConditionType.UNCONSCIOUS to ("Privo di sensi" to "Unconscious"),
    )
}
