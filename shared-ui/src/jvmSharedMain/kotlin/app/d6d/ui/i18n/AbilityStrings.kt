package app.d6d.ui.i18n

/**
 * L'archivio delle capacita' del Compendio.
 *
 * E' un editor tecnico: dadi, bonus, tipi di danno, tiri salvezza. Il testo qui
 * dev'essere corto e preciso piu' che discorsivo — sono etichette di campi, e
 * ognuna sta accanto a un valore che deve restare leggibile.
 */
interface AbilityStrings {
    val backToAbilities: String
    val newAbility: String
    val title: String
    val subtitle: String
    val newAbilityPlus: String
    val noAbilityMatchesFilters: String
    val allFeminine: String
    val allClasses: String
    val srdContent: String
    val howItWorks: String
    val information: String
    val spell: String
    val cantrip: String
    val concentration: String
    val radius: String
    val targets: String
    val healing: String
    val damage: String
    val resolutionCaps: String
    val targetCaps: String
    val activeLabel: String
    val classCaps: String
    val srdReadOnly: String
    val noRulesText: String
    val appliedByApp: String
    val castingTime: String
    val activeHealingNote: String
    val srdHealingProtected: String
    val srdEntryProtected: String
    val duplicateAsCustom: String
    val duringTurn: String
    val tableChoice: String
    val passiveTraitHint: String
    val activeAbilityHint: String
    val healingIsActiveAutomated: String
    val attackBonus: String
    val spellOrCantrip: String
    val attackAbilityCaps: String
    val unspecified: String
    val manualResolution: String
    val restoresHitPoints: String
    val quantityCaps: String
    val diceCount: String
    val dynamicBonusCaps: String
    val bonusClassCaps: String
    val hitPoints: String
    val healingNoDamageNote: String
    val notApplicableHealing: String
    val dealsDamage: String
    val mainTypeCaps: String
    val extraTypeCaps: String
    val addDamageComponent: String
    val areaAndSave: String
    val notApplicableSingleTarget: String
    val areaEffect: String
    val savingThrowCaps: String
    val halfDamageOnSave: String
    val saveAbility: String
    val deleteAbilityTitle: String
    val spellcastingModifier: String
    val classLevel: String
    val healingSelfOnly: String
    val healingAllyOnly: String
    val healingSelfOrAlly: String
    val bonusAction: String
    val attackRoll: String
    val savingThrow: String

    fun deleteAbilityBody(name: String): String
    fun abilityInUse(name: String, usage: Int): String
    fun abilityId(id: String): String
    fun prerequisite(text: String): String
    fun healingSummary(amount: String, target: String): String
    fun areaOf(radius: String): String
    fun costSuffix(cost: String): String
    fun copyOf(name: String): String
    fun abilitiesCount(shown: Int, total: Int): String
    fun fromLevel(level: Int): String
    fun spellOfLevel(level: Int): String
    fun page(number: Int): String
    fun extraComponentCaps(index: Int): String
    val plusSpellcastingModifier: String
    fun plusClassLevel(className: String): String
    fun perLevelAbove(level: Int): String
}
