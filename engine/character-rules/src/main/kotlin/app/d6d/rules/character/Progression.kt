package app.d6d.rules.character

import app.d6d.i18n.AppLanguage
import app.d6d.i18n.label
import app.d6d.i18n.pick
import kotlinx.serialization.Serializable

/** Una classe e il numero di livelli acquisiti in essa. */
@Serializable
data class ClassLevelState(
    val classId: CharacterClassId,
    val level: Int,
) {
    init {
        require(level >= 1)
    }
}

@Serializable
data class SubclassSelection(
    val classId: CharacterClassId,
    val subclassId: String,
)

@Serializable
data class ChoiceSelection(
    val choiceId: String,
    val optionIds: List<String>,
)

/** Stato spendibile di una risorsa, conservato separatamente dal suo massimo derivato. */
@Serializable
data class ResourcePoolState(
    val resourceId: String,
    val name: String,
    val maximum: Int,
    val spent: Int = 0,
    val recovery: RecoveryPeriod,
    val dieSides: Int = 0,
    val shortRestRecovery: Int = 0,
) {
    init {
        require(resourceId.isNotBlank())
        require(name.isNotBlank())
        require(maximum >= 0)
        require(spent in 0..maximum)
        require(dieSides >= 0)
        require(shortRestRecovery >= 0)
    }

    val remaining: Int get() = maximum - spent

    fun recoveredAfter(period: RecoveryPeriod): ResourcePoolState {
        val recovers = when (period) {
            RecoveryPeriod.SHORT_REST ->
                shortRestRecovery > 0 ||
                    recovery == RecoveryPeriod.SHORT_REST ||
                    recovery == RecoveryPeriod.SHORT_OR_LONG_REST
            RecoveryPeriod.LONG_REST ->
                recovery != RecoveryPeriod.MANUAL && recovery != RecoveryPeriod.TURN
            else -> recovery == period
        }
        if (!recovers) return this
        return if (period == RecoveryPeriod.SHORT_REST && shortRestRecovery > 0) {
            copy(spent = (spent - shortRestRecovery).coerceAtLeast(0))
        } else {
            copy(spent = 0)
        }
    }
}

@Serializable
data class LevelAdvancementRecord(
    val totalLevel: Int,
    val classId: CharacterClassId,
    val classLevel: Int,
    val hitPointIncrease: Int,
    val usedFixedHitPoints: Boolean,
    val selections: List<ChoiceSelection> = emptyList(),
    val abilityScoreIncreases: Map<Ability, Int> = emptyMap(),
    val backgroundAbilityScoreIncreases: Map<Ability, Int> = emptyMap(),
)

/**
 * Parte strutturata della scheda.
 *
 * Uno stato vuoto identifica una scheda legacy/manuale. In questo modo i vecchi
 * salvataggi restano apribili senza inventare scelte che il giocatore non ha fatto.
 */
@Serializable
data class CharacterProgression(
    val contentPackId: String = "",
    val contentPackVersion: String = "",
    val rulesetProjectId: String = "",
    val rulesetRevisionId: String = "",
    val rulesetCanonicalHash: String = "",
    val rulesetRuntimeHash: String = "",
    val runtimeSemanticsVersion: String = "",
    val proficiencyProgression: ProficiencyProgressionDefinition = ProficiencyProgressionDefinition(),
    val maximumCharacterLevel: Int = 20,
    /** Se false, il regolamento consente avanzamenti senza soglie PE. */
    val enforceExperienceThresholds: Boolean = true,
    /** Snapshot della curva usata dalla scheda; non viene risolta dall'ultima revisione. */
    val experienceThresholds: List<Int> = ExperienceProgression.thresholds,
    val statDefinitions: List<CharacterStatDefinition> = emptyList(),
    val skillDefinitions: List<CharacterSkillDefinition> = emptyList(),
    val backgroundId: String = "",
    val classLevels: List<ClassLevelState> = emptyList(),
    val subclasses: List<SubclassSelection> = emptyList(),
    val selections: List<ChoiceSelection> = emptyList(),
    val selectedFeatureIds: List<String> = emptyList(),
    val featIds: List<String> = emptyList(),
    val knownCantripIds: List<String> = emptyList(),
    val preparedSpellIds: List<String> = emptyList(),
    val alwaysPreparedSpellIds: List<String> = emptyList(),
    val spellbookSpellIds: List<String> = emptyList(),
    val resourcePools: List<ResourcePoolState> = emptyList(),
    /**
     * Effetti numerici che i privilegi ottenuti producono sulle statistiche.
     *
     * Sono derivati dal pacchetto, come le riserve di risorse, e vengono
     * ricalcolati a ogni avanzamento. Restano nella progressione perche' la
     * scheda deve poterli applicare da sola: non conosce il content pack.
     */
    val effects: List<RuleEffect> = emptyList(),
    val advancementHistory: List<LevelAdvancementRecord> = emptyList(),
) {
    init {
        require(maximumCharacterLevel >= 1)
        require(experienceThresholds.zipWithNext().all { (first, second) -> second > first })
        // Una fotografia deve restare apribile anche se il relativo ruleset non
        // è installato o se una vecchia curva PE copriva meno livelli del limite
        // attuale. È il confine di pubblicazione del ruleset a rifiutare nuove
        // configurazioni incomplete; il documento salvato non viene corrotto.
        require(statDefinitions.map { it.id }.distinct().size == statDefinitions.size)
        require(skillDefinitions.map { it.id }.distinct().size == skillDefinitions.size)
    }

    val configured: Boolean get() = classLevels.isNotEmpty()
    val totalLevel: Int get() = classLevels.sumOf { it.level }

    fun levelIn(classId: CharacterClassId): Int =
        classLevels.firstOrNull { it.classId == classId }?.level ?: 0

    fun subclassFor(classId: CharacterClassId): String? =
        subclasses.firstOrNull { it.classId == classId }?.subclassId
}

data class LevelUpRequest(
    val classId: CharacterClassId,
    val hitPointIncrease: Int,
    val usedFixedHitPoints: Boolean,
    val selections: List<ChoiceSelection> = emptyList(),
    /** Allocazione del talento Aumento dei punteggi di caratteristica. */
    val abilityScoreIncreases: Map<Ability, Int> = emptyMap(),
    /** Tre punti concessi dal background alla creazione (2+1 oppure 1+1+1). */
    val backgroundAbilityScoreIncreases: Map<Ability, Int> = emptyMap(),
)

data class ProgressionIssue(
    val code: String,
    val message: String,
)

data class LevelUpValidation(
    val issues: List<ProgressionIssue>,
) {
    val valid: Boolean get() = issues.isEmpty()
}

/** Soglie cumulative ufficiali della tabella Avanzamento dei personaggi. */
object ExperienceProgression {
    val thresholds: List<Int> = listOf(
        0,
        300,
        900,
        2_700,
        6_500,
        14_000,
        23_000,
        34_000,
        48_000,
        64_000,
        85_000,
        100_000,
        120_000,
        140_000,
        165_000,
        195_000,
        225_000,
        265_000,
        305_000,
        355_000,
    )

    fun levelForExperience(experiencePoints: Int): Int {
        val xp = experiencePoints.coerceAtLeast(0)
        return thresholds.indexOfLast { xp >= it } + 1
    }

    fun thresholdForLevel(level: Int): Int = thresholds[level.coerceIn(1, 20) - 1]

    fun nextThreshold(currentLevel: Int): Int? =
        if (currentLevel >= 20) null else thresholds[currentLevel.coerceAtLeast(1)]

    fun proficiencyBonus(level: Int): Int = when (level.coerceIn(1, 20)) {
        in 1..4 -> 2
        in 5..8 -> 3
        in 9..12 -> 4
        in 13..16 -> 5
        else -> 6
    }

    /** Dopo il 20º, il GM può concedere un talento ogni 30.000 PE oltre 355.000. */
    fun epicBonusFeatCount(experiencePoints: Int): Int =
        ((experiencePoints - thresholds.last()).coerceAtLeast(0)) / 30_000
}

class CharacterProgressionEngine(private val pack: RulesContentPack) {

    private val language: AppLanguage = if (pack.manifest.locale.startsWith("en", ignoreCase = true)) {
        AppLanguage.ENGLISH
    } else {
        AppLanguage.ITALIAN
    }

    private fun say(italian: String, english: String): String = language.pick(italian, english)

    private fun defaultScore(ability: Ability): Int = pack.stat(ability)?.defaultScore ?: 10

    private fun advancementCap(ability: Ability, absolute: Boolean = false): Int =
        pack.stat(ability)?.let { if (absolute) it.maximumScore else it.advancementMaximum } ?: 20

    private fun eligibleLevel(experiencePoints: Int): Int {
        if (pack.experienceThresholds.isEmpty()) return 0
        val xp = experiencePoints.coerceAtLeast(0)
        return (pack.experienceThresholds.indexOfLast { xp >= it } + 1).coerceAtLeast(1)
    }

    private fun nextExperienceThreshold(currentLevel: Int): Int? =
        pack.experienceThresholds.getOrNull(currentLevel.coerceAtLeast(0))

    fun requirementsFor(
        progression: CharacterProgression,
        classId: CharacterClassId,
        provisionalSelections: List<ChoiceSelection> = emptyList(),
    ): List<ChoiceDefinition> {
        val definition = pack.classDefinition(classId)
        val nextClassLevel = progression.levelIn(classId) + 1
        require(nextClassLevel in 1..definition.maximumLevel) {
            say(
                "${definition.name} è già al livello massimo (${definition.maximumLevel}).",
                "${definition.name} is already at its maximum level (${definition.maximumLevel}).",
            )
        }
        val level = definition.level(nextClassLevel)
        val previous = if (nextClassLevel == 1) null else definition.level(nextClassLevel - 1)
        val activeOptionIds = buildSet {
            progression.selections.forEach { addAll(it.optionIds) }
            progression.subclasses.forEach { add(it.subclassId) }
            addAll(progression.selectedFeatureIds)
            provisionalSelections.forEach { addAll(it.optionIds) }
        }
        val provisionalBackground = provisionalSelections
            .asSequence()
            .flatMap { it.optionIds.asSequence() }
            .mapNotNull(pack::background)
            .firstOrNull()
        fun replaceDuplicateFixedTool(choice: ChoiceDefinition): ChoiceDefinition {
            if (choice.kind != ChoiceKind.TOOL_PROFICIENCY || choice.optionIds.size != 1) return choice
            val fixedToolId = choice.optionIds.single()
            val alreadyGranted = buildSet {
                progression.selections
                    .filterNot { it.choiceId == choice.id }
                    .forEach { addAll(it.optionIds) }
                provisionalSelections
                    .filterNot { it.choiceId == choice.id }
                    .forEach { addAll(it.optionIds) }
                provisionalBackground?.toolChoice?.optionIds?.let(::addAll)
            }
            if (fixedToolId !in alreadyGranted) return choice
            return choice.copy(
                id = "${choice.id}:sostitutiva",
                title = choice.title + say(
                    ": scegli una competenza sostitutiva negli strumenti",
                    ": choose a replacement Tool proficiency",
                ),
                optionIds = emptyList(),
                poolId = "${pack.manifest.id}:pool:tools:any",
            )
        }
        return buildList {
            if (!progression.configured) {
                if (pack.backgrounds.isNotEmpty()) {
                    add(
                        ChoiceDefinition(
                            id = "${pack.manifest.id}:choice:origin:background",
                            title = say("Scegli il background", "Choose a background"),
                            kind = ChoiceKind.BACKGROUND,
                            count = 1,
                            optionIds = pack.backgrounds.map { it.id },
                        ),
                    )
                    provisionalBackground?.let { background ->
                        add(background.toolChoice)
                        add(background.equipmentChoice)
                    }
                } else if (pack.elements.any { it.kind == RuleElementKind.ORIGIN_FEAT }) {
                    add(
                        ChoiceDefinition(
                            id = "${pack.manifest.id}:choice:origin:feat",
                            title = say(
                                "Scegli il talento Origini concesso dal background",
                                "Choose the Origin feat granted by your background",
                            ),
                            kind = ChoiceKind.FEAT,
                            count = 1,
                            poolId = "${pack.manifest.id}:pool:feats:origin",
                        ),
                    )
                }
            }
            if (nextClassLevel == 1) {
                if (progression.configured) {
                    definition.multiclassSkillChoice?.let(::add)
                    definition.multiclassToolChoice?.let { add(replaceDuplicateFixedTool(it)) }
                } else {
                    if (definition.skillChoice.count > 0) add(definition.skillChoice)
                    definition.toolChoice?.let { add(replaceDuplicateFixedTool(it)) }
                    definition.startingEquipmentChoice?.let(::add)
                        ?: definition.startingWeaponChoice?.let(::add)
                }
            }
            addAll(
                level.choices.filter { choice ->
                    choice.requiredOptionId == null || choice.requiredOptionId in activeOptionIds
                },
            )
            definition.levels
                .take(nextClassLevel - 1)
                .flatMap { it.choices }
                .filter { it.replacementWindow == ChoiceReplacementWindow.CLASS_LEVEL_UP }
                .filter { it.requiredOptionId == null || it.requiredOptionId in activeOptionIds }
                .forEach { acquiredChoice ->
                    val acquiredSelection = progression.selections
                        .singleOrNull { it.choiceId == acquiredChoice.id }
                        ?.optionIds
                        .orEmpty()
                    if (acquiredSelection.isEmpty()) return@forEach
                    val replacementPrefix = "${acquiredChoice.id}:replacement:$nextClassLevel"
                    val targetChoiceId = "$replacementPrefix:old"
                    add(
                        ChoiceDefinition(
                            id = targetChoiceId,
                            title = say(
                                "${acquiredChoice.title}: sostituisci un'opzione (facoltativo)",
                                "${acquiredChoice.title}: replace one option (optional)",
                            ),
                            kind = ChoiceKind.REPLACEMENT_TARGET,
                            count = 1,
                            minimumCount = 0,
                            optionIds = acquiredSelection,
                            description = say(
                                "Lascia vuoto per conservare le opzioni attuali.",
                                "Leave empty to keep the current options.",
                            ),
                            replacesChoiceId = acquiredChoice.id,
                        ),
                    )
                    val replacedOptionId = provisionalSelections
                        .firstOrNull { it.choiceId == targetChoiceId }
                        ?.optionIds
                        ?.singleOrNull()
                    if (replacedOptionId != null) {
                        add(
                            acquiredChoice.copy(
                                id = "$replacementPrefix:new",
                                title = say("Scegli la nuova opzione", "Choose the new option"),
                                count = 1,
                                minimumCount = 1,
                                replacementWindow = ChoiceReplacementWindow.NEVER,
                                replacesChoiceId = acquiredChoice.id,
                            ),
                        )
                    }
                }
            val newCantrips = level.cantripsKnown - (previous?.cantripsKnown ?: 0)
            if (newCantrips > 0) {
                add(
                    ChoiceDefinition(
                        id = "${classId.contentId}:$nextClassLevel:cantrips",
                        title = say(
                            "Scegli $newCantrips " +
                                if (newCantrips == 1) "trucchetto" else "trucchetti",
                            "Choose $newCantrips " +
                                if (newCantrips == 1) "cantrip" else "cantrips",
                        ),
                        kind = ChoiceKind.CANTRIP,
                        count = newCantrips,
                        poolId = "spells:${classId.contentId}:cantrip",
                    ),
                )
            }
            val newPrepared = level.preparedSpellLimit - (previous?.preparedSpellLimit ?: 0)
            if (newPrepared > 0) {
                add(
                    ChoiceDefinition(
                        id = "${classId.contentId}:$nextClassLevel:prepared-spells",
                        title = say(
                            "Scegli $newPrepared " +
                                if (newPrepared == 1) "incantesimo preparato" else "incantesimi preparati",
                            "Choose $newPrepared prepared " +
                                if (newPrepared == 1) "spell" else "spells",
                        ),
                        kind = ChoiceKind.PREPARED_SPELL,
                        count = newPrepared,
                        poolId = level.preparedSpellPoolId
                            ?: "spells:${classId.contentId}:available",
                    ),
                )
            }
            if (level.spellbookAdditions > 0) {
                add(
                    ChoiceDefinition(
                        id = "${classId.contentId}:$nextClassLevel:spellbook",
                        title = say(
                            "Aggiungi ${level.spellbookAdditions} incantesimi al libro",
                            "Add ${level.spellbookAdditions} spells to your spellbook",
                        ),
                        kind = ChoiceKind.SPELLBOOK_SPELL,
                        count = level.spellbookAdditions,
                        poolId = "spells:${classId.contentId}:spellbook",
                    ),
                )
            }
            val provisionalOptions = provisionalSelections.flatMap { it.optionIds } +
                listOfNotNull(provisionalBackground?.featId)
            if (provisionalOptions.any { it.endsWith(":feat:origin:abile") }) {
                add(
                    ChoiceDefinition(
                        id = "${pack.manifest.id}:choice:origin:abile:proficiencies",
                        title = say(
                            "Abile: scegli tre competenze in abilità o strumenti",
                            "Skilled: choose three Skill or Tool proficiencies",
                        ),
                        kind = ChoiceKind.SKILL_OR_TOOL_PROFICIENCY,
                        count = 3,
                        poolId = "${pack.manifest.id}:pool:skills-or-tools:any",
                    ),
                )
            }
            if (provisionalOptions.any { it.endsWith(":feat:origin:iniziato-alla-magia") }) {
                addAll(magicInitiateRequirements(provisionalBackground?.magicInitiateListId))
            }
            provisionalOptions
                .mapNotNull(pack::element)
                .firstOrNull { it.id.endsWith(":feat:general:lottatore") }
                ?.let {
                    add(
                        featAbilityIncreaseRequirement(
                            totalLevel = progression.totalLevel + 1,
                            featId = it.id,
                            abilities = listOf(Ability.STRENGTH, Ability.DEXTERITY),
                        ),
                    )
                }
            provisionalOptions
                .mapNotNull(pack::element)
                .firstOrNull { it.kind == RuleElementKind.EPIC_BOON_FEAT }
                ?.let { feat ->
                    val abilities = when {
                        feat.id.endsWith(":dono-offensiva-irresistibile") ->
                            listOf(Ability.STRENGTH, Ability.DEXTERITY)
                        feat.id.endsWith(":dono-richiamo-incantesimi") ->
                            listOf(Ability.INTELLIGENCE, Ability.WISDOM, Ability.CHARISMA)
                        else -> pack.stats.map { it.id }
                    }
                    add(
                        featAbilityIncreaseRequirement(
                            totalLevel = progression.totalLevel + 1,
                            featId = feat.id,
                            abilities = abilities,
                        ),
                    )
                }
            provisionalSelections.forEach { parentSelection ->
                parentSelection.optionIds.forEach { optionId ->
                    when {
                        optionId.endsWith(":subclass:collegio-della-sapienza") -> add(
                            ChoiceDefinition(
                                id = "${parentSelection.choiceId}:sapienza:competenze-bonus",
                                title = say(
                                    "Collegio della Sapienza: scegli tre competenze nelle abilità",
                                    "College of Lore: choose three Skill proficiencies",
                                ),
                                kind = ChoiceKind.SKILL_PROFICIENCY,
                                count = 3,
                                poolId = "${pack.manifest.id}:pool:skills:bardo:any",
                            ),
                        )
                        optionId.endsWith(":ordine-taumaturgo") -> add(
                            bonusCantripChoice(
                                parentSelection.choiceId,
                                stableSlug = "taumaturgo",
                                title = say("Taumaturgo", "Thaumaturge"),
                                count = 1,
                                listSlug = CharacterClassId.CLERIC.contentId,
                            ),
                        )
                        optionId.endsWith(":ordine-mago") -> add(
                            bonusCantripChoice(
                                parentSelection.choiceId,
                                stableSlug = "ordine-primordiale-mago",
                                title = say("Ordine primordiale: Mago", "Primal Order: Magician"),
                                count = 1,
                                listSlug = CharacterClassId.DRUID.contentId,
                            ),
                        )
                        optionId.endsWith(":guerriero-benedetto") -> add(
                            bonusCantripChoice(
                                parentSelection.choiceId,
                                stableSlug = "guerriero-benedetto",
                                title = say("Guerriero benedetto", "Blessed Warrior"),
                                count = 2,
                                listSlug = CharacterClassId.CLERIC.contentId,
                            ),
                        )
                        optionId.endsWith(":guerriero-druidico") -> add(
                            bonusCantripChoice(
                                parentSelection.choiceId,
                                stableSlug = "guerriero-druidico",
                                title = say("Guerriero druidico", "Druidic Warrior"),
                                count = 2,
                                listSlug = CharacterClassId.DRUID.contentId,
                            ),
                        )
                        optionId.endsWith(":patto-del-tomo") -> {
                            add(
                                ChoiceDefinition(
                                    id = "${parentSelection.choiceId}:patto-del-tomo:cantrips",
                                    title = say(
                                        "Patto del tomo: scegli tre trucchetti",
                                        "Pact of the Tome: choose three cantrips",
                                    ),
                                    kind = ChoiceKind.CANTRIP,
                                    count = 3,
                                    poolId = "${pack.manifest.id}:pool:spells:any:cantrip",
                                ),
                            )
                            add(
                                ChoiceDefinition(
                                    id = "${parentSelection.choiceId}:patto-del-tomo:rituals",
                                    title = say(
                                        "Patto del tomo: scegli due rituali di 1º livello",
                                        "Pact of the Tome: choose two level 1 Ritual spells",
                                    ),
                                    kind = ChoiceKind.PREPARED_SPELL,
                                    count = 2,
                                    poolId = "${pack.manifest.id}:pool:spells:any:1:ritual",
                                ),
                            )
                        }
                        optionId.endsWith(":conoscenze-degli-antichi") -> add(
                            ChoiceDefinition(
                                id = "${parentSelection.choiceId}:conoscenze-degli-antichi:talento",
                                title = say(
                                    "Conoscenze degli Antichi: scegli un talento Origini",
                                    "Lessons of the First Ones: choose an Origin feat",
                                ),
                                kind = ChoiceKind.FEAT,
                                count = 1,
                                poolId = "${pack.manifest.id}:pool:feats:origin",
                            ),
                        )
                        optionId.endsWith(":deflagrazione-agonizzante") -> add(
                            invocationCantripTarget(parentSelection.choiceId, optionId, mode = "damage"),
                        )
                        optionId.endsWith(":lancia-occulta") -> add(
                            invocationCantripTarget(parentSelection.choiceId, optionId, mode = "range"),
                        )
                        optionId.endsWith(":deflagrazione-respingente") -> add(
                            invocationCantripTarget(parentSelection.choiceId, optionId, mode = "attack-roll"),
                        )
                    }
                }
            }
        }
    }

    fun validateLevelUp(
        progression: CharacterProgression,
        experiencePoints: Int,
        abilityScores: Map<Ability, Int>,
        request: LevelUpRequest,
    ): LevelUpValidation {
        val issues = mutableListOf<ProgressionIssue>()
        val totalLevel = progression.totalLevel
        if (totalLevel >= pack.maximumCharacterLevel) {
            issues += ProgressionIssue(
                "MAX_LEVEL",
                say(
                    "Il personaggio ha già raggiunto il livello massimo (${pack.maximumCharacterLevel}).",
                    "The character has already reached the maximum level (${pack.maximumCharacterLevel}).",
                ),
            )
        } else if (
            pack.enforceExperienceThresholds &&
            totalLevel > 0 &&
            nextExperienceThreshold(totalLevel) != null &&
            eligibleLevel(experiencePoints) <= totalLevel
        ) {
            val next = requireNotNull(nextExperienceThreshold(totalLevel))
            issues += ProgressionIssue(
                "INSUFFICIENT_XP",
                say(
                    "Servono $next PE per raggiungere il livello ${totalLevel + 1}.",
                    "$next XP are required to reach level ${totalLevel + 1}.",
                ),
            )
        }
        val requestedClassMaximum = pack.classDefinition(request.classId).maximumLevel
        if (progression.levelIn(request.classId) >= requestedClassMaximum) {
            issues += ProgressionIssue(
                "CLASS_MAX_LEVEL",
                say(
                    "La classe scelta è già al livello massimo ($requestedClassMaximum).",
                    "The selected class is already at its maximum level ($requestedClassMaximum).",
                ),
            )
        }
        if (request.hitPointIncrease < 1) {
            issues += ProgressionIssue(
                "HIT_POINTS",
                say("L'incremento dei punti ferita deve essere almeno 1.", "The Hit Point increase must be at least 1."),
            )
        }

        if (progression.configured && progression.levelIn(request.classId) == 0) {
            val requiredGroups = (
                progression.classLevels.flatMap {
                    pack.classDefinition(it.classId).multiclassPrerequisiteGroups
                } + pack.classDefinition(request.classId).multiclassPrerequisiteGroups
                ).distinct()
            requiredGroups.forEach { alternatives ->
                if (alternatives.none { (abilityScores[it] ?: defaultScore(it)) >= 13 }) {
                    val names = alternatives.joinToString(say(" o ", " or ")) { it.label(language) }
                    issues += ProgressionIssue(
                        "MULTICLASS_PREREQUISITE",
                        say(
                            "Per la multiclasse serve almeno 13 in $names.",
                            "Multiclassing requires a score of at least 13 in $names.",
                        ),
                    )
                }
            }
        }

        val duplicateChoiceIds = request.selections
            .groupingBy { it.choiceId }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        if (duplicateChoiceIds.isNotEmpty()) {
            issues += ProgressionIssue(
                "DUPLICATE_CHOICE_ID",
                say("Scelte ripetute: ", "Duplicate choices: ") + duplicateChoiceIds.joinToString() + ".",
            )
        }
        val selectionsById = request.selections.associateBy { it.choiceId }
        val requirements = runCatching {
            requirementsFor(progression, request.classId, request.selections)
        }
            .getOrElse {
                issues += ProgressionIssue(
                    "CLASS_LEVEL",
                    it.message ?: say("Livello di classe non valido.", "Invalid class level."),
                )
                emptyList()
            }
        requirements.forEach { choice ->
            val selected = selectionsById[choice.id]?.optionIds.orEmpty()
            if (selected.size !in choice.minimumCount..choice.count) {
                issues += ProgressionIssue(
                    "CHOICE_COUNT",
                    if (choice.minimumCount == choice.count) {
                        say(
                            "«${choice.title}» richiede esattamente ${choice.count} scelte.",
                            "“${choice.title}” requires exactly ${choice.count} selections.",
                        )
                    } else {
                        say(
                            "«${choice.title}» richiede da ${choice.minimumCount} a ${choice.count} scelte.",
                            "“${choice.title}” requires ${choice.minimumCount} to ${choice.count} selections.",
                        )
                    },
                )
            }
            if (!choice.allowDuplicates && selected.distinct().size != selected.size) {
                issues += ProgressionIssue(
                    "DUPLICATE_CHOICE",
                    say("«${choice.title}» non accetta duplicati.", "“${choice.title}” doesn't allow duplicates."),
                )
            }
            if (choice.optionIds.isNotEmpty() && selected.any { it !in choice.optionIds }) {
                issues += ProgressionIssue(
                    "INVALID_CHOICE",
                    say("«${choice.title}» contiene un'opzione non valida.", "“${choice.title}” contains an invalid option."),
                )
            }
            selected.forEach { optionId ->
                if (
                    !isInPool(
                        optionId,
                        choice.poolId,
                        request.classId,
                        progression.levelIn(request.classId) + 1,
                        progression,
                        request.selections,
                    )
                ) {
                    issues += ProgressionIssue(
                        "INVALID_POOL_CHOICE",
                        say(
                            "«${choice.title}» contiene un'opzione non disponibile: $optionId.",
                            "“${choice.title}” contains an unavailable option: $optionId.",
                        ),
                    )
                }
            }
            val previouslySelected = when (choice.kind) {
                ChoiceKind.FEAT, ChoiceKind.EPIC_BOON -> progression.featIds
                ChoiceKind.CANTRIP -> progression.knownCantripIds
                ChoiceKind.MAGICAL_DISCOVERY ->
                    progression.knownCantripIds + progression.alwaysPreparedSpellIds
                ChoiceKind.ALWAYS_PREPARED_SPELL -> progression.alwaysPreparedSpellIds
                ChoiceKind.PREPARED_SPELL ->
                    progression.preparedSpellIds + progression.alwaysPreparedSpellIds
                ChoiceKind.SPELLBOOK_SPELL -> progression.spellbookSpellIds
                ChoiceKind.CLASS_OPTION,
                ChoiceKind.FIGHTING_STYLE,
                ChoiceKind.WEAPON_MASTERY,
                ChoiceKind.METAMAGIC,
                ChoiceKind.ELDRITCH_INVOCATION,
                -> progression.selectedFeatureIds
                else -> emptyList()
            }
            if (selected.any { it in previouslySelected && it !in repeatableOptionIds }) {
                issues += ProgressionIssue(
                    "ALREADY_SELECTED",
                    say(
                        "«${choice.title}» contiene un'opzione non ripetibile già posseduta.",
                        "“${choice.title}” contains a non-repeatable option already owned.",
                    ),
                )
            }
            if (choice.kind == ChoiceKind.FEATURE_TARGET) {
                val targetSuffix = choice.id.substringAfterLast(":suppliche-occulte:")
                    .substringBeforeLast(":target")
                val previousTargets = progression.selections
                    .filter { it.choiceId.endsWith(":$targetSuffix:target") }
                    .flatMapTo(mutableSetOf()) { it.optionIds }
                if (selected.any { it in previousTargets }) {
                    issues += ProgressionIssue(
                        "ALREADY_SELECTED_TARGET",
                        say(
                            "«${choice.title}» deve essere associata a un trucchetto diverso.",
                            "“${choice.title}” must target a different cantrip.",
                        ),
                    )
                }
            }
            if (choice.id.contains(":conoscenze-degli-antichi:talento")) {
                val previousAncientKnowledgeFeats = progression.selections
                    .filter { it.choiceId.contains(":conoscenze-degli-antichi:talento") }
                    .flatMapTo(mutableSetOf()) { it.optionIds }
                if (selected.any { it in previousAncientKnowledgeFeats }) {
                    issues += ProgressionIssue(
                        "ANCIENT_KNOWLEDGE_REQUIRES_DIFFERENT_FEAT",
                        say(
                            "Ogni acquisizione di Conoscenze degli Antichi richiede un talento Origini diverso.",
                            "Each acquisition of Lessons of the First Ones requires a different Origin feat.",
                        ),
                    )
                }
            }
        }
        val selectedBackground = requirements
            .firstOrNull { it.kind == ChoiceKind.BACKGROUND }
            ?.let { selectionsById[it.id]?.optionIds?.singleOrNull() }
            ?.let(pack::background)
        val backgroundIncreases = selectedBackground?.let {
            resolvedBackgroundIncreases(it, request.backgroundAbilityScoreIncreases)
        }.orEmpty()
        if (selectedBackground != null) {
            val distribution = backgroundIncreases.values.sorted()
            val validDistribution = distribution == listOf(1, 2) || distribution == listOf(1, 1, 1)
            if (
                !validDistribution ||
                backgroundIncreases.keys.any { it !in selectedBackground.abilityOptions } ||
                backgroundIncreases.any { (ability, amount) ->
                    amount < 1 ||
                        (abilityScores[ability] ?: defaultScore(ability)) + amount > advancementCap(ability)
                }
            ) {
                issues += ProgressionIssue(
                    "BACKGROUND_ABILITY_SCORE_INCREASE",
                    say(
                        "Il background richiede +2 e +1 oppure +1 a tutte e tre le caratteristiche " +
                            "indicate, senza superare 20.",
                        "The background requires +2 and +1, or +1 to each of its three listed " +
                            "abilities, without exceeding 20.",
                    ),
                )
            }
            val backgroundSkillIds = selectedBackground.skillProficiencies.mapTo(mutableSetOf()) {
                "${pack.manifest.id}:skill:${it.name.lowercase().replace('_', '-')}"
            }
            val chosenSkillIds = requirements
                .filter {
                    it.kind == ChoiceKind.SKILL_PROFICIENCY ||
                        it.kind == ChoiceKind.SKILL_OR_TOOL_PROFICIENCY
                }
                .flatMapTo(mutableSetOf()) { selectionsById[it.id]?.optionIds.orEmpty() }
            if (chosenSkillIds.any { it in backgroundSkillIds }) {
                issues += ProgressionIssue(
                    "BACKGROUND_SKILL_DUPLICATE",
                    say(
                        "Le competenze di classe devono essere diverse da quelle già concesse dal background.",
                        "Class proficiencies must differ from those already granted by the background.",
                    ),
                )
            }
        } else if (request.backgroundAbilityScoreIncreases.any { it.value != 0 }) {
            issues += ProgressionIssue(
                "UNEXPECTED_BACKGROUND_ABILITY_SCORE_INCREASE",
                say(
                    "Gli aumenti del background richiedono un background selezionato.",
                    "Background ability increases require a selected background.",
                ),
            )
        }
        val acquisitions = requirements.flatMap { choice ->
            selectionsById[choice.id]?.optionIds.orEmpty().mapNotNull { optionId ->
                val bucket = when (choice.kind) {
                    ChoiceKind.FEAT,
                    ChoiceKind.EPIC_BOON,
                    -> "feat"
                    ChoiceKind.CANTRIP -> "cantrip"
                    ChoiceKind.PREPARED_SPELL,
                    ChoiceKind.ALWAYS_PREPARED_SPELL,
                    -> "prepared"
                    ChoiceKind.MAGICAL_DISCOVERY ->
                        if (pack.element(optionId)?.spell?.level == 0) "cantrip" else "prepared"
                    ChoiceKind.SPELLBOOK_SPELL -> "spellbook"
                    ChoiceKind.SKILL_PROFICIENCY,
                    ChoiceKind.SKILL_OR_TOOL_PROFICIENCY,
                    ChoiceKind.TOOL_PROFICIENCY,
                    ChoiceKind.LANGUAGE_PROFICIENCY,
                    -> "proficiency"
                    else -> null
                }
                bucket?.let { it to optionId }
            }
        }
        val duplicateAcquisitions = acquisitions
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        if (duplicateAcquisitions.isNotEmpty()) {
            issues += ProgressionIssue(
                "DUPLICATE_ACQUISITION",
                say(
                    "La stessa opzione non può essere acquisita due volte nello stesso avanzamento: ",
                    "The same option can't be acquired twice in one advancement: ",
                ) + duplicateAcquisitions.joinToString { it.second },
            )
        }
        val definition = pack.classDefinition(request.classId)
        if (definition.spellcastingKind == SpellcastingKind.SPELLBOOK) {
            val selectedBookSpells = requirements
                .filter { it.kind == ChoiceKind.SPELLBOOK_SPELL }
                .flatMap { selectionsById[it.id]?.optionIds.orEmpty() }
            val selectedPreparedSpells = requirements
                .filter {
                    it.kind == ChoiceKind.PREPARED_SPELL &&
                        it.id.startsWith("${CharacterClassId.WIZARD.contentId}:") &&
                        it.id.endsWith(":prepared-spells")
                }
                .flatMap { selectionsById[it.id]?.optionIds.orEmpty() }
            val availableInBook = progression.spellbookSpellIds + selectedBookSpells
            if (selectedPreparedSpells.any { it !in availableInBook }) {
                issues += ProgressionIssue(
                    "SPELL_NOT_IN_SPELLBOOK",
                    say(
                        "Gli incantesimi preparati dal mago devono essere presenti nel suo libro.",
                        "A Wizard's prepared spells must be in their spellbook.",
                    ),
                )
            }
            val wizardSpecialChoices = requirements.filter {
                it.kind == ChoiceKind.ALWAYS_PREPARED_SPELL &&
                    (
                        it.id.contains(":maestria-incantesimo-") ||
                            it.id.endsWith(":incantesimi-personali")
                        )
            }
            val selectedWizardSpecials = wizardSpecialChoices
                .flatMap { selectionsById[it.id]?.optionIds.orEmpty() }
            if (selectedWizardSpecials.any { it !in availableInBook }) {
                issues += ProgressionIssue(
                    "SPELL_NOT_IN_SPELLBOOK",
                    say(
                        "Maestria e Incantesimi personali devono essere scelti dal libro del mago.",
                        "Spell Mastery and Signature Spells must be chosen from the Wizard's spellbook.",
                    ),
                )
            }
            val selectedMasterySpells = wizardSpecialChoices
                .filter { it.id.contains(":maestria-incantesimo-") }
                .flatMap { selectionsById[it.id]?.optionIds.orEmpty() }
            if (
                selectedMasterySpells.any {
                    pack.element(it)?.spell?.castingTime?.trim()?.lowercase() !=
                        say("azione", "action")
                }
            ) {
                issues += ProgressionIssue(
                    "SPELL_MASTERY_CASTING_TIME",
                    say(
                        "Maestria negli incantesimi richiede un tempo di lancio di un'Azione.",
                        "Spell Mastery requires a casting time of one Action.",
                    ),
                )
            }
        }
        val nextTotalLevel = progression.totalLevel + 1
        request.selections.flatMap { it.optionIds }.mapNotNull(pack::element).forEach { element ->
            when {
                element.kind == RuleElementKind.GENERAL_FEAT && nextTotalLevel < 4 ->
                    issues += ProgressionIssue(
                        "FEAT_LEVEL_PREREQUISITE",
                        say(
                            "Il talento «${element.name}» richiede il 4º livello.",
                            "The ${element.name} feat requires level 4.",
                        ),
                    )
                element.kind == RuleElementKind.EPIC_BOON_FEAT && nextTotalLevel < 19 ->
                    issues += ProgressionIssue(
                        "FEAT_LEVEL_PREREQUISITE",
                        say(
                            "Il talento «${element.name}» richiede il 19º livello.",
                            "The ${element.name} feat requires level 19.",
                        ),
                    )
                element.id.endsWith(":lottatore") &&
                    listOf(Ability.STRENGTH, Ability.DEXTERITY)
                        .none { (abilityScores[it] ?: defaultScore(it)) >= 13 } ->
                    issues += ProgressionIssue(
                        "FEAT_ABILITY_PREREQUISITE",
                        say(
                            "Lottatore richiede Forza o Destrezza 13 o superiore.",
                            "Grappler requires Strength or Dexterity 13 or higher.",
                        ),
                    )
                element.id.endsWith(":dono-richiamo-incantesimi") &&
                    progression.classLevels.none {
                        pack.classDefinition(it.classId).spellcastingKind != SpellcastingKind.NONE
                    } &&
                    pack.classDefinition(request.classId).spellcastingKind == SpellcastingKind.NONE ->
                    issues += ProgressionIssue(
                        "FEAT_SPELLCASTING_PREREQUISITE",
                        say(
                            "Il Dono del richiamo degli incantesimi richiede il privilegio Incantesimi.",
                            "Boon of Spell Recall requires the Spellcasting feature.",
                        ),
                    )
            }
        }
        validateMagicInitiate(progression, request, requirements, selectionsById, issues)
        val unexpected = selectionsById.keys - requirements.mapTo(mutableSetOf()) { it.id }
        if (unexpected.isNotEmpty()) {
            issues += ProgressionIssue(
                "UNEXPECTED_CHOICE",
                say("Scelte non richieste: ", "Unexpected selections: ") + unexpected.joinToString() + ".",
            )
        }
        val selectedAbilityScoreIncrease = request.selections
            .flatMap { it.optionIds }
            .any { it.endsWith(":aumento-punteggi-caratteristica") }
        val conditionalAbilityChoices = requirements.filter {
            it.kind == ChoiceKind.ABILITY_SCORE_INCREASE
        }
        val conditionalIncreases = conditionalAbilityChoices
            .flatMap { selectionsById[it.id]?.optionIds.orEmpty() }
            .mapNotNull(::abilityFromOptionId)
            .groupingBy { it }
            .eachCount()
        val actualIncreases = request.abilityScoreIncreases.filterValues { it != 0 }
        val selectedEpicBoon = request.selections
            .flatMap { it.optionIds }
            .mapNotNull(pack::element)
            .any { it.kind == RuleElementKind.EPIC_BOON_FEAT }
        if (conditionalAbilityChoices.isNotEmpty()) {
            if (
                actualIncreases != conditionalIncreases ||
                actualIncreases.any { (ability, amount) ->
                    (abilityScores[ability] ?: defaultScore(ability)) + amount >
                        advancementCap(ability, absolute = selectedEpicBoon)
                }
            ) {
                issues += ProgressionIssue(
                    "ABILITY_SCORE_INCREASE",
                    say(
                        "L'incremento del talento deve applicare +1 alla caratteristica scelta, " +
                            "senza superare il limite della caratteristica.",
                        "The feat increase must add +1 without exceeding that stat's configured limit.",
                    ),
                )
            }
        } else if (selectedAbilityScoreIncrease) {
            if (
                actualIncreases.values.any { it !in 1..2 } ||
                actualIncreases.values.sum() != 2 ||
                actualIncreases.any { (ability, amount) ->
                    (abilityScores[ability] ?: defaultScore(ability)) + amount > advancementCap(ability)
                }
            ) {
                issues += ProgressionIssue(
                    "ABILITY_SCORE_INCREASE",
                    say(
                        "L'aumento richiede +2 a una caratteristica o +1 a due caratteristiche, " +
                            "senza superare 20.",
                        "The increase requires +2 to one ability or +1 to two abilities, " +
                            "without exceeding 20.",
                    ),
                )
            }
        } else if (actualIncreases.isNotEmpty()) {
            issues += ProgressionIssue(
                "UNEXPECTED_ABILITY_SCORE_INCREASE",
                say(
                    "Gli aumenti di caratteristica richiedono il talento corrispondente.",
                    "Ability score increases require the corresponding feat.",
                ),
            )
        }
        return LevelUpValidation(issues.distinct())
    }

    fun applyLevelUp(
        progression: CharacterProgression,
        experiencePoints: Int,
        abilityScores: Map<Ability, Int>,
        request: LevelUpRequest,
    ): CharacterProgression {
        val validation = validateLevelUp(progression, experiencePoints, abilityScores, request)
        require(validation.valid) { validation.issues.joinToString(" ") { it.message } }

        val definition = pack.classDefinition(request.classId)
        val nextClassLevel = progression.levelIn(request.classId) + 1
        val nextTotalLevel = progression.totalLevel + 1
        val requestRequirements = requirementsFor(progression, request.classId, request.selections)
        val requestChoicesById = requestRequirements.associateBy { it.id }
        val replacementPairs = request.selections
            .filter { selection -> requestChoicesById[selection.choiceId]?.kind == ChoiceKind.REPLACEMENT_TARGET }
            .mapNotNull { targetSelection ->
                val targetDefinition = requestChoicesById.getValue(targetSelection.choiceId)
                val sourceChoiceId = targetDefinition.replacesChoiceId ?: return@mapNotNull null
                val oldOptionId = targetSelection.optionIds.singleOrNull() ?: return@mapNotNull null
                val newOptionId = request.selections
                    .firstOrNull { selection ->
                        val definition = requestChoicesById[selection.choiceId]
                        definition?.replacesChoiceId == sourceChoiceId &&
                            definition.kind != ChoiceKind.REPLACEMENT_TARGET
                    }
                    ?.optionIds
                    ?.singleOrNull()
                    ?: return@mapNotNull null
                Triple(sourceChoiceId, oldOptionId, newOptionId)
            }
        val newClassLevels = progression.classLevels
            .filterNot { it.classId == request.classId } +
            ClassLevelState(request.classId, nextClassLevel)
        val levelDefinition = definition.level(nextClassLevel)
        val persistedRequestSelections = request.selections.filter { selection ->
            requestChoicesById[selection.choiceId]?.replacesChoiceId == null
        }
        val allSelections = progression.selections + persistedRequestSelections
        val allSelectedOptionIds = (allSelections + request.selections).flatMapTo(mutableSetOf()) { it.optionIds }
            .apply {
                addAll(progression.subclasses.map { it.subclassId })
                addAll(progression.selectedFeatureIds)
            }
        val cumulativeLevelDefinitions = newClassLevels.flatMap { classLevel ->
            pack.classDefinition(classLevel.classId).levels.take(classLevel.level)
        }
        val activeCumulativeFeatureIds = cumulativeLevelDefinitions
            .flatMap { it.featureIds }
            .filter { featureId ->
                val requirement = pack.element(featureId)?.requiredOptionId
                requirement == null || requirement in allSelectedOptionIds
            }
        val newlyGrantedSpells = cumulativeLevelDefinitions
            .flatMap { it.spellGrants }
            .filter { it.requiredOptionId == null || it.requiredOptionId in allSelectedOptionIds }
            .flatMap { it.spellIds }
            .plus(
                (
                    progression.selectedFeatureIds +
                        activeCumulativeFeatureIds +
                        request.selections.flatMap { it.optionIds }
                    )
                    .distinct()
                    .mapNotNull(pack::element)
                    .flatMap { it.grantedSpellIds },
            )
        val subclassOption = request.selections
            .firstOrNull { selection ->
                requestChoicesById[selection.choiceId]?.kind == ChoiceKind.SUBCLASS
            }
            ?.optionIds
            ?.singleOrNull()
        val subclasses = if (subclassOption == null) {
            progression.subclasses
        } else {
            progression.subclasses.filterNot { it.classId == request.classId } +
                SubclassSelection(request.classId, subclassOption)
        }
        val selectedIdsByKind = request.selections.flatMap { selection ->
            val kind = requestChoicesById.getValue(selection.choiceId).kind
            selection.optionIds.map { kind to it }
        }
        val selectedBackgroundId = selectedIdsByKind
            .firstOrNull { it.first == ChoiceKind.BACKGROUND }
            ?.second
        val selectedBackground = selectedBackgroundId?.let(pack::background)
        val backgroundIncreases = selectedBackground?.let {
            resolvedBackgroundIncreases(it, request.backgroundAbilityScoreIncreases)
        }.orEmpty()

        val updated = progression.copy(
            contentPackId = pack.manifest.id,
            contentPackVersion = pack.manifest.version,
            rulesetProjectId = pack.manifest.rulesetProjectId,
            rulesetRevisionId = pack.manifest.rulesetRevisionId,
            rulesetCanonicalHash = pack.manifest.rulesetCanonicalHash,
            rulesetRuntimeHash = pack.manifest.rulesetRuntimeHash,
            runtimeSemanticsVersion = pack.manifest.runtimeSemanticsVersion,
            proficiencyProgression = pack.proficiencyProgression,
            maximumCharacterLevel = pack.maximumCharacterLevel,
            enforceExperienceThresholds = pack.enforceExperienceThresholds,
            experienceThresholds = pack.experienceThresholds,
            statDefinitions = pack.stats,
            skillDefinitions = pack.skills,
            backgroundId = selectedBackgroundId ?: progression.backgroundId,
            classLevels = newClassLevels.sortedWith(
                compareBy<ClassLevelState> { it.classId.ordinal }.thenBy { it.classId.value },
            ),
            subclasses = subclasses,
            selections = allSelections,
            selectedFeatureIds = (
                progression.selectedFeatureIds +
                    levelDefinition.featureIds.filter { featureId ->
                        val requirement = pack.element(featureId)?.requiredOptionId
                        requirement == null || requirement in allSelectedOptionIds
                    } +
                    selectedIdsByKind.filter { it.first in featureChoiceKinds }.map { it.second }
                ).distinct(),
            featIds = (
                progression.featIds +
                    listOfNotNull(selectedBackground?.featId) +
                    selectedIdsByKind.filter { it.first == ChoiceKind.FEAT || it.first == ChoiceKind.EPIC_BOON }
                        .map { it.second }
                ).distinct(),
            knownCantripIds = (
                progression.knownCantripIds +
                    selectedIdsByKind.filter { it.first == ChoiceKind.CANTRIP }.map { it.second } +
                    selectedIdsByKind
                        .filter {
                            it.first == ChoiceKind.MAGICAL_DISCOVERY &&
                                pack.element(it.second)?.spell?.level == 0
                        }
                        .map { it.second }
                ).distinct(),
            preparedSpellIds = (
                progression.preparedSpellIds +
                    selectedIdsByKind.filter { it.first == ChoiceKind.PREPARED_SPELL }.map { it.second }
                ).distinct(),
            alwaysPreparedSpellIds = (
                progression.alwaysPreparedSpellIds +
                    newlyGrantedSpells +
                    selectedIdsByKind
                        .filter { it.first == ChoiceKind.ALWAYS_PREPARED_SPELL }
                        .map { it.second } +
                    selectedIdsByKind
                        .filter {
                            it.first == ChoiceKind.MAGICAL_DISCOVERY &&
                                pack.element(it.second)?.spell?.level != 0
                        }
                        .map { it.second }
                ).distinct(),
            spellbookSpellIds = (
                progression.spellbookSpellIds +
                    selectedIdsByKind.filter { it.first == ChoiceKind.SPELLBOOK_SPELL }.map { it.second }
                ).distinct(),
            advancementHistory = progression.advancementHistory + LevelAdvancementRecord(
                totalLevel = nextTotalLevel,
                classId = request.classId,
                classLevel = nextClassLevel,
                hitPointIncrease = request.hitPointIncrease,
                usedFixedHitPoints = request.usedFixedHitPoints,
                selections = request.selections,
                abilityScoreIncreases = request.abilityScoreIncreases,
                backgroundAbilityScoreIncreases = backgroundIncreases,
            ),
        )
        val absoluteIncrease = if (
            request.selections
                .flatMap { it.optionIds }
                .mapNotNull(pack::element)
                .any { it.kind == RuleElementKind.EPIC_BOON_FEAT }
        ) {
            true
        } else {
            false
        }
        val scoresAfterAdvancement = abilityScores.toMutableMap().apply {
            backgroundIncreases.forEach { (ability, increase) ->
                this[ability] = (getOrDefault(ability, defaultScore(ability)) + increase)
                    .coerceAtMost(advancementCap(ability))
            }
            request.abilityScoreIncreases.forEach { (ability, increase) ->
                this[ability] = (getOrDefault(ability, defaultScore(ability)) + increase)
                    .coerceAtMost(advancementCap(ability, absoluteIncrease))
            }
        }
        val updatedAfterReplacements = replacementPairs.fold(updated) { current, (sourceChoiceId, oldId, newId) ->
            fun List<String>.withReplacement(): List<String> = map { id ->
                if (id == oldId) newId else id
            }.distinct()
            current.copy(
                selections = current.selections.map { selection ->
                    if (selection.choiceId == sourceChoiceId) {
                        selection.copy(optionIds = selection.optionIds.withReplacement())
                    } else {
                        selection
                    }
                },
                selectedFeatureIds = current.selectedFeatureIds.withReplacement(),
                featIds = current.featIds.withReplacement(),
                knownCantripIds = current.knownCantripIds.withReplacement(),
                preparedSpellIds = current.preparedSpellIds.withReplacement(),
                alwaysPreparedSpellIds = current.alwaysPreparedSpellIds.withReplacement(),
                spellbookSpellIds = current.spellbookSpellIds.withReplacement(),
            )
        }
        return refreshDerivedState(updatedAfterReplacements, scoresAfterAdvancement)
    }

    /**
     * Ricalcola tutto ciò che dipende dalle opzioni attualmente attive.
     *
     * Serve sia dopo un passaggio di livello sia quando una regola permette di
     * sostituire una scelta al riposo. In particolare rimuove privilegi e
     * incantesimi della vecchia opzione prima di applicare quelli della nuova.
     */
    fun refreshDerivedState(
        progression: CharacterProgression,
        abilityScores: Map<Ability, Int>,
    ): CharacterProgression {
        val allLevelFeatureIds = pack.classes
            .flatMap { definition -> definition.levels.flatMap { it.featureIds } }
            .toSet()
        val retainedChosenFeatures = progression.selectedFeatureIds
            .filterNot { it in allLevelFeatureIds }
        val optionIdsBeforeLevelFeatures = buildSet {
            progression.selections.forEach { addAll(it.optionIds) }
            progression.subclasses.forEach { add(it.subclassId) }
            addAll(retainedChosenFeatures)
        }
        val attainedLevelFeatureIds = progression.classLevels.flatMap { classLevel ->
            pack.classDefinition(classLevel.classId)
                .levels
                .take(classLevel.level)
                .flatMap { it.featureIds }
        }
        val activeLevelFeatureIds = buildList {
            var changed: Boolean
            do {
                changed = false
                attainedLevelFeatureIds.forEach { featureId ->
                    if (featureId in this) return@forEach
                    val requirement = pack.element(featureId)?.requiredOptionId
                    if (requirement == null || requirement in optionIdsBeforeLevelFeatures || requirement in this) {
                        add(featureId)
                        changed = true
                    }
                }
            } while (changed)
        }
        val selectedFeatureIds = (activeLevelFeatureIds + retainedChosenFeatures).distinct()
        val activeOptionIds = buildSet {
            progression.selections.forEach { addAll(it.optionIds) }
            progression.subclasses.forEach { add(it.subclassId) }
            addAll(selectedFeatureIds)
            addAll(progression.featIds)
        }
        val attainedLevels = progression.classLevels.flatMap { classLevel ->
            pack.classDefinition(classLevel.classId).levels.take(classLevel.level)
        }
        val activeGrantedSpellIds = attainedLevels
            .flatMap { it.spellGrants }
            .filter { it.requiredOptionId == null || it.requiredOptionId in activeOptionIds }
            .flatMap { it.spellIds }
            .plus(
                activeOptionIds
                    .mapNotNull(pack::element)
                    .filter { it.requiredOptionId == null || it.requiredOptionId in activeOptionIds }
                    .flatMap { it.grantedSpellIds },
            )
        val everyGeneratedSpellId = buildSet {
            pack.classes.forEach { definition ->
                definition.levels.forEach { level ->
                    level.spellGrants.forEach { addAll(it.spellIds) }
                }
            }
            pack.elements.forEach { addAll(it.grantedSpellIds) }
        }
        val choicesById = pack.classes
            .flatMap { it.levels }
            .flatMap { it.choices }
            .associateBy { it.id }
        val explicitlyAlwaysPrepared = progression.selections.flatMap { selection ->
            when (choicesById[selection.choiceId]?.kind) {
                ChoiceKind.ALWAYS_PREPARED_SPELL -> selection.optionIds
                ChoiceKind.MAGICAL_DISCOVERY -> selection.optionIds.filter { id ->
                    pack.element(id)?.spell?.level != 0
                }
                else -> emptyList()
            }
        }
        val alwaysPreparedSpellIds = (
            progression.alwaysPreparedSpellIds.filterNot { it in everyGeneratedSpellId } +
                explicitlyAlwaysPrepared +
                activeGrantedSpellIds
            ).distinct()
        val refreshed = progression.copy(
            selectedFeatureIds = selectedFeatureIds,
            alwaysPreparedSpellIds = alwaysPreparedSpellIds,
        )
        return refreshed.copy(
            resourcePools = deriveResourcePools(refreshed, abilityScores),
            effects = deriveEffects(refreshed),
        )
    }

    /**
     * Raccoglie gli effetti numerici di tutto cio' che il personaggio ha ottenuto.
     *
     * Le due sorgenti sono i livelli di classe — che li portano automaticamente —
     * e gli elementi scelti: privilegi, talenti, sottoclassi. Gli effetti
     * progressivi dichiarano un gruppo e non si sommano fra loro: vale il piu'
     * alto, cosi' il Movimento senza armatura passa da +10 a +30 invece di
     * accumularli tutti.
     */
    fun deriveEffects(progression: CharacterProgression): List<RuleEffect> {
        val fromLevels = progression.classLevels.flatMap { classLevel ->
            pack.classDefinition(classLevel.classId)
                .levels
                .take(classLevel.level)
                .flatMap { it.effects }
        }
        val chosenIds = buildSet {
            addAll(progression.selectedFeatureIds)
            addAll(progression.featIds)
            addAll(progression.subclasses.map { it.subclassId })
            progression.selections.forEach { addAll(it.optionIds) }
        }
        val fromElements = chosenIds.mapNotNull(pack::element).flatMap { it.effects }
        val (grouped, single) = (fromLevels + fromElements).partition { it.group.isNotBlank() }
        return single + grouped
            .groupBy { it.group }
            .map { (_, sameGroup) -> sameGroup.maxBy { it.amount } }
    }

    private fun deriveResourcePools(
        progression: CharacterProgression,
        abilityScores: Map<Ability, Int>,
    ): List<ResourcePoolState> {
        val existing = progression.resourcePools.associateBy { it.resourceId }
        val selectedOptionIds = buildSet {
            progression.selections.forEach { addAll(it.optionIds) }
            progression.subclasses.forEach { add(it.subclassId) }
            addAll(progression.selectedFeatureIds)
        }
        val classResources = progression.classLevels.flatMap { classLevel ->
            val definition = pack.classDefinition(classLevel.classId)
            val level = definition.level(classLevel.level)
            definition.resources.mapNotNull { resource ->
                if (classLevel.level < resource.availableFromClassLevel) {
                    return@mapNotNull null
                }
                if (
                    resource.requiredOptionId != null &&
                    resource.requiredOptionId !in selectedOptionIds
                ) {
                    return@mapNotNull null
                }
                val table = level.resourceMaximums.firstOrNull { it.resourceId == resource.id }
                val maximum = when (resource.formula) {
                    ResourceFormula.TABLE -> table?.maximum ?: 0
                    ResourceFormula.FIXED -> resource.fixedMaximum
                    ResourceFormula.CLASS_LEVEL -> classLevel.level
                    ResourceFormula.CLASS_LEVEL_TIMES_MULTIPLIER -> classLevel.level * resource.multiplier
                    ResourceFormula.ABILITY_MODIFIER -> abilityModifier(abilityScores[resource.ability] ?: 10)
                    ResourceFormula.PROFICIENCY_BONUS ->
                        pack.proficiencyProgression.bonus(progression.totalLevel)
                }.coerceAtLeast(resource.minimum)
                val previous = existing[resource.id]
                ResourcePoolState(
                    resourceId = resource.id,
                    name = resource.name,
                    maximum = maximum,
                    spent = previous?.spent?.coerceAtMost(maximum) ?: 0,
                    recovery = if (
                        resource.fullShortRestRecoveryFromLevel > 0 &&
                        classLevel.level >= resource.fullShortRestRecoveryFromLevel
                    ) {
                        RecoveryPeriod.SHORT_OR_LONG_REST
                    } else {
                        resource.recovery
                    },
                    dieSides = table?.dieSides ?: previous?.dieSides ?: 0,
                    shortRestRecovery = resource.shortRestRecovery,
                )
            }
        }
        val magicInitiateResources = progression.selections
            .filter { it.choiceId.endsWith(":magic-initiate:list") }
            .flatMap { it.optionIds }
            .distinct()
            .map { listId ->
                val stableListName = listId.substringAfterLast(':')
                    .replaceFirstChar { it.uppercase() }
                val listLabel = CharacterClassId.entries
                    .firstOrNull { it.contentId == listId.substringAfterLast(':') }
                    ?.label(language)
                    ?: stableListName
                val resourceId = "${pack.manifest.id}:resource:magic-initiate:$stableListName"
                ResourcePoolState(
                    resourceId = resourceId,
                    name = say(
                        "Iniziato alla magia ($listLabel): lancio gratuito",
                        "Magic Initiate ($listLabel): free casting",
                    ),
                    maximum = 1,
                    spent = existing[resourceId]?.spent?.coerceAtMost(1) ?: 0,
                    recovery = RecoveryPeriod.LONG_REST,
                    shortRestRecovery = 0,
                )
            }
        return (classResources + magicInitiateResources).distinctBy { it.resourceId }
    }

    private fun isInPool(
        optionId: String,
        poolId: String?,
        classId: CharacterClassId,
        classLevel: Int,
        progression: CharacterProgression,
        provisionalSelections: List<ChoiceSelection>,
    ): Boolean {
        if (poolId == null) return true
        // I content pack possono namespace-are i pool (per esempio
        // `srd521-it:pool:skills:barbaro`); il significato resta quello della
        // porzione finale e non viene legato a un ID globale dell'app.
        val semanticPool = poolId
            .removePrefix("${pack.manifest.id}:pool:")
            .removePrefix("pool:")
        if (semanticPool.startsWith("skills:")) {
            val slug = optionId.substringAfterLast(':').replace('-', '_')
            return optionId.startsWith("${pack.manifest.id}:skill:") &&
                pack.skills.any {
                    it.id.name.lowercase() == slug ||
                        it.id.value.substringAfterLast(':').replace('-', '_').lowercase() == slug
                }
        }
        if (semanticPool.startsWith("tools:")) {
            return optionId.startsWith("${pack.manifest.id}:tool:")
        }
        if (semanticPool.startsWith("weapons:")) {
            return optionId.startsWith("${pack.manifest.id}:weapon:")
        }
        if (semanticPool.startsWith("skills-or-tools:")) {
            return optionId.startsWith("${pack.manifest.id}:skill:") ||
                optionId.startsWith("${pack.manifest.id}:tool:")
        }
        if (semanticPool.startsWith("beasts:")) {
            return optionId.startsWith("${pack.manifest.id}:beast:")
        }
        if (semanticPool.startsWith("known-cantrips:warlock:")) {
            val knownIds = progression.knownCantripIds +
                provisionalSelections.flatMap { it.optionIds }
            val eligibleIds = if (semanticPool.endsWith(":range")) {
                invocationRangeCantripIds
            } else {
                invocationDamageCantripIds
            }
            return optionId in knownIds &&
                optionId in eligibleIds &&
                pack.element(optionId)?.kind == RuleElementKind.CANTRIP
        }
        val element = pack.element(optionId) ?: return false
        return when {
            semanticPool == "feats:general" ->
                element.kind == RuleElementKind.GENERAL_FEAT ||
                    element.kind == RuleElementKind.ORIGIN_FEAT
            semanticPool == "feats:origin" -> element.kind == RuleElementKind.ORIGIN_FEAT
            semanticPool == "feats:epic-or-other" -> element.kind in setOf(
                RuleElementKind.ORIGIN_FEAT,
                RuleElementKind.GENERAL_FEAT,
                RuleElementKind.FIGHTING_STYLE_FEAT,
                RuleElementKind.EPIC_BOON_FEAT,
            )
            semanticPool == "feats:epic" -> element.kind == RuleElementKind.EPIC_BOON_FEAT
            semanticPool == "feats:fighting-style" -> element.kind == RuleElementKind.FIGHTING_STYLE_FEAT
            semanticPool.startsWith("spells:") -> {
                val spell = element.spell ?: return false
                val eligible = if (semanticPool.startsWith("spells:any:")) {
                    true
                } else if (semanticPool.startsWith("spells:magic-initiate:")) {
                    element.classEligibility.any {
                        it.classId in setOf(
                            CharacterClassId.CLERIC,
                            CharacterClassId.DRUID,
                            CharacterClassId.WIZARD,
                        )
                    }
                } else if (semanticPool == "spells:bardo:magical-discoveries") {
                    element.classEligibility.any {
                        it.classId in setOf(
                            CharacterClassId.CLERIC,
                            CharacterClassId.DRUID,
                            CharacterClassId.WIZARD,
                        )
                    }
                } else if (semanticPool == "spells:bardo:magical-secrets") {
                    element.classEligibility.any {
                        it.classId in setOf(
                            CharacterClassId.BARD,
                            CharacterClassId.CLERIC,
                            CharacterClassId.DRUID,
                            CharacterClassId.WIZARD,
                        )
                    }
                } else {
                    val listClass = semanticPool.substringAfter("spells:")
                        .substringBefore(':')
                        .let { slug ->
                            CharacterClassId.entries.firstOrNull { it.contentId == slug }
                        }
                    element.classEligibility.any {
                        it.classId == (listClass ?: classId) &&
                            classLevel >= it.minimumLevel
                    }
                }
                eligible && when {
                    semanticPool.endsWith(":cantrip") -> spell.level == 0
                    semanticPool == "spells:bardo:magical-discoveries" ->
                        spell.level in 0..maximumSpellLevel(classId, classLevel)
                    semanticPool == "spells:any:1:ritual" ->
                        spell.level == 1 && spell.ritual
                    semanticPool.substringAfterLast(':').toIntOrNull() != null ->
                        spell.level == semanticPool.substringAfterLast(':').toInt()
                    else -> spell.level in 1..maximumSpellLevel(classId, classLevel)
                }
            }
            semanticPool == "metamagic" -> element.kind == RuleElementKind.METAMAGIC
            semanticPool.startsWith("eldritch-invocations") -> {
                val availableFeatures = progression.selectedFeatureIds +
                    provisionalSelections.flatMap { it.optionIds }
                element.kind == RuleElementKind.ELDRITCH_INVOCATION &&
                    element.classEligibility.any {
                        it.classId == CharacterClassId.WARLOCK &&
                            classLevel >= it.minimumLevel
                    } &&
                    invocationPrerequisitesMet(element.id, availableFeatures)
            }
            else -> true
        }
    }

    private fun maximumSpellLevel(classId: CharacterClassId, classLevel: Int): Int {
        val level = pack.classDefinition(classId).level(classLevel)
        if (level.pactSlotLevel > 0) return level.pactSlotLevel
        return level.spellSlots.indexOfLast { it > 0 } + 1
    }

    private fun invocationPrerequisitesMet(
        invocationId: String,
        availableFeatureIds: Collection<String>,
    ): Boolean {
        fun has(slug: String) = availableFeatureIds.any { it.endsWith(":$slug") }
        return when {
            invocationId.endsWith(":dono-del-protettore") -> has("patto-del-tomo")
            invocationId.endsWith(":investitura-del-signore-delle-catene") ->
                has("patto-della-catena")
            invocationId.endsWith(":lama-assetata") ||
                invocationId.endsWith(":punizione-occulta") ||
                invocationId.endsWith(":succhiavita") -> has("patto-della-lama")
            invocationId.endsWith(":lama-divoratrice") -> has("lama-assetata")
            else -> true
        }
    }

    private fun abilityModifier(score: Int): Int = Math.floorDiv(score - 10, 2)

    private fun resolvedBackgroundIncreases(
        background: BackgroundDefinition,
        requested: Map<Ability, Int>,
    ): Map<Ability, Int> = requested.filterValues { it != 0 }.ifEmpty {
        // Compatibilità con chiamanti precedenti al picker: +1 a tutte e tre è
        // una delle due distribuzioni ufficiali e non richiede una decisione arbitraria.
        background.abilityOptions.associateWith { 1 }
    }

    private fun featAbilityIncreaseRequirement(
        totalLevel: Int,
        featId: String,
        abilities: List<Ability>,
    ): ChoiceDefinition = ChoiceDefinition(
        id = "${pack.manifest.id}:choice:level:$totalLevel:" +
            "${featId.substringAfterLast(':')}:ability-increase",
        title = say(
            "Scegli la caratteristica da aumentare di 1",
            "Choose the ability to increase by 1",
        ),
        kind = ChoiceKind.ABILITY_SCORE_INCREASE,
        count = 1,
        optionIds = abilities.map { "${pack.manifest.id}:ability:${it.name.lowercase()}" },
    )

    private fun invocationCantripTarget(
        parentChoiceId: String,
        invocationId: String,
        mode: String,
    ): ChoiceDefinition {
        val slug = invocationId.substringAfterLast(':')
        return ChoiceDefinition(
            id = "$parentChoiceId:$slug:target",
            title = (pack.element(invocationId)?.name ?: say("Supplica", "Invocation")) +
                say(": scegli il trucchetto", ": choose the cantrip"),
            kind = ChoiceKind.FEATURE_TARGET,
            count = 1,
            poolId = "${pack.manifest.id}:pool:known-cantrips:warlock:$mode",
        )
    }

    private fun bonusCantripChoice(
        parentChoiceId: String,
        stableSlug: String,
        title: String,
        count: Int,
        listSlug: String,
    ): ChoiceDefinition = ChoiceDefinition(
        id = "$parentChoiceId:$stableSlug:cantrips",
        title = title + say(
            ": scegli ${if (count == 1) "un trucchetto" else "$count trucchetti"}",
            ": choose ${if (count == 1) "one cantrip" else "$count cantrips"}",
        ),
        kind = ChoiceKind.CANTRIP,
        count = count,
        poolId = "${pack.manifest.id}:pool:spells:$listSlug:cantrip",
    )

    private fun magicInitiateRequirements(fixedListId: String? = null): List<ChoiceDefinition> = listOf(
        ChoiceDefinition(
            id = "${pack.manifest.id}:choice:origin:magic-initiate:list",
            title = if (fixedListId == null) {
                say("Iniziato alla magia: scegli la lista", "Magic Initiate: choose the spell list")
            } else {
                say(
                    "Iniziato alla magia: lista concessa dal background",
                    "Magic Initiate: spell list granted by the background",
                )
            },
            kind = ChoiceKind.SPELL_LIST,
            count = 1,
            optionIds = fixedListId?.let(::listOf) ?: listOf("chierico", "druido", "mago").map {
                "${pack.manifest.id}:spell-list:$it"
            },
        ),
        ChoiceDefinition(
            id = "${pack.manifest.id}:choice:origin:magic-initiate:cantrips",
            title = say(
                "Iniziato alla magia: scegli due trucchetti dalla lista",
                "Magic Initiate: choose two cantrips from the list",
            ),
            kind = ChoiceKind.CANTRIP,
            count = 2,
            poolId = "${pack.manifest.id}:pool:spells:magic-initiate:cantrip",
        ),
        ChoiceDefinition(
            id = "${pack.manifest.id}:choice:origin:magic-initiate:spell",
            title = say(
                "Iniziato alla magia: scegli un incantesimo di 1º livello",
                "Magic Initiate: choose a level 1 spell",
            ),
            kind = ChoiceKind.PREPARED_SPELL,
            count = 1,
            poolId = "${pack.manifest.id}:pool:spells:magic-initiate:1",
        ),
        ChoiceDefinition(
            id = "${pack.manifest.id}:choice:origin:magic-initiate:ability",
            title = say(
                "Iniziato alla magia: scegli la caratteristica da incantatore",
                "Magic Initiate: choose the spellcasting ability",
            ),
            kind = ChoiceKind.SPELLCASTING_ABILITY,
            count = 1,
            optionIds = listOf(Ability.INTELLIGENCE, Ability.WISDOM, Ability.CHARISMA).map {
                "${pack.manifest.id}:ability:${it.name.lowercase()}"
            },
        ),
    )

    private fun validateMagicInitiate(
        progression: CharacterProgression,
        request: LevelUpRequest,
        requirements: List<ChoiceDefinition>,
        selectionsById: Map<String, ChoiceSelection>,
        issues: MutableList<ProgressionIssue>,
    ) {
        if (requirements.none { it.id.endsWith(":magic-initiate:list") }) return
        val listId = selectionsById.entries
            .firstOrNull { it.key.endsWith(":magic-initiate:list") }
            ?.value?.optionIds?.singleOrNull()
            ?: return
        val selectedClass = when (listId.substringAfterLast(':')) {
            "chierico" -> CharacterClassId.CLERIC
            "druido" -> CharacterClassId.DRUID
            "mago" -> CharacterClassId.WIZARD
            else -> return
        }
        val previousLists = progression.selections
            .filter { it.choiceId.endsWith(":magic-initiate:list") }
            .flatMapTo(mutableSetOf()) { it.optionIds }
        if (listId in previousLists) {
            issues += ProgressionIssue(
                "MAGIC_INITIATE_REPEAT",
                say(
                    "Iniziato alla magia può essere scelto di nuovo solo con una lista diversa.",
                    "Magic Initiate can be selected again only with a different spell list.",
                ),
            )
        }
        val spells = request.selections
            .filter {
                it.choiceId.endsWith(":magic-initiate:cantrips") ||
                    it.choiceId.endsWith(":magic-initiate:spell")
            }
            .flatMap { it.optionIds }
            .mapNotNull(pack::element)
        if (spells.any { element ->
                element.classEligibility.none { it.classId == selectedClass }
            }
        ) {
            issues += ProgressionIssue(
                "MAGIC_INITIATE_LIST",
                say(
                    "I trucchetti e l'incantesimo di Iniziato alla magia devono provenire dalla lista scelta.",
                    "Magic Initiate's cantrips and spell must come from the selected spell list.",
                ),
            )
        }
    }

    private fun abilityFromOptionId(optionId: String): Ability? {
        val slug = optionId.substringAfterLast(':')
        return pack.stats.map { it.id }.firstOrNull {
            it.name.lowercase() == slug ||
                it.value.substringAfterLast(':').replace('-', '_').lowercase() == slug
        }
    }

    private companion object {
        val repeatableOptionIds = setOf(
            "srd521-it:feat:origin:abile",
            "srd521-it:feat:origin:iniziato-alla-magia",
            "srd521-it:feat:general:aumento-punteggi-caratteristica",
            "srd521-it:feature:warlock:conoscenze-degli-antichi",
            "srd521-it:feature:warlock:deflagrazione-agonizzante",
            "srd521-it:feature:warlock:deflagrazione-respingente",
            "srd521-it:feature:warlock:lancia-occulta",
        )

        val featureChoiceKinds = setOf(
            ChoiceKind.CLASS_OPTION,
            ChoiceKind.FIGHTING_STYLE,
            ChoiceKind.WEAPON_MASTERY,
            ChoiceKind.METAMAGIC,
            ChoiceKind.ELDRITCH_INVOCATION,
        )

        val invocationDamageCantripIds = setOf(
            "srd521-it:spell:colpo-accurato",
            "srd521-it:spell:deflagrazione-occulta",
            "srd521-it:spell:spruzzo-velenoso",
            "srd521-it:spell:tocco-gelido",
        )

        val invocationRangeCantripIds = setOf(
            "srd521-it:spell:deflagrazione-occulta",
            "srd521-it:spell:spruzzo-velenoso",
        )
    }
}
