@file:UseSerializers(
    ActivationCostSerializer::class,
    AutomationStatusSerializer::class,
    DamageTypeSerializer::class,
    ResolutionMethodSerializer::class,
)

package app.d6d.sheet

import app.d6d.domain.combat.AbilityDefinition
import app.d6d.domain.combat.ActivationCost
import app.d6d.domain.combat.AutomationStatus
import app.d6d.domain.combat.DamageFormula
import app.d6d.domain.combat.DamageType
import app.d6d.domain.combat.ResolutionMethod
import app.d6d.domain.combat.SaveAbility
import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.ClassEligibility
import app.d6d.rules.character.RuleElementKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

/** Componente di danno aggiuntiva, per capacità che combinano più tipi. */
@Serializable
data class CatalogDamage(
    val diceCount: Int = 1,
    val diceSides: Int = 6,
    val modifier: Int = 0,
    val type: DamageType = DamageType.SLASHING,
) {
    val text: String
        get() = buildString {
            append(diceCount).append('d').append(diceSides)
            if (modifier != 0) append(formatModifier(modifier))
            append(' ').append(type.italianLabel)
        }

    fun toFormula(): DamageFormula = DamageFormula.dice(
        type,
        diceCount.coerceAtLeast(1),
        diceSides.coerceAtLeast(2),
        modifier,
    )
}

/**
 * Capacità riusabile conservata nel Compendio.
 *
 * I personaggi ne memorizzano soltanto l'identificatore: nome, regole e meccanica
 * restano una fonte unica e una correzione nel catalogo vale per tutte le schede
 * che la usano.
 */
@Serializable
data class CatalogAbility(
    val id: String,
    val name: String = "",
    val activationCost: ActivationCost = ActivationCost.ACTION,
    val resolutionMethod: ResolutionMethod = ResolutionMethod.ATTACK_ROLL,
    val attackBonus: Int = 0,
    val rangeFeet: Int = 5,
    val maxTargets: Int = 1,
    val dealsDamage: Boolean = true,
    val diceCount: Int = 1,
    val diceSides: Int = 6,
    val damageModifier: Int = 0,
    val damageType: DamageType = DamageType.SLASHING,
    /** Ulteriori componenti, per esempio il danno necrotico oltre al morso perforante. */
    val additionalDamage: List<CatalogDamage> = emptyList(),
    val automationStatus: AutomationStatus = AutomationStatus.AUTOMATED,
    val rulesText: String = "",
    val areaRadiusFeet: Int = 0,
    val saveAbility: Ability? = null,
    val halfOnSave: Boolean = false,
    /** Metadati del contenuto: vuoti per le capacità private create dall'utente. */
    val category: RuleElementKind = RuleElementKind.CUSTOM,
    val classEligibility: List<ClassEligibility> = emptyList(),
    val sourcePackId: String? = null,
    val sourcePackVersion: String = "1.0.0",
    val sourcePage: Int = 0,
    val spellLevel: Int? = null,
    val school: String = "",
    val castingTime: String = "",
    val components: String = "",
    val duration: String = "",
    val concentration: Boolean = false,
    val ritual: Boolean = false,
    val prerequisite: String = "",
    val resourceId: String? = null,
    val resourceCost: Int = 0,
    val immutable: Boolean = false,
    /**
     * Tratto permanente — padronanza d'armi, Incantesimi, un talento — che non si
     * attiva mai nel turno. Resta fuori dalle capacità giocabili e viene mostrato
     * accanto al nome di chi ha il turno.
     */
    val passive: Boolean = false,
) {
    val isArea: Boolean get() = areaRadiusFeet > 0

    val damageText: String
        get() = if (!dealsDamage) {
            "Nessun danno"
        } else {
            buildList {
                add(buildString {
                    append(diceCount).append('d').append(diceSides)
                    if (damageModifier != 0) append(formatModifier(damageModifier))
                    append(' ').append(damageType.italianLabel)
                })
                addAll(additionalDamage.map { it.text })
            }.joinToString(" + ")
        }

    fun toDefinition(rulesetVersion: String = "5.2.1"): AbilityDefinition {
        require(id.isNotBlank()) { "L'identificatore dell'abilità non può essere vuoto." }
        require(name.isNotBlank()) { "Il nome dell'abilità non può essere vuoto." }
        require(rangeFeet >= 0) { "La gittata non può essere negativa." }
        require(maxTargets > 0) { "Il numero di bersagli deve essere positivo." }
        require(areaRadiusFeet >= 0) { "Il raggio dell'area non può essere negativo." }
        require(sourcePage >= 0) { "La pagina sorgente non può essere negativa." }
        require(spellLevel == null || spellLevel in 0..9) { "Il livello dell'incantesimo deve essere 0-9." }
        require(resourceCost >= 0) { "Il costo in risorse non può essere negativo." }
        require(resolutionMethod != ResolutionMethod.ATTACK_ROLL || dealsDamage) {
            "Un attacco deve indicare il danno."
        }

        val damage = if (dealsDamage) {
            listOf(
                DamageFormula.dice(
                    damageType,
                    diceCount.coerceAtLeast(1),
                    diceSides.coerceAtLeast(2),
                    damageModifier,
                ),
            ) + additionalDamage.map { it.toFormula() }
        } else {
            emptyList()
        }
        return AbilityDefinition.builder(id, name)
            .version(sourcePackVersion)
            .source(sourcePackId ?: "content-user-private")
            .rulesetVersion(rulesetVersion)
            .activationCost(activationCost)
            .resolutionMethod(resolutionMethod)
            .attackBonus(attackBonus)
            .rangeFeet(rangeFeet)
            .maxTargets(maxTargets)
            .damage(damage)
            .automationStatus(automationStatus)
            .rulesText(rulesText)
            .areaRadiusFeet(areaRadiusFeet)
            .saveAbility(saveAbility?.let { SaveAbility.valueOf(it.name) })
            .halfOnSave(halfOnSave)
            .passive(passive)
            .build()
    }

    fun availableTo(classId: CharacterClassId, classLevel: Int): Boolean =
        classEligibility.isEmpty() || classEligibility.any {
            it.classId == classId && classLevel >= it.minimumLevel
        }
}

/**
 * Capacità iniziali usate dalle schede e dall'incontro dimostrativo.
 *
 * Gli identificatori coincidono con quelli degli attori di esempio: una scheda
 * ricostruita dal combattimento può quindi collegarsi alla voce del catalogo
 * invece di crearne una copia incorporata.
 */
fun defaultAbilityCatalog(): List<CatalogAbility> = listOf(
    catalogAttack(
        "arma-spadone", "Spadone", 6, 5, 2, 6, 4, DamageType.SLASHING,
        "Attacco in mischia con arma. Portata 5 piedi.",
    ),
    catalogAttack(
        "arma-giavellotto", "Giavellotto", 5, 30, 1, 6, 3, DamageType.PIERCING,
        "Attacco a distanza. Gittata 30/120 piedi.",
    ),
    catalogAttack(
        "inc-dardo-runico", "Dardo Runico", 6, 60, 2, 6, 0, DamageType.FORCE,
        "Trucchetto d'attacco a distanza. Non consuma slot.",
    ),
    CatalogAbility(
        id = "inc-palla-di-fuoco",
        name = "Palla di Fuoco",
        activationCost = ActivationCost.ACTION,
        resolutionMethod = ResolutionMethod.SAVING_THROW,
        rangeFeet = 150,
        dealsDamage = true,
        diceCount = 8,
        diceSides = 6,
        damageType = DamageType.FIRE,
        areaRadiusFeet = 20,
        saveAbility = Ability.DEXTERITY,
        halfOnSave = true,
        rulesText = "Invocazione di 3° livello. Sfera di 6 m (20 piedi); tiro salvezza su " +
            "Destrezza, metà danni se superato. Ai livelli superiori: +1d6 per ogni " +
            "slot oltre il 3°.",
    ),
    catalogAttack(
        "arma-bastone", "Bastone", 2, 5, 1, 6, 0, DamageType.BLUDGEONING,
        "Attacco in mischia con arma.",
    ),
    catalogAttack(
        "arma-martello", "Martello da Guerra", 5, 5, 1, 8, 3, DamageType.BLUDGEONING,
        "Attacco in mischia con arma.",
    ),
    catalogAttack(
        "arma-arco", "Arco Lungo", 7, 150, 1, 8, 4, DamageType.PIERCING,
        "Attacco a distanza. Gittata 150/600 piedi.",
    ),
    catalogAttack(
        "arma-pugnale", "Pugnale", 6, 5, 1, 4, 4, DamageType.PIERCING,
        "Arma Leggera: attacco come Azione Bonus dopo l'Azione Attacco.",
        cost = ActivationCost.BONUS_ACTION,
    ),
    catalogAttack(
        "nem-scimitarra", "Scimitarra", 4, 5, 1, 6, 2, DamageType.SLASHING,
        "Attacco in mischia con arma.",
    ),
    catalogAttack(
        "nem-morso", "Morso Gelido", 4, 5, 1, 6, 2, DamageType.PIERCING,
        "Attacco in mischia. Infligge danni perforanti e necrotici.",
        additionalDamage = listOf(CatalogDamage(1, 4, 0, DamageType.NECROTIC)),
    ),
    catalogAttack(
        "nem-scarica", "Scarica Ombrosa", 5, 90, 2, 8, 0, DamageType.NECROTIC,
        "Attacco magico a distanza.",
    ),
    CatalogAbility(
        id = "nem-mastino-bonus_action-0",
        name = "Scatto nell'Ombra",
        activationCost = ActivationCost.BONUS_ACTION,
        resolutionMethod = ResolutionMethod.MANUAL,
        rangeFeet = 30,
        dealsDamage = false,
        automationStatus = AutomationStatus.MANUAL_REQUIRED,
        rulesText = "Si teletrasporta fino a 9 m in uno spazio in ombra che può vedere.",
    ),
    CatalogAbility(
        id = "abilita-furtivita-ombra",
        name = "Furtività d'Ombra",
        activationCost = ActivationCost.BONUS_ACTION,
        resolutionMethod = ResolutionMethod.MANUAL,
        dealsDamage = false,
        automationStatus = AutomationStatus.MANUAL_REQUIRED,
        rulesText = "Mentre si trova in oscurità leggera o totale, può compiere l'azione " +
            "Nascondersi come Azione Bonus.",
    ),
    CatalogAbility(
        id = "abilita-recuperare-energie",
        name = "Recuperare Energie",
        activationCost = ActivationCost.BONUS_ACTION,
        resolutionMethod = ResolutionMethod.MANUAL,
        dealsDamage = false,
        automationStatus = AutomationStatus.MANUAL_REQUIRED,
        rulesText = "Recupera 1d10 + livello da guerriero punti ferita; si ricarica con un riposo.",
    ),
    CatalogAbility(
        id = "abilita-azione-impetuosa",
        name = "Azione Impetuosa",
        activationCost = ActivationCost.NONE,
        resolutionMethod = ResolutionMethod.MANUAL,
        dealsDamage = false,
        automationStatus = AutomationStatus.MANUAL_REQUIRED,
        rulesText = "Ottiene un'Azione aggiuntiva nel turno; si ricarica con un riposo.",
    ),
)

private fun catalogAttack(
    id: String,
    name: String,
    attackBonus: Int,
    rangeFeet: Int,
    diceCount: Int,
    diceSides: Int,
    modifier: Int,
    damageType: DamageType,
    rulesText: String,
    cost: ActivationCost = ActivationCost.ACTION,
    additionalDamage: List<CatalogDamage> = emptyList(),
): CatalogAbility = CatalogAbility(
    id = id,
    name = name,
    activationCost = cost,
    resolutionMethod = ResolutionMethod.ATTACK_ROLL,
    attackBonus = attackBonus,
    rangeFeet = rangeFeet,
    diceCount = diceCount,
    diceSides = diceSides,
    damageModifier = modifier,
    damageType = damageType,
    additionalDamage = additionalDamage,
    rulesText = rulesText,
)
