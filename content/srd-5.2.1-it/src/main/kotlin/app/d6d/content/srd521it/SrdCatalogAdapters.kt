package app.d6d.content.srd521it

import app.d6d.domain.combat.AbilityEffect
import app.d6d.domain.combat.ActivationCost
import app.d6d.domain.combat.AutomationStatus
import app.d6d.domain.combat.ResolutionMethod
import app.d6d.rules.character.ContentPackManifest
import app.d6d.rules.character.RuleElementDefinition
import app.d6d.rules.character.RuleElementKind
import app.d6d.rules.character.RulesContentPack
import app.d6d.sheet.CatalogAbility
import app.d6d.sheet.reflowRulesText

/**
 * Proiezione completa di una voce del pacchetto nel Compendio.
 *
 * Alcuni effetti appartengono alla riga di livello, perche' il motore deve
 * applicarli automaticamente salendo di livello. Quando lo stesso privilegio
 * viene collegato a mano dalla scheda, pero', serve anche sulla sua voce di
 * catalogo. Si importa soltanto l'effetto del livello in cui il privilegio viene
 * acquisito e la cui sorgente coincide col suo nome: gli altri privilegi della
 * stessa riga non ricevono cosi' un bonus che non appartiene loro.
 */
fun RuleElementDefinition.toCatalogAbility(pack: RulesContentPack): CatalogAbility {
    val inheritedEffects = pack.classes
        .asSequence()
        .flatMap { it.levels.asSequence() }
        .filter { id in it.featureIds }
        .flatMap { it.effects.asSequence() }
        .filter { it.source.effectSourceKey() == name.effectSourceKey() }
        .toList()
    return toCatalogAbility(pack.manifest).copy(
        effects = (effects + inheritedEffects).distinct(),
    )
}

/** Proiezione leggibile dal Compendio e selezionabile dalle schede/battaglia. */
fun RuleElementDefinition.toCatalogAbility(manifest: ContentPackManifest): CatalogAbility =
    CatalogAbility(
        id = id,
        name = name,
        passive = id != ACTION_SURGE_ID && isPassiveTrait(activation.toActivationCost()),
        activationCost = activation.toActivationCost(),
        resolutionMethod = if (id == ACTION_SURGE_ID) ResolutionMethod.AUTOMATIC else ResolutionMethod.MANUAL,
        dealsDamage = false,
        automationStatus = if (id == ACTION_SURGE_ID) AutomationStatus.AUTOMATED else AutomationStatus.MANUAL_REQUIRED,
        rulesText = description.reflowRulesText(),
        category = kind,
        classEligibility = classEligibility,
        sourcePackId = manifest.id,
        sourcePackVersion = manifest.version,
        sourcePage = sourcePage,
        spellLevel = spell?.level,
        school = spell?.school.orEmpty(),
        castingTime = spell?.castingTime ?: activation,
        components = spell?.components.orEmpty(),
        duration = spell?.duration.orEmpty(),
        concentration = spell?.concentration ?: false,
        ritual = spell?.ritual ?: false,
        prerequisite = prerequisite,
        resourceId = resourceId,
        resourceCost = resourceCost,
        effect = if (id == ACTION_SURGE_ID) AbilityEffect.GRANT_NON_MAGIC_ACTION else AbilityEffect.NONE,
        immutable = true,
        effects = effects,
    )

private const val ACTION_SURGE_ID = "srd521-it:feature:guerriero:azione-impetuosa"

/**
 * Un tratto è passivo quando vale sempre e non c'è nulla da spendere nel turno.
 *
 * I talenti lo sono per definizione: concedono benefici permanenti, ed eventuali
 * incantesimi che regalano vivono come elementi propri. Privilegi, opzioni,
 * metamagie e suppliche lo sono solo quando non costano azione, azione bonus o
 * reazione. Incantesimi e trucchetti restano sempre giocabili, e con loro le
 * azioni comuni — tranne quelle che si limitano a nominare ciò che fanno altre
 * capacità (vedi [markerActionIds]).
 */
private fun RuleElementDefinition.isPassiveTrait(cost: ActivationCost): Boolean = when (kind) {
    RuleElementKind.CANTRIP,
    RuleElementKind.SPELL,
    -> false
    RuleElementKind.COMMON_ACTION -> id in markerActionIds
    RuleElementKind.ORIGIN_FEAT,
    RuleElementKind.GENERAL_FEAT,
    RuleElementKind.FIGHTING_STYLE_FEAT,
    RuleElementKind.EPIC_BOON_FEAT,
    -> true
    else -> cost == ActivationCost.NONE
}

/**
 * Azioni comuni che da sole non risolvono niente.
 *
 * Nominano ciò che fanno altre capacità: l'azione di Magia dice che il
 * personaggio sa lanciare, ma a lanciare sono i singoli incantesimi; l'azione
 * di Attacco dice che sa combattere, ma a colpire sono le singole armi. Ognuna
 * di quelle ha già la propria scheda con bersaglio, gittata e danno, mentre
 * queste due, come comando, sarebbero schede su cui non c'è nulla da premere.
 * Valgono quindi come indicazione, accanto a chi ha il turno.
 */
private val markerActionIds = setOf(
    "srd521-it:action:magia",
    "srd521-it:action:attacco",
)

private fun String.effectSourceKey(): String =
    lowercase().filter(Char::isLetterOrDigit)

/**
 * Lo SRD descrive l'attivazione a parole. Le voci che iniziano con "Passiva" o
 * "Nessuna azione" non consumano nulla anche quando proseguono con la condizione
 * che le innesca ("Passiva; subito dopo il tiro per l'iniziativa").
 */
private fun String.toActivationCost(): ActivationCost {
    val normalized = lowercase().trim()
    return when {
        normalized.startsWith("passiva") || normalized.startsWith("nessuna azione") ->
            ActivationCost.NONE
        "azione bonus" in normalized -> ActivationCost.BONUS_ACTION
        "reazione" in normalized -> ActivationCost.REACTION
        normalized.isBlank() || normalized == "—" -> ActivationCost.NONE
        else -> ActivationCost.ACTION
    }
}
