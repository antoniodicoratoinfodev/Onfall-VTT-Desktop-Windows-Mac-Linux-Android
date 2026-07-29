package app.d6d.sheet

import app.d6d.domain.combat.DamageType
import app.d6d.rules.character.Ability
import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.CharacterProgressionEngine
import app.d6d.rules.character.ChoiceKind
import app.d6d.rules.character.ClassDefinition
import app.d6d.rules.character.LevelUpRequest
import app.d6d.rules.character.ProgressionIssue
import app.d6d.rules.character.RuleEffect
import app.d6d.rules.character.RuleElementKind
import app.d6d.rules.character.RulesContentPack
import app.d6d.rules.character.SpellcastingKind
import app.d6d.rules.character.WeaponDefinition
import app.d6d.rules.character.WeaponProperty
import app.d6d.rules.character.WeaponReach

/**
 * Applica una creazione/avanzamento validato alla scheda senza duplicare le
 * tabelle del content pack nella UI.
 */
class GuidedCharacterService(val pack: RulesContentPack) {
    private val progressionEngine = CharacterProgressionEngine(pack)

    fun requirements(
        sheet: CharacterSheet,
        classId: CharacterClassId,
        provisionalSelections: List<app.d6d.rules.character.ChoiceSelection> = emptyList(),
    ) = progressionEngine.requirementsFor(sheet.progression, classId, provisionalSelections)

    fun validate(sheet: CharacterSheet, request: LevelUpRequest): app.d6d.rules.character.LevelUpValidation {
        val validation = progressionEngine.validateLevelUp(
            progression = sheet.progression,
            experiencePoints = sheet.experiencePoints,
            abilityScores = sheet.abilityScores,
            request = request,
        )
        val requirements = runCatching {
            progressionEngine.requirementsFor(sheet.progression, request.classId, request.selections)
        }.getOrDefault(emptyList())
        val byId = request.selections.associateBy { it.choiceId }
        val newlyProficientSkillIds = requirements
            .filter {
                it.kind == ChoiceKind.SKILL_PROFICIENCY ||
                    it.kind == ChoiceKind.SKILL_OR_TOOL_PROFICIENCY
            }
            .flatMapTo(mutableSetOf()) { byId[it.id]?.optionIds.orEmpty() }
        val invalidExpertise = requirements
            .filter { it.kind == ChoiceKind.EXPERTISE }
            .flatMap { byId[it.id]?.optionIds.orEmpty() }
            .filter { id ->
                val skill = id.toSkillOrNull() ?: return@filter true
                sheet.skillProficiencies[skill] != Proficiency.PROFICIENT &&
                    id !in newlyProficientSkillIds
            }
        val duplicateSkillProficiencies = requirements
            .filter {
                it.kind == ChoiceKind.SKILL_PROFICIENCY ||
                    it.kind == ChoiceKind.SKILL_OR_TOOL_PROFICIENCY
            }
            .flatMap { byId[it.id]?.optionIds.orEmpty() }
            .mapNotNull { id -> id.toSkillOrNull()?.let { id to it } }
            .filter { (_, skill) ->
                sheet.skillProficiencies[skill].let {
                    it != null && it != Proficiency.NONE
                }
            }
            .map { it.first }
        val duplicateToolProficiencies = requirements
            .filter {
                it.kind == ChoiceKind.TOOL_PROFICIENCY ||
                    it.kind == ChoiceKind.SKILL_OR_TOOL_PROFICIENCY
            }
            .flatMap { byId[it.id]?.optionIds.orEmpty() }
            .filter { it.toSkillOrNull() == null }
            .filter { sheet.toolProficiencies.containsListedEntry(optionLabel(it)) }
        val duplicateLanguages = requirements
            .filter { it.kind == ChoiceKind.LANGUAGE_PROFICIENCY }
            .flatMap { byId[it.id]?.optionIds.orEmpty() }
            .filter { sheet.languages.containsListedEntry(optionLabel(it)) }
        val localIssues = buildList {
            if (invalidExpertise.isNotEmpty()) {
                add(
                    ProgressionIssue(
                        "EXPERTISE_REQUIRES_PROFICIENCY",
                        "Maestria richiede una competenza nelle abilità già posseduta o appena scelta.",
                    ),
                )
            }
            if (duplicateSkillProficiencies.isNotEmpty()) {
                add(
                    ProgressionIssue(
                        "SKILL_ALREADY_PROFICIENT",
                        "Non puoi scegliere di nuovo una competenza nelle abilità già posseduta.",
                    ),
                )
            }
            if (duplicateToolProficiencies.isNotEmpty()) {
                add(
                    ProgressionIssue(
                        "TOOL_ALREADY_PROFICIENT",
                        "Non puoi scegliere di nuovo una competenza negli strumenti già posseduta.",
                    ),
                )
            }
            if (duplicateLanguages.isNotEmpty()) {
                add(
                    ProgressionIssue(
                        "LANGUAGE_ALREADY_KNOWN",
                        "Non puoi scegliere di nuovo una lingua già conosciuta.",
                    ),
                )
            }
        }
        return if (localIssues.isEmpty()) {
            validation
        } else {
            validation.copy(
                issues = validation.issues + localIssues,
            )
        }
    }

    fun fixedHitPointIncrease(sheet: CharacterSheet, classId: CharacterClassId): Int {
        val definition = pack.classDefinition(classId)
        val constitution = sheet.modifier(Ability.CONSTITUTION)
        return if (!sheet.progression.configured) {
            (definition.hitDieSides + constitution).coerceAtLeast(1)
        } else {
            (definition.fixedHitPointsPerLevel + constitution).coerceAtLeast(1)
        }
    }

    /**
     * Ricalcola gli effetti dei privilegi ottenuti e delle personalizzazioni.
     *
     * La progressione resta la fonte storica, mentre [CharacterSheet.excludedTraitIds]
     * e i collegamenti manuali nel Compendio fanno da overlay. In questo modo
     * togliere uno stile dalla scheda toglie anche il suo bonus, senza cancellare
     * la scelta dal registro del livello in cui era stata fatta.
     */
    fun withRefreshedEffects(
        sheet: CharacterSheet,
        abilityCatalog: List<CatalogAbility> = emptyList(),
    ): CharacterSheet {
        val catalogById = abilityCatalog.associateBy { it.id }
        val baseline = if (sheet.progression.configured) {
            progressionEngine.deriveEffects(sheet.progression)
        } else {
            emptyList()
        }

        fun nameOf(id: String): String? =
            catalogById[id]?.name ?: pack.element(id)?.name

        fun effectsOf(id: String): List<RuleEffect> =
            catalogById[id]?.effects ?: pack.element(id)?.effects.orEmpty()

        val excludedEffects = sheet.excludedTraitIds.flatMap(::effectsOf)
        val excludedSources = buildSet {
            sheet.excludedTraitIds.mapNotNullTo(this) { nameOf(it)?.effectKey() }
            excludedEffects.mapTo(this) { it.source.effectKey() }
        }
        val excludedGroups = excludedEffects
            .mapNotNullTo(mutableSetOf()) { it.group.takeIf(String::isNotBlank) }
        val retainedBaseline = baseline.filterNot { effect ->
            effect.source.effectKey() in excludedSources ||
                effect.group.isNotBlank() && effect.group in excludedGroups
        }

        val activeTraitIds = (
            sheet.progression.selectedFeatureIds +
                sheet.progression.featIds +
                sheet.abilityIds
            )
            .distinct()
            .filterNot { it in sheet.excludedTraitIds }
            .filter { id ->
                val kind = catalogById[id]?.category ?: pack.element(id)?.kind
                kind in editableTraitKinds
            }
        val refreshed = mergeEffects(
            retainedBaseline + activeTraitIds.flatMap(::effectsOf),
        )
        if (refreshed == sheet.progression.effects) return sheet
        return sheet.copy(progression = sheet.progression.copy(effects = refreshed))
    }

    /**
     * Toglie l'elenco di nomi che le versioni precedenti scrivevano nei campi di
     * testo di privilegi e talenti.
     *
     * Quei campi ora ospitano le note di chi gioca, mentre l'elenco arriva dalla
     * progressione: lasciarcelo mostrerebbe due volte le stesse voci. Si cancella
     * soltanto se il contenuto e' esattamente quello generato allora — una nota
     * scritta a mano, anche solo aggiunta in fondo, resta intatta.
     */
    fun withoutGeneratedFeatureText(sheet: CharacterSheet): CharacterSheet {
        if (!sheet.progression.configured) return sheet
        fun generated(ids: List<String>) = ids.joinToString("\n") {
            "• ${pack.element(it)?.name ?: optionLabel(it)}"
        }
        return sheet.copy(
            classFeatures = sheet.classFeatures
                .takeIf { it.trim() != generated(sheet.progression.selectedFeatureIds).trim() }
                .orEmpty(),
            feats = sheet.feats
                .takeIf { it.trim() != generated(sheet.progression.featIds).trim() }
                .orEmpty(),
        )
    }

    fun advance(sheet: CharacterSheet, request: LevelUpRequest): CharacterSheet {
        val wasConfigured = sheet.progression.configured
        val oldClassLevel = sheet.progression.levelIn(request.classId)
        val oldConstitutionModifier = sheet.modifier(Ability.CONSTITUTION)
        val progressed = progressionEngine.applyLevelUp(
            progression = sheet.progression,
            experiencePoints = sheet.experiencePoints,
            abilityScores = sheet.abilityScores,
            request = request,
        )
        val abilityCap = if (
            request.selections
                .flatMap { it.optionIds }
                .mapNotNull(pack::element)
                .any { it.kind == RuleElementKind.EPIC_BOON_FEAT }
        ) {
            30
        } else {
            20
        }
        val abilityScores = sheet.abilityScores.toMutableMap().apply {
            request.abilityScoreIncreases.forEach { (ability, increase) ->
                this[ability] = (getOrDefault(ability, 10) + increase).coerceAtMost(abilityCap)
            }
        }
        val newConstitutionModifier = abilityModifier(abilityScores[Ability.CONSTITUTION] ?: 10)
        val constitutionHitPointAdjustment =
            (newConstitutionModifier - oldConstitutionModifier) * progressed.totalLevel
        val hitPointIncrease = request.hitPointIncrease + constitutionHitPointAdjustment
        val definition = pack.classDefinition(request.classId)
        val requirements = progressionEngine.requirementsFor(
            sheet.progression,
            request.classId,
            request.selections,
        )
        val choicesById = requirements.associateBy { it.id }

        val skills = sheet.skillProficiencies.toMutableMap()
        val selectedTools = mutableListOf<String>()
        val selectedLanguages = mutableListOf<String>()
        val selectedWeapons = mutableListOf<WeaponDefinition>()
        request.selections.forEach { selection ->
            when (choicesById[selection.choiceId]?.kind) {
                ChoiceKind.STARTING_WEAPON ->
                    selectedWeapons += selection.optionIds.mapNotNull(pack::weapon)
                ChoiceKind.SKILL_PROFICIENCY -> selection.optionIds.forEach { id ->
                    id.toSkillOrNull()?.let { skills[it] = Proficiency.PROFICIENT }
                }
                ChoiceKind.EXPERTISE -> selection.optionIds.forEach { id ->
                    id.toSkillOrNull()?.let { skills[it] = Proficiency.EXPERTISE }
                }
                ChoiceKind.SKILL_OR_TOOL_PROFICIENCY -> selection.optionIds.forEach { id ->
                    val skill = id.toSkillOrNull()
                    if (skill != null) {
                        skills[skill] = Proficiency.PROFICIENT
                    } else {
                        selectedTools += optionLabel(id)
                    }
                }
                ChoiceKind.TOOL_PROFICIENCY -> selectedTools += selection.optionIds.map(::optionLabel)
                ChoiceKind.LANGUAGE_PROFICIENCY ->
                    selectedLanguages += selection.optionIds.map(::optionLabel)
                else -> Unit
            }
        }

        val isFirstCharacterLevel = !wasConfigured
        val cumulativeClassLevels = progressed.classLevels.flatMap { classLevel ->
            pack.classDefinition(classLevel.classId).levels.take(classLevel.level)
        }
        val baseSaves = if (isFirstCharacterLevel) {
            sheet.saveProficiencies + definition.savingThrowProficiencies.associateWith {
                Proficiency.PROFICIENT
            }
        } else {
            sheet.saveProficiencies
        }
        val saves = baseSaves +
            cumulativeClassLevels
                .flatMap { it.savingThrowProficiencyGrants }
                .associateWith {
                    Proficiency.PROFICIENT
                }
        val baseArmorGrant = when {
            isFirstCharacterLevel -> definition.armorTraining
            oldClassLevel == 0 -> definition.multiclassArmorTraining
            else -> null
        }
        val selectedRuleElements = request.selections
            .flatMap { it.optionIds }
            .mapNotNull(pack::element)
        val armor = (listOfNotNull(baseArmorGrant) +
            selectedRuleElements.mapNotNull { it.armorTrainingGrant })
            .fold(sheet.armorTraining) { current, grant ->
                current.copy(
                    light = current.light || grant.light,
                    medium = current.medium || grant.medium,
                    heavy = current.heavy || grant.heavy,
                    shields = current.shields || grant.shields,
                )
            }
        val weaponGrant = when {
            isFirstCharacterLevel -> definition.weaponTraining
            oldClassLevel == 0 -> definition.multiclassWeaponTraining
            else -> ""
        }
        val weaponTraining = appendDistinctText(
            sheet.weaponProficiencies,
            (
                listOf(weaponGrant) +
                    selectedRuleElements.map { it.weaponTrainingGrant }
                ).filter { it.isNotBlank() }.distinct().joinToString(", "),
        )
        val toolTraining = appendDistinctText(
            sheet.toolProficiencies,
            selectedTools.joinToString(", "),
        )
        val languages = appendDistinctText(
            sheet.languages,
            (
                cumulativeClassLevels.flatMap { it.languageProficiencyGrants } +
                    selectedLanguages
                ).distinct().joinToString(", "),
        )

        // Un'arma scelta alla creazione diventa subito una riga della scheda: da li'
        // la proiezione da combattimento la trasforma nella capacita' con cui si
        // attacca, senza che l'utente debba ricopiarne i numeri a mano.
        val weaponRows = selectedWeapons.map {
            it.toWeaponEntry(
                abilityScores = abilityScores,
                proficiencyBonus = proficiencyBonusForLevel(progressed.totalLevel),
            )
        }
        val weapons = sheet.weapons +
            weaponRows.filterNot { row -> sheet.weapons.any { it.name == row.name } }

        val classNames = progressed.classLevels.joinToString(" / ") {
            "${pack.classDefinition(it.classId).name} ${it.level}"
        }
        val subclassNames = progressed.subclasses.joinToString(" / ") {
            pack.element(it.subclassId)?.name ?: optionLabel(it.subclassId)
        }
        val spellcasting = deriveSpellcasting(sheet.spellcasting, progressed)
        // L'azione di Magia dichiara che il personaggio sa lanciare incantesimi:
        // darla anche a chi non ne ha nemmeno uno non indicherebbe piu' nulla.
        val commonActions = pack.elements
            .filter { it.kind == RuleElementKind.COMMON_ACTION }
            .filterNot { it.id == MAGIC_ACTION_ID && spellcasting == null }
            .map { it.id }
        val previouslyGeneratedIds = buildSet {
            addAll(pack.elements.filter { it.kind == RuleElementKind.COMMON_ACTION }.map { it.id })
            addAll(sheet.progression.selectedFeatureIds)
            addAll(sheet.progression.featIds)
            addAll(sheet.progression.knownCantripIds)
            addAll(sheet.progression.preparedSpellIds)
            addAll(sheet.progression.alwaysPreparedSpellIds)
        }
        // Capacità e tratti aggiunti a mano dalla scheda non devono sparire al
        // passaggio di livello: si sostituisce soltanto la parte generata.
        val manuallyLinkedAbilityIds = sheet.abilityIds.filterNot { it in previouslyGeneratedIds }
        val selectedAbilityIds = (
            commonActions +
                manuallyLinkedAbilityIds +
                progressed.selectedFeatureIds +
                progressed.featIds +
                progressed.knownCantripIds +
                progressed.preparedSpellIds +
                progressed.alwaysPreparedSpellIds
            )
            .distinct()
            .filterNot { it in sheet.excludedTraitIds }
        val hitDicePools = deriveHitDicePools(sheet, progressed)
        val newClassLevel = progressed.levelIn(request.classId)
        val draconicResilienceActive =
            request.classId == CharacterClassId.SORCERER &&
                progressed.subclassFor(CharacterClassId.SORCERER)
                    ?.endsWith(":stregoneria-draconica") == true
        val draconicHitPointIncrease = when {
            !draconicResilienceActive -> 0
            newClassLevel == 3 -> 3
            newClassLevel > 3 -> 1
            else -> 0
        }
        val totalHitPointIncrease = hitPointIncrease + draconicHitPointIncrease
        val newMaximum = if (isFirstCharacterLevel) {
            totalHitPointIncrease.coerceAtLeast(1)
        } else {
            (sheet.maxHitPoints + totalHitPointIncrease).coerceAtLeast(1)
        }
        val maximumDelta = newMaximum - sheet.maxHitPoints
        val mayAdoptClassArmorFormula =
            sheet.armorClassMethod == ArmorClassMethod.UNARMORED ||
                (
                    sheet.armorClassMethod == ArmorClassMethod.MANUAL_TOTAL &&
                        sheet.armorClass == 10 &&
                        sheet.armorClassOverride == null
                    )
        val armorClassMethod = if (mayAdoptClassArmorFormula) {
            when {
                isFirstCharacterLevel && request.classId == CharacterClassId.BARBARIAN ->
                    ArmorClassMethod.BARBARIAN_UNARMORED
                isFirstCharacterLevel && request.classId == CharacterClassId.MONK ->
                    ArmorClassMethod.MONK_UNARMORED
                draconicResilienceActive && newClassLevel >= 3 ->
                    ArmorClassMethod.DRACONIC_RESILIENCE
                else -> sheet.armorClassMethod
            }
        } else {
            sheet.armorClassMethod
        }
        val fiendResistance = request.selections
            .firstOrNull { it.choiceId.endsWith(":resilienza-immonda") }
            ?.optionIds
            ?.singleOrNull()
            ?.substringAfterLast(':')
            ?.toDamageTypeOrNull()

        return withRefreshedEffects(
            sheet.copy(
                className = classNames,
                subclass = subclassNames,
                level = progressed.totalLevel,
                progression = progressed,
                maxHitPoints = newMaximum,
                currentHitPoints = if (isFirstCharacterLevel) {
                    newMaximum
                } else {
                    (sheet.currentHitPoints + maximumDelta).coerceIn(0, newMaximum)
                },
                hitDiceMax = progressed.totalLevel,
                hitDiceSpent = hitDicePools.sumOf { it.spent },
                hitDieSides = definition.hitDieSides,
                hitDicePools = hitDicePools,
                weapons = weapons,
                abilityScores = abilityScores,
                saveProficiencies = saves,
                skillProficiencies = skills,
                armorTraining = armor,
                armorClassMethod = armorClassMethod,
                fiendishResilienceDamageType =
                    fiendResistance ?: sheet.fiendishResilienceDamageType,
                weaponProficiencies = weaponTraining,
                toolProficiencies = toolTraining,
                languages = languages,
                abilityIds = selectedAbilityIds,
                // Privilegi e talenti non vengono piu' riscritti come elenco di nomi:
                // la progressione li conosce gia' e la scheda li mostra da li', con il
                // testo del documento. Quei campi restano all'utente per le sue note,
                // che un passaggio di livello non deve cancellare.
                spellcasting = spellcasting,
            ),
        )
    }

    private fun deriveSpellcasting(
        previous: Spellcasting?,
        progression: app.d6d.rules.character.CharacterProgression,
    ): Spellcasting? {
        val castingClasses = progression.classLevels.mapNotNull { classLevel ->
            pack.classDefinition(classLevel.classId)
                .takeIf { it.spellcastingKind != SpellcastingKind.NONE }
                ?.let { Triple(classLevel, it, it.level(classLevel.level)) }
        }
        val magicInitiateAbility = progression.selections
            .asReversed()
            .firstOrNull { it.choiceId.endsWith(":magic-initiate:ability") }
            ?.optionIds
            ?.singleOrNull()
            ?.substringAfterLast(':')
            ?.let { slug -> Ability.entries.firstOrNull { it.name.lowercase() == slug } }
        val spellIds = (
            progression.knownCantripIds +
                progression.preparedSpellIds +
                progression.alwaysPreparedSpellIds +
                progression.spellbookSpellIds
            ).distinct()
        if (castingClasses.isEmpty() && spellIds.isEmpty()) return null

        val abilities = castingClasses.associate { (state, definition) ->
            state.classId to checkNotNull(definition.spellcastingAbility)
        }
        val standardCasters = castingClasses.filter { (_, definition) ->
            definition.spellcastingKind != SpellcastingKind.PACT_MAGIC
        }
        val standardSlots = when {
            standardCasters.isEmpty() -> List(9) { 0 }
            standardCasters.size == 1 -> standardCasters.single().third.spellSlots.padSlots()
            else -> {
                val effectiveCasterLevel = standardCasters.sumOf { (state, definition) ->
                    when (definition.spellcastingKind) {
                        SpellcastingKind.HALF_CASTER -> (state.level + 1) / 2
                        else -> state.level
                    }
                }.coerceIn(1, 20)
                MULTICLASS_SLOTS[effectiveCasterLevel - 1]
            }
        }
        val existingSlots = previous?.slots.orEmpty().associateBy { it.level }
        val slots = standardSlots.mapIndexed { index, total ->
            val level = index + 1
            SpellSlot(level, total, existingSlots[level]?.spent?.coerceAtMost(total) ?: 0)
        }
        val pact = castingClasses.firstOrNull { (_, definition) ->
            definition.spellcastingKind == SpellcastingKind.PACT_MAGIC
        }?.third
        val pactSlots = pact?.let {
            SpellSlot(
                level = it.pactSlotLevel,
                total = it.pactSlotCount,
                spent = previous?.pactSlots?.spent?.coerceAtMost(it.pactSlotCount) ?: 0,
            )
        }
        val magicInitiateSpellIds = progression.selections
            .filter {
                it.choiceId.endsWith(":magic-initiate:cantrips") ||
                    it.choiceId.endsWith(":magic-initiate:spell")
            }
            .flatMapTo(mutableSetOf()) { it.optionIds }
        val spells = spellIds.mapNotNull { id ->
            pack.element(id)?.let { element ->
                val spell = element.spell ?: return@let null
                SpellEntry(
                    level = spell.level,
                    name = element.name,
                    castingTime = spell.castingTime,
                    range = spell.range,
                    concentration = spell.concentration,
                    ritual = spell.ritual,
                    materials = "M" in spell.components,
                    note = when {
                        id in magicInitiateSpellIds -> "Iniziato alla magia"
                        id in progression.alwaysPreparedSpellIds -> "Sempre preparato"
                        id in progression.spellbookSpellIds && id !in progression.preparedSpellIds ->
                            "Nel libro degli incantesimi"
                        else -> ""
                    },
                )
            }
        }
        return Spellcasting(
            ability = abilities.values.firstOrNull() ?: magicInitiateAbility ?: Ability.INTELLIGENCE,
            abilitiesByClass = abilities,
            slots = slots,
            pactSlots = pactSlots,
            spells = spells,
        )
    }

    private fun deriveHitDicePools(
        previous: CharacterSheet,
        progression: app.d6d.rules.character.CharacterProgression,
    ): List<HitDicePool> {
        val oldPools = previous.hitDicePools.associateBy { it.dieSides }
        return progression.classLevels
            .groupingBy { pack.classDefinition(it.classId).hitDieSides }
            .fold(0) { total, state -> total + state.level }
            .map { (sides, total) ->
                HitDicePool(sides, total, oldPools[sides]?.spent?.coerceAtMost(total) ?: 0)
            }
            .sortedBy { it.dieSides }
    }
}

/**
 * Riga "Armi e trucchetti da combattimento" per un'arma dello SRD.
 *
 * La caratteristica segue il regolamento: la Destrezza per le armi a distanza,
 * la migliore fra Forza e Destrezza per quelle accurate, la Forza altrimenti.
 * Il personaggio è competente in quello che la sua classe gli insegna a usare,
 * quindi il bonus di competenza rientra sempre nel tiro per colpire.
 */
fun WeaponDefinition.toWeaponEntry(
    abilityScores: Map<Ability, Int>,
    proficiencyBonus: Int,
): WeaponEntry {
    fun modifierOf(ability: Ability) = abilityModifier(abilityScores[ability] ?: 10)
    val strength = modifierOf(Ability.STRENGTH)
    val dexterity = modifierOf(Ability.DEXTERITY)
    val modifier = when {
        reach == WeaponReach.RANGED -> dexterity
        WeaponProperty.FINESSE in properties -> maxOf(strength, dexterity)
        else -> strength
    }
    return WeaponEntry(
        name = name,
        attackBonus = proficiencyBonus + modifier,
        diceCount = diceCount,
        diceSides = diceSides,
        damageModifier = modifier,
        damageType = damageType,
        rangeFeet = attackRangeFeet,
        note = buildString {
            append("Padronanza: ").append(mastery)
            if (versatileDiceSides > 0) {
                append(" · a due mani 1d").append(versatileDiceSides)
            }
            if (reach == WeaponReach.RANGED || WeaponProperty.THROWN in properties) {
                append(" · gittata ").append(normalRangeFeet).append('/').append(longRangeFeet)
            }
        },
    )
}

/** Azione comune che segnala la capacità di lanciare incantesimi. */
private const val MAGIC_ACTION_ID = "srd521-it:action:magia"

private fun List<Int>.padSlots(): List<Int> = (this + List(9) { 0 }).take(9)

private fun String.toSkillOrNull(): Skill? {
    val slug = substringAfterLast(':').replace('-', '_')
    return Skill.entries.firstOrNull {
        it.name.lowercase() == slug ||
            it.italianLabel.toRulesSlug().replace('-', '_') == slug
    }
}

private fun optionLabel(id: String): String =
    id.substringAfterLast(':')
        .replace('-', ' ')
        .replace('_', ' ')
        .replaceFirstChar { it.uppercase() }

private fun String.toRulesSlug(): String =
    java.text.Normalizer.normalize(lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')

private fun String.toDamageTypeOrNull(): DamageType? = when (this) {
    "acido" -> DamageType.ACID
    "contundente" -> DamageType.BLUDGEONING
    "freddo" -> DamageType.COLD
    "fuoco" -> DamageType.FIRE
    "fulmine" -> DamageType.LIGHTNING
    "necrotico" -> DamageType.NECROTIC
    "perforante" -> DamageType.PIERCING
    "veleno" -> DamageType.POISON
    "psichico" -> DamageType.PSYCHIC
    "radioso" -> DamageType.RADIANT
    "tagliente" -> DamageType.SLASHING
    "tuono" -> DamageType.THUNDER
    else -> null
}

private fun appendDistinctText(existing: String, addition: String): String =
    listOf(existing, addition)
        .flatMap { it.split(',', ';', '\n') }
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
        .joinToString(", ")

private fun String.containsListedEntry(label: String): Boolean =
    split(',', ';', '\n').any { it.trim().equals(label, ignoreCase = true) }

private val editableTraitKinds = setOf(
    RuleElementKind.CLASS_FEATURE,
    RuleElementKind.SUBCLASS_FEATURE,
    RuleElementKind.CLASS_OPTION,
    RuleElementKind.METAMAGIC,
    RuleElementKind.ELDRITCH_INVOCATION,
    RuleElementKind.ORIGIN_FEAT,
    RuleElementKind.GENERAL_FEAT,
    RuleElementKind.FIGHTING_STYLE_FEAT,
    RuleElementKind.EPIC_BOON_FEAT,
)

private fun String.effectKey(): String = trim().lowercase()

/** Applica la stessa regola degli effetti progressivi usata dal motore. */
private fun mergeEffects(effects: List<RuleEffect>): List<RuleEffect> {
    val distinct = effects.distinct()
    val (grouped, single) = distinct.partition { it.group.isNotBlank() }
    return single + grouped
        .groupBy { it.group }
        .map { (_, sameGroup) -> sameGroup.maxBy { it.amount } }
}

/** Tabella Incantatore multiclasse SRD 5.2.1, livelli effettivi 1–20. */
private val MULTICLASS_SLOTS = listOf(
    listOf(2, 0, 0, 0, 0, 0, 0, 0, 0),
    listOf(3, 0, 0, 0, 0, 0, 0, 0, 0),
    listOf(4, 2, 0, 0, 0, 0, 0, 0, 0),
    listOf(4, 3, 0, 0, 0, 0, 0, 0, 0),
    listOf(4, 3, 2, 0, 0, 0, 0, 0, 0),
    listOf(4, 3, 3, 0, 0, 0, 0, 0, 0),
    listOf(4, 3, 3, 1, 0, 0, 0, 0, 0),
    listOf(4, 3, 3, 2, 0, 0, 0, 0, 0),
    listOf(4, 3, 3, 3, 1, 0, 0, 0, 0),
    listOf(4, 3, 3, 3, 2, 0, 0, 0, 0),
    listOf(4, 3, 3, 3, 2, 1, 0, 0, 0),
    listOf(4, 3, 3, 3, 2, 1, 0, 0, 0),
    listOf(4, 3, 3, 3, 2, 1, 1, 0, 0),
    listOf(4, 3, 3, 3, 2, 1, 1, 0, 0),
    listOf(4, 3, 3, 3, 2, 1, 1, 1, 0),
    listOf(4, 3, 3, 3, 2, 1, 1, 1, 0),
    listOf(4, 3, 3, 3, 2, 1, 1, 1, 1),
    listOf(4, 3, 3, 3, 3, 1, 1, 1, 1),
    listOf(4, 3, 3, 3, 3, 2, 1, 1, 1),
    listOf(4, 3, 3, 3, 3, 2, 2, 1, 1),
)
