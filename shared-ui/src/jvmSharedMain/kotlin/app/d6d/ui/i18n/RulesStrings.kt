package app.d6d.ui.i18n

/** Testi del catalogo e dell'editor dei regolamenti. */
interface RulesStrings {
    val title: String
    val subtitle: String
    val standard: String
    val homebrew: String
    val imported: String
    val sessionLocal: String
    val disabled: String
    val active: String
    val allRulesets: String
    val readOnly: String
    val editableDraft: String
    val published: String
    val searchPlaceholder: String
    val noResults: String
    val ruleset: String
    val rule: String
    val rulesetDetails: String
    val descriptionPlaceholder: String
    val saveRulesetDetails: String
    val source: String
    val automation: String
    val attributes: String
    val ruleType: String
    val enabled: String
    val attributeKey: String
    val attributeValue: String
    val tagsPlaceholder: String
    val fullyAutomated: String
    val assisted: String
    val manual: String
    val fork: String
    val editInDraft: String
    val forkHint: String
    val newRevisionDraft: String
    val newRevisionHint: String
    val saveDraft: String
    val restoreFromBase: String
    val removeAddedRule: String
    val publish: String
    val versionLabel: String
    val addCustomRule: String
    val customRuleName: String
    val customRuleDescription: String
    val runtimeTitle: String
    val runtimeHint: String
    val criticalThreshold: String
    val naturalOneMisses: String
    val maximumExhaustion: String
    val exhaustionD20Penalty: String
    val exhaustionSpeedPenalty: String
    val proficiencyBase: String
    val proficiencyInterval: String
    val proficiencyMaximum: String
    val applyToSession: String
    val noOpenSession: String
    val applyPausesSession: String
    val cannotApplyResolved: String
    val cpuManualFallback: String
    val currentSessionUses: String
    val chooseForSession: String
    val chooseForSessionBody: String
    val selectRuleset: String
    val nextParticipants: String
    fun entities(count: Int): String
    fun revision(version: String): String
    fun forkName(base: String): String
    fun applied(name: String): String
    fun appliedWithManualCpu(name: String): String
    fun saved(name: String): String
    fun publishedAs(name: String, version: String): String
    fun runtimeSummary(criticalThreshold: Int, maximumExhaustion: Int, entityCount: Int): String
    fun automationCoverage(full: Long, assisted: Long, manual: Long): String
    fun modifiedCount(count: Int): String
    fun addedCount(count: Int): String
    fun disabledCount(count: Int): String
}

internal object RulesStringsIt : RulesStrings {
    override val title = "Regole"
    override val subtitle = "Scegli, consulta e adatta il regolamento con cui gioca Onfall."
    override val standard = "Standard"
    override val homebrew = "Homebrew"
    override val imported = "Importato"
    override val sessionLocal = "Solo sessione"
    override val disabled = "Disabilitata"
    override val active = "Attive"
    override val allRulesets = "Tutti"
    override val readOnly = "Sola lettura"
    override val editableDraft = "Bozza modificabile"
    override val published = "Pubblicato"
    override val searchPlaceholder = "Cerca regole, classi, incantesimi…"
    override val noResults = "Nessuna regola corrisponde ai filtri."
    override val ruleset = "Regolamento"
    override val rule = "Regola"
    override val rulesetDetails = "Dettagli del regolamento"
    override val descriptionPlaceholder = "Descrizione del regolamento"
    override val saveRulesetDetails = "Salva nome e descrizione"
    override val source = "Fonte"
    override val automation = "Automazione"
    override val attributes = "Parametri"
    override val ruleType = "Tipo di regola"
    override val enabled = "Attiva"
    override val attributeKey = "Nome parametro"
    override val attributeValue = "Valore"
    override val tagsPlaceholder = "Tag separati da virgole"
    override val fullyAutomated = "Automatica"
    override val assisted = "Assistita"
    override val manual = "Manuale"
    override val fork = "Crea copia homebrew"
    override val editInDraft = "Modifica in una bozza"
    override val forkHint = "Lo standard resta intatto; la nuova linea parte da questa revisione."
    override val newRevisionDraft = "Nuova revisione"
    override val newRevisionHint = "La revisione pubblicata resta immutabile; le modifiche entrano in una nuova bozza."
    override val saveDraft = "Salva bozza"
    override val restoreFromBase = "Ripristina dalla base"
    override val removeAddedRule = "Rimuovi dalla bozza"
    override val publish = "Pubblica revisione"
    override val versionLabel = "Versione"
    override val addCustomRule = "Aggiungi elemento"
    override val customRuleName = "Nuova regola"
    override val customRuleDescription = "Descrivi qui la regola decisa dal tavolo."
    override val runtimeTitle = "Parametri runtime e formula assistita"
    override val runtimeHint =
        "Critico e Indebolimento sono applicati dal motore. La competenza resta assistita finché le schede legacy non vengono migrate."
    override val criticalThreshold = "Soglia del critico naturale"
    override val naturalOneMisses = "L'1 naturale manca sempre"
    override val maximumExhaustion = "Livello massimo di Indebolimento"
    override val exhaustionD20Penalty = "Penalità d20 per livello"
    override val exhaustionSpeedPenalty = "Riduzione velocità per livello (piedi)"
    override val proficiencyBase = "Competenza iniziale"
    override val proficiencyInterval = "Livelli per incremento"
    override val proficiencyMaximum = "Competenza massima"
    override val applyToSession = "Usa nella partita aperta"
    override val noOpenSession = "Apri una partita per applicarle un regolamento."
    override val applyPausesSession = "Se il combattimento è attivo verrà messo in pausa."
    override val cannotApplyResolved = "Una partita conclusa conserva il regolamento con cui è stata risolta."
    override val cpuManualFallback =
        "Questa revisione cambia strutture non interpretate dalla CPU: gli avversari restano sotto controllo manuale."
    override val currentSessionUses = "La partita aperta usa"
    override val chooseForSession = "Scegli il regolamento"
    override val chooseForSessionBody =
        "Ogni partita conserva la revisione esatta scelta qui. Potrai cambiarla in seguito dalla sezione Regole."
    override val selectRuleset = "Seleziona un regolamento"
    override val nextParticipants = "Avanti: partecipanti"
    override fun entities(count: Int) = if (count == 1) "1 regola" else "$count regole"
    override fun revision(version: String) = "Revisione $version"
    override fun forkName(base: String) = "$base — Homebrew"
    override fun applied(name: String) = "$name applicato alla partita; il combattimento è in pausa."
    override fun appliedWithManualCpu(name: String) =
        "$name applicato; il combattimento è in pausa e la CPU è stata disattivata per sicurezza."
    override fun saved(name: String) = "Bozza “$name” salvata."
    override fun publishedAs(name: String, version: String) = "“$name” pubblicato come versione $version."
    override fun runtimeSummary(criticalThreshold: Int, maximumExhaustion: Int, entityCount: Int) =
        "Critico $criticalThreshold+ · Indebolimento $maximumExhaustion · ${entities(entityCount)}"
    override fun automationCoverage(full: Long, assisted: Long, manual: Long) =
        "$full automatiche · $assisted assistite · $manual manuali"
    override fun modifiedCount(count: Int) = "$count modificate"
    override fun addedCount(count: Int) = "$count aggiunte"
    override fun disabledCount(count: Int) = "$count disabilitate"
}

internal object RulesStringsEn : RulesStrings {
    override val title = "Rules"
    override val subtitle = "Choose, browse, and adapt the ruleset Onfall uses at the table."
    override val standard = "Standard"
    override val homebrew = "Homebrew"
    override val imported = "Imported"
    override val sessionLocal = "Session only"
    override val disabled = "Disabled"
    override val active = "Enabled"
    override val allRulesets = "All"
    override val readOnly = "Read only"
    override val editableDraft = "Editable draft"
    override val published = "Published"
    override val searchPlaceholder = "Search rules, classes, spells…"
    override val noResults = "No rules match the current filters."
    override val ruleset = "Ruleset"
    override val rule = "Rule"
    override val rulesetDetails = "Ruleset details"
    override val descriptionPlaceholder = "Ruleset description"
    override val saveRulesetDetails = "Save name and description"
    override val source = "Source"
    override val automation = "Automation"
    override val attributes = "Parameters"
    override val ruleType = "Rule type"
    override val enabled = "Enabled"
    override val attributeKey = "Parameter name"
    override val attributeValue = "Value"
    override val tagsPlaceholder = "Comma-separated tags"
    override val fullyAutomated = "Automatic"
    override val assisted = "Assisted"
    override val manual = "Manual"
    override val fork = "Create homebrew copy"
    override val editInDraft = "Edit in a draft"
    override val forkHint = "The standard remains untouched; the new line starts from this revision."
    override val newRevisionDraft = "New revision"
    override val newRevisionHint = "The published revision stays immutable; edits go into a new draft."
    override val saveDraft = "Save draft"
    override val restoreFromBase = "Restore from base"
    override val removeAddedRule = "Remove from draft"
    override val publish = "Publish revision"
    override val versionLabel = "Version"
    override val addCustomRule = "Add element"
    override val customRuleName = "New rule"
    override val customRuleDescription = "Describe the table's rule here."
    override val runtimeTitle = "Runtime parameters and assisted formula"
    override val runtimeHint =
        "Critical hits and Exhaustion are enforced by the engine. Proficiency remains assisted until legacy sheets are migrated."
    override val criticalThreshold = "Natural critical threshold"
    override val naturalOneMisses = "A natural 1 always misses"
    override val maximumExhaustion = "Maximum Exhaustion level"
    override val exhaustionD20Penalty = "d20 penalty per level"
    override val exhaustionSpeedPenalty = "Speed reduction per level (feet)"
    override val proficiencyBase = "Initial proficiency"
    override val proficiencyInterval = "Levels per increase"
    override val proficiencyMaximum = "Maximum proficiency"
    override val applyToSession = "Use in open game"
    override val noOpenSession = "Open a game to apply a ruleset to it."
    override val applyPausesSession = "An active combat will be paused."
    override val cannotApplyResolved = "A resolved game keeps the ruleset it was completed with."
    override val cpuManualFallback =
        "This revision changes structures the CPU cannot interpret; opponents remain under manual control."
    override val currentSessionUses = "The open game uses"
    override val chooseForSession = "Choose the ruleset"
    override val chooseForSessionBody =
        "Every game keeps the exact revision selected here. You can change it later from Rules."
    override val selectRuleset = "Select a ruleset"
    override val nextParticipants = "Next: participants"
    override fun entities(count: Int) = if (count == 1) "1 rule" else "$count rules"
    override fun revision(version: String) = "Revision $version"
    override fun forkName(base: String) = "$base — Homebrew"
    override fun applied(name: String) = "$name was applied; combat is paused."
    override fun appliedWithManualCpu(name: String) =
        "$name was applied; combat is paused and the CPU was disabled for safety."
    override fun saved(name: String) = "Draft “$name” saved."
    override fun publishedAs(name: String, version: String) = "“$name” published as version $version."
    override fun runtimeSummary(criticalThreshold: Int, maximumExhaustion: Int, entityCount: Int) =
        "Critical $criticalThreshold+ · Exhaustion $maximumExhaustion · ${entities(entityCount)}"
    override fun automationCoverage(full: Long, assisted: Long, manual: Long) =
        "$full automatic · $assisted assisted · $manual manual"
    override fun modifiedCount(count: Int) = "$count modified"
    override fun addedCount(count: Int) = "$count added"
    override fun disabledCount(count: Int) = "$count disabled"
}
