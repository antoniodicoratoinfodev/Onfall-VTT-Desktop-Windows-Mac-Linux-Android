package app.d6d.ui.i18n

/**
 * La scheda di personaggio e lo stat block delle creature.
 *
 * E' il fascicolo del Compendio: l'editor completo, la sezione dell'armatura, la
 * progressione SRD, la procedura guidata di creazione e i messaggi che il view
 * model restituisce dopo un salvataggio.
 *
 * Le sigle di gioco sono un caso a parte e vanno lette come traduzione, non come
 * abbreviazione: «PF» diventa «HP» e «CA» diventa «AC» perche' sono le sigle che
 * ciascun manuale stampa, non le iniziali delle parole tradotte.
 */
interface SheetStrings {

    // --- azioni comuni sulla scheda ------------------------------------------

    val saveSheet: String
    val saveStatBlock: String
    val unsavedChanges: String
    val deleteSheetTitle: String
    val deleteStatBlockTitle: String
    fun deleteSheetBody(name: String): String
    fun deleteStatBlockBody(name: String): String

    // --- intestazione e valori principali ------------------------------------

    val characterName: String
    val alignment: String
    val failures: String
    val weapons: String
    val tools: String
    val monsters: String
    val score: String
    val initiative: String
    val feats: String
    val spells: String
    val appearance: String
    val equipment: String
    val header: String
    val abilitiesLabel: String
    val actions: String
    val reactions: String
    val proficient: String
    val expertise: String
    val fightingStyle: String
    val activeLabel: String
    val currentArmorClass: String
    val hitPoints: String
    val hitDice: String
    val deathSaves: String
    val proficiencyBonus: String
    val heroicInspiration: String
    val available: String
    val cantripsHeading: String
    val notAvailable: String
    val trainingAndProficiencies: String
    val armorTraining: String
    val savingThrow: String
    val disadvantageShort: String
    val passivePerception: String
    val modifier: String
    val heal: String
    val stabilize: String
    val conditions: String
    fun effectiveSpeed(distance: String): String

    // --- armi e capacita' -----------------------------------------------------

    val weaponsAndCombatAbilities: String
    val attackBonusOrDc: String
    val damageAndType: String
    val addWeapon: String
    val addAbility: String
    val fixedDamage: String
    val bonusAction: String
    val spellOrCantrip: String
    val attackAbilityCaps: String
    val spellcastingAbility: String
    val toClassify: String
    val unclassifiedEntryHint: String
    val areaDamageWithSave: String
    val savingThrowCaps: String
    val halfDamageOnSave: String
    val fromAbilityCatalog: String
    val addAbilityTitle: String
    val emptyAbilityCatalog: String
    val legendaryAction: String
    val noCost: String
    fun areaOf(radius: String): String
    fun abilityMissingFromCatalog(abilityId: String): String

    // --- privilegi, talenti e note --------------------------------------------

    val classFeatures: String
    val sectionFeatures: String
    val sectionFeats: String
    val noFeaturesRecorded: String
    val manageFeatures: String
    val yourNotes: String
    val speciesTraits: String
    val noFeatsRecorded: String
    val manageFeats: String
    val manageFeaturesTitle: String
    val manageFeatsTitle: String
    val searchByNameRuleOrPrerequisite: String
    val allFeminine: String
    val onlyCompatibleWithClassAndLevel: String
    val noEntryMatchesFilters: String
    val outOfRequirements: String
    fun chooseFromCompendium(count: Int): String
    fun sourcePage(page: Int): String
    fun classAndMinimumLevel(className: String, level: Int): String
    fun prerequisite(text: String): String

    // --- incantesimi -------------------------------------------------------------

    val castsSpells: String
    val castingBlockedByArmor: String
    val saveDc: String
    val attackBonus: String
    val spellSlots: String
    val selectedCantripsAndSpells: String
    val concentrationInitial: String
    val ritualInitial: String
    val shortOrLongRest: String
    val weaponMasteries: String
    val backgroundAndTraits: String
    val magicItemAttunement: String
    fun levelHeading(level: Int): String
    fun slotLevel(level: Int): String
    fun slotTotal(total: Int): String
    fun pactSlotLevel(level: Int): String
    fun spellcastingAbilityOf(className: String, abbreviation: String): String

    // --- classe armatura -----------------------------------------------------------

    val armorClassCalculation: String
    val baseMethodCaps: String
    val dexterityContributionCaps: String
    val wornArmorCaps: String
    val armorVariantCaps: String
    val armorClassModifiersCaps: String
    val noArmor: String
    val manualFinalArmorClass: String
    val manualFinalHint: String
    val elvenChainManualHint: String
    val baseStartingValue: String
    val armorStrengthRequirement: String
    val stealthDisadvantage: String
    val mithralNote: String
    val elvenChainNote: String
    val shieldNotEquipped: String
    val shieldAlreadyInManual: String
    val shieldEquipped: String
    val shieldWithoutProficiency: String
    val shieldActionNote: String
    val shieldNeedsProficiency: String
    val manualTotalNote: String
    val noOtherModifier: String
    val addModifier: String
    val newModifier: String
    val unnamedModifier: String
    val bonusOrPenalty: String
    val stealthDisadvantageWarning: String
    fun overrideActive(value: Int): String
    fun restoreCalculatedArmorClass(value: Int): String
    fun baseArmorClass(value: Int): String
    fun armorClassModifiers(signed: String): String
    fun calculatedArmorClass(value: Int): String
    fun overrideValue(value: Int): String
    fun missingArmorProficiency(category: String): String
    fun strengthBelowRequirement(score: Int, required: Int, penalty: String): String
    fun donDoffMinutes(don: Int, doff: Int): String
    fun effectRow(source: String, text: String): String

    /**
     * Un effetto numerico detto a parole: «+1 alla Classe Armatura con
     * un'armatura indossata».
     *
     * Sono tre funzioni e non una perche' l'italiano cambia preposizione col
     * bersaglio — *alla* Classe Armatura, *ai* Tiri per colpire, *di* velocita' —
     * mentre l'inglese usa sempre `to`. Una funzione sola costringerebbe a
     * infilare la preposizione nell'etichetta del bersaglio, che serve anche
     * altrove e da sola.
     */
    fun effectOnArmorClass(amount: String, condition: String): String
    fun effectOnSpeed(distance: String, condition: String): String
    fun effectOnAttack(amount: String, target: String, condition: String): String
    fun manualFinalArmorClassIs(value: Int): String
    fun baseWithFullDexterity(base: Int, dexterity: String): String
    fun baseWithCappedDexterity(base: Int, contribution: String): String
    fun baseWithoutDexterity(base: Int): String
    fun plusSecondaryAbility(abbreviation: String, signed: String): String
    fun equalsBaseArmorClass(detail: String, secondary: String, base: Int): String
    fun overrideSuffix(value: Int): String
    fun shieldRow(signed: String): String
    fun armorRuleRow(rule: String, signed: String): String
    fun equalsCalculatedArmorClass(value: Int): String
    fun currentArmorClassOverride(value: Int): String

    // --- progressione SRD ------------------------------------------------------------

    val srdCreationTitle: String
    val srdCreationBody: String
    val startGuidedCreation: String
    val srdProgressionTitle: String
    val classResourcesCaps: String
    val shortRest: String
    val longRest: String
    val longRestAndSwapForm: String
    val swapKnownFormTitle: String
    val swapKnownFormBody: String
    val formToForgetCaps: String
    val newFormCaps: String
    val finishRestAndSwap: String
    fun classAndLevel(className: String, level: Int): String
    fun proficiencyBonusIs(signed: String): String
    fun experiencePoints(points: Int): String
    fun levelUpAvailable(level: Int): String
    fun levelUpTo(level: Int): String
    fun nextLevelAt(threshold: Int, missing: Int?): String
    fun resourcePool(remaining: Int, maximum: Int, recovery: String): String
    fun dieSuffix(sides: Int): String
    fun formSummary(name: String, summary: String): String

    // --- procedura guidata --------------------------------------------------------------

    val guidedCreationTitle: String
    val srdLevelUpTitle: String
    val chooseExactlyForFirstLevel: String
    val multiclassNote: String
    val newLevelHitPoints: String
    val firstLevelUsesMaximum: String
    val fixedValue: String
    val rollTheDie: String
    val dieResultPlusConstitution: String
    val createCharacter: String
    val applyLevel: String
    val noOptionForClassAndLevel: String
    val abilityScoreIncreaseTitle: String
    val abilityScoreIncreaseBody: String
    fun levelAndExperience(level: Int, points: Int): String
    fun choicesMade(selected: Int, required: Int): String
    fun searchAmongOptions(count: Int): String
    fun fixedHitPoints(amount: Int): String
    fun applied(text: String): String
    fun backgroundAbilityScoresTitle(background: String): String
    val backgroundAbilityScoresBody: String
    fun assignedOutOf(assigned: Int, total: Int): String

    // --- stat block delle creature ----------------------------------------------------

    val unnamedCreature: String
    val defenceInitiativeHitPoints: String
    val armorClass: String
    val initiativeModifier: String
    val initiativeLabel: String
    val perceptionLabel: String
    val challengeRatingShort: String
    val selected: String
    val marked: String
    val staticScore: String
    val averageHitPoints: String
    val diceCount: String
    val onFoot: String
    val canHover: String
    val typedDefences: String
    val damageImmunities: String
    val conditionImmunities: String
    val sensesLanguagesGear: String
    val gear: String
    val challengeRating: String
    val baseXp: String
    val lairXp: String
    val treasureTheme: String
    val bonusActions: String
    val legendaryActions: String
    val makeAttackExecutable: String
    val attackBonusShort: String
    val addEntry: String
    val vulnerabilities: String
    fun passive(value: Int): String
    fun sectionCount(title: String, count: Int): String
    fun challengeRatingSummary(rating: String, xp: Long, lairXp: Long?, proficiency: String): String
    fun initiativeSummary(signed: String, score: Int): String

    // --- campi e stati dei componenti ---------------------------------------------------

    val notProficient: String
    val notSelected: String
    val notMarked: String
    fun labelWithUnit(label: String, unit: String): String

    // --- messaggi del view model -----------------------------------------------------------

    val archiveLoaded: String
    val newSheetFillAndSave: String
    val sheetSaved: String
    val sheetDeleted: String
    val sheetUpdatedFromBattle: String
    val statBlockUpdatedFromBattle: String
    val templateSheetsRestored: String
    val longRestResourcesRecovered: String
    val shortRestResourcesRecovered: String
    val cannotSwapKnownForm: String
    val srdCharacterCreated: String
    val abilitySaved: String
    val automaticAbilityMustStayActive: String
    val abilityNotInCompendium: String
    val bundledSrdReadOnly: String
    val bundledSrdCannotBeDeleted: String
    val abilityDeleted: String
    val unsavedChangesPrompt: String
    val sheetNotFound: String
    fun diskError(detail: String): String
    fun invalidSheet(detail: String): String
    fun longRestFormSwapped(oldName: String, newName: String): String
    fun levelApplied(level: Int): String
    fun entryNotInSection(section: String): String
    fun abilityAddedToSheet(name: String): String
    fun abilityRemovedFromSheet(name: String): String
    fun abilityBecamePassive(name: String): String
    fun abilityBecameActive(name: String): String
    fun cannotDeleteAbilityInUse(usedBy: String): String
}
