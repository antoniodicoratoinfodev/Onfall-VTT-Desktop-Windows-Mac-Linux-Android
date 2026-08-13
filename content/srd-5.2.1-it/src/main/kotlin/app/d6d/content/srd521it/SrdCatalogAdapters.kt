package app.d6d.content.srd521it

import app.d6d.domain.combat.AbilityEffect
import app.d6d.domain.combat.ActivationCost
import app.d6d.domain.combat.AutomationStatus
import app.d6d.domain.combat.HealingTarget
import app.d6d.domain.combat.ResolutionMethod
import app.d6d.rules.character.ContentPackManifest
import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.RuleElementDefinition
import app.d6d.rules.character.RuleElementKind
import app.d6d.rules.character.RulesContentPack
import app.d6d.sheet.CatalogAbility
import app.d6d.sheet.CatalogHealing
import app.d6d.sheet.CatalogHealingBonusSource
import app.d6d.sheet.CatalogHealingSlotScaling
import app.d6d.sheet.SPELL_SLOT_RESOURCE_PREFIX
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
fun RuleElementDefinition.toCatalogAbility(manifest: ContentPackManifest): CatalogAbility {
    val healingSpec = structuredHealingById[id]
    return CatalogAbility(
        id = id,
        name = name,
        passive = healingSpec == null &&
            id != ACTION_SURGE_ID &&
            !id.isWildShapeForm() &&
            isPassiveTrait(activation.toActivationCost()),
        activationCost = if (id.isWildShapeForm()) ActivationCost.BONUS_ACTION else activation.toActivationCost(),
        resolutionMethod = if (id == ACTION_SURGE_ID || healingSpec != null) {
            ResolutionMethod.AUTOMATIC
        } else {
            ResolutionMethod.MANUAL
        },
        rangeFeet = healingSpec?.rangeFeet ?: 5,
        dealsDamage = false,
        automationStatus = if (id == ACTION_SURGE_ID || healingSpec != null) {
            AutomationStatus.AUTOMATED
        } else {
            AutomationStatus.MANUAL_REQUIRED
        },
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
        resourceId = healingSpec?.resourceId
            ?: if (id.isWildShapeForm()) WILD_SHAPE_RESOURCE_ID else resourceId,
        resourceCost = healingSpec?.resourceCost
            ?: if (id.isWildShapeForm()) 1 else resourceCost,
        effect = if (id == ACTION_SURGE_ID) AbilityEffect.GRANT_NON_MAGIC_ACTION else AbilityEffect.NONE,
        immutable = true,
        effects = effects,
        healing = healingSpec?.healing,
    )
}

private const val ACTION_SURGE_ID = "srd521-it:feature:guerriero:azione-impetuosa"
private const val WILD_SHAPE_RESOURCE_ID = "srd521-it:resource:druido:forma-selvatica"
private const val CURE_WOUNDS_ID = "srd521-it:spell:cura-ferite"
private const val HEALING_WORD_ID = "srd521-it:spell:parola-guaritrice"
private const val SECOND_WIND_ID = "srd521-it:feature:guerriero:recuperare-energie"
private const val SECOND_WIND_RESOURCE_ID = "srd521-it:resource:guerriero:recuperare-energie"

/**
 * Cure che il motore sa risolvere senza interpretare il testo SRD.
 *
 * La chiave e tutti i numeri sono intenzionalmente espliciti: una frase come
 * "recupera punti ferita" in una voce nuova o legacy non deve trasformarla
 * silenziosamente in una capacità automatizzata. Anche le risorse vengono
 * assegnate soltanto alle tre identità note qui sotto.
 */
private val structuredHealingById = mapOf(
    CURE_WOUNDS_ID to StructuredHealingSpec(
        healing = CatalogHealing.dice(
            HealingTarget.SELF_OR_ALLY,
            2,
            8,
            bonusSource = CatalogHealingBonusSource.SPELLCASTING_ABILITY,
            slotScaling = CatalogHealingSlotScaling(
                baseSlotLevel = 1,
                additionalDicePerSlotLevel = 2,
            ),
        ),
        rangeFeet = 5,
        resourceId = "${SPELL_SLOT_RESOURCE_PREFIX}1",
        resourceCost = 1,
    ),
    HEALING_WORD_ID to StructuredHealingSpec(
        healing = CatalogHealing.dice(
            HealingTarget.SELF_OR_ALLY,
            2,
            4,
            bonusSource = CatalogHealingBonusSource.SPELLCASTING_ABILITY,
            slotScaling = CatalogHealingSlotScaling(
                baseSlotLevel = 1,
                additionalDicePerSlotLevel = 2,
            ),
        ),
        rangeFeet = 60,
        resourceId = "${SPELL_SLOT_RESOURCE_PREFIX}1",
        resourceCost = 1,
    ),
    SECOND_WIND_ID to StructuredHealingSpec(
        healing = CatalogHealing.dice(
            HealingTarget.SELF,
            1,
            10,
            bonusSource = CatalogHealingBonusSource.CLASS_LEVEL,
            bonusClassId = CharacterClassId.FIGHTER,
        ),
        rangeFeet = 0,
        resourceId = SECOND_WIND_RESOURCE_ID,
        resourceCost = 1,
    ),
)

private data class StructuredHealingSpec(
    val healing: CatalogHealing,
    val rangeFeet: Int,
    val resourceId: String,
    val resourceCost: Int,
)

private fun String.isWildShapeForm(): Boolean = startsWith("srd521-it:beast:")

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
