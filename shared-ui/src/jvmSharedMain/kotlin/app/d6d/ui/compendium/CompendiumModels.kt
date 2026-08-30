package app.d6d.ui.compendium

import app.d6d.domain.campaign.ActorKind
import app.d6d.domain.campaign.ActorTemplate
import app.d6d.domain.catalog.ActorCatalogEntry
import app.d6d.domain.combat.AbilityDefinition
import app.d6d.domain.combat.ActivationCost
import app.d6d.domain.combat.ActorDefinition
import app.d6d.domain.combat.AutomationStatus
import app.d6d.domain.combat.ConditionType
import app.d6d.domain.combat.DamageFormula
import app.d6d.ui.i18n.AppLocale
import app.d6d.domain.combat.DamageType
import app.d6d.domain.combat.HealingDefinition
import app.d6d.domain.combat.ResolutionMethod
import app.d6d.domain.combat.SaveAbility
import java.math.BigDecimal

private const val RULESET = "5.2.1"
private const val VERSION = "1.0.0"
private const val SOURCE = "content-user-private"

/**
 * Bozza modificabile di una capacita'.
 *
 * Il dominio e' immutabile per scelta, quindi l'editor lavora su bozze e produce
 * la definizione finale solo al salvataggio. Cosi' una modifica a meta' non puo'
 * corrompere un combattimento in corso.
 */
data class AbilityDraft(
    val id: String = "cap-nuova",
    val name: String = AppLocale.current.compendium.newAbility,
    val activationCost: ActivationCost = ActivationCost.ACTION,
    val resolutionMethod: ResolutionMethod = ResolutionMethod.ATTACK_ROLL,
    val attackBonus: Int = 4,
    val attackAbility: SaveAbility? = null,
    val spellOrCantrip: Boolean = false,
    val rangeFeet: Int = 5,
    val maxTargets: Int = 1,
    val dealsDamage: Boolean = true,
    val diceCount: Int = 1,
    val diceSides: Int = 6,
    val diceModifier: Int = 2,
    val damageType: DamageType = DamageType.SLASHING,
    val automationStatus: AutomationStatus = AutomationStatus.AUTOMATED,
    val rulesText: String = "",
    val resourceId: String = "",
    val resourceCost: Int = 0,
    val healing: HealingDefinition? = null,
) {
    fun toDefinition(): AbilityDefinition {
        val structuredHealing = healing
        val damage = if (dealsDamage && structuredHealing == null) {
            listOf(
                DamageFormula.dice(
                    damageType,
                    diceCount.coerceAtLeast(1),
                    diceSides.coerceAtLeast(2),
                    diceModifier,
                ),
            )
        } else {
            emptyList()
        }
        return AbilityDefinition.builder(
            id.ifBlank { "cap-senza-nome" },
            name.ifBlank { AppLocale.current.compendium.unnamed },
        )
            .version(VERSION)
            .source(SOURCE)
            .rulesetVersion(RULESET)
            .activationCost(activationCost)
            .resolutionMethod(
                if (structuredHealing != null) ResolutionMethod.AUTOMATIC else resolutionMethod,
            )
            .attackBonus(attackBonus)
            .attackAbility(attackAbility)
            .spellOrCantrip(spellOrCantrip)
            .rangeFeet(rangeFeet)
            .maxTargets(if (structuredHealing != null) 1 else maxTargets.coerceAtLeast(1))
            .damage(damage)
            .automationStatus(
                if (structuredHealing != null) AutomationStatus.AUTOMATED else automationStatus,
            )
            .rulesText(rulesText)
            .resource(resourceId, resourceCost)
            .healing(structuredHealing)
            .build()
    }

    companion object {
        fun from(definition: AbilityDefinition): AbilityDraft {
            val formula = definition.damage().firstOrNull()
            val dice = formula?.dice()
            return AbilityDraft(
                id = definition.id(),
                name = definition.name(),
                activationCost = definition.activationCost(),
                resolutionMethod = definition.resolutionMethod(),
                attackBonus = definition.attackBonus(),
                attackAbility = definition.attackAbility(),
                spellOrCantrip = definition.spellOrCantrip(),
                rangeFeet = definition.rangeFeet(),
                maxTargets = definition.maxTargets(),
                dealsDamage = definition.damage().isNotEmpty(),
                diceCount = dice?.count() ?: 1,
                diceSides = dice?.sides() ?: 6,
                diceModifier = dice?.modifier() ?: 0,
                damageType = formula?.type() ?: DamageType.SLASHING,
                automationStatus = definition.automationStatus(),
                rulesText = definition.rulesText(),
                resourceId = definition.resourceId(),
                resourceCost = definition.resourceCost(),
                healing = definition.healing(),
            )
        }
    }
}

/** Bozza modificabile di un attore: personaggio, PNG o creatura. */
data class ActorDraft(
    val id: String = "attore-nuovo",
    val name: String = AppLocale.current.compendium.newActor,
    val kind: ActorKind = ActorKind.CREATURE,
    val level: Int = 1,
    val armorClass: Int = 13,
    val maxHitPoints: Int = 20,
    val speedFeet: Int = 30,
    val initiativeModifier: Int = 0,
    val constitutionSaveBonus: Int = 0,
    val challengeRating: String = "1",
    val xp: Long = 200,
    val activePartyMember: Boolean = false,
    val resistances: Set<DamageType> = emptySet(),
    val vulnerabilities: Set<DamageType> = emptySet(),
    val damageImmunities: Set<DamageType> = emptySet(),
    val conditionImmunities: Set<ConditionType> = emptySet(),
    val abilities: List<AbilityDraft> = listOf(AbilityDraft()),
) {
    fun toDefinition(): ActorDefinition = ActorDefinition(
        id.ifBlank { "attore-senza-id" },
        VERSION,
        RULESET,
        name.ifBlank { AppLocale.current.compendium.unnamed },
        armorClass,
        maxHitPoints.coerceAtLeast(1),
        maxHitPoints.coerceAtLeast(1),
        0,
        speedFeet,
        initiativeModifier,
        // Il punteggio statico d'iniziativa resta un campo distinto dal
        // modificatore, come richiede lo stat block aggiornato.
        10 + initiativeModifier,
        constitutionSaveBonus,
        resistances,
        vulnerabilities,
        damageImmunities,
        conditionImmunities,
        abilities.map { it.toDefinition() },
    )

    fun toEntry(): ActorCatalogEntry = ActorCatalogEntry(
        ActorTemplate(
            id.ifBlank { "attore-senza-id" },
            name.ifBlank { AppLocale.current.compendium.unnamed },
            kind,
            if (kind == ActorKind.PLAYER_CHARACTER) level.coerceAtLeast(1) else level.coerceAtLeast(0),
        ),
        toDefinition(),
        activePartyMember,
        runCatching { BigDecimal(challengeRating.trim()) }.getOrElse { BigDecimal.ZERO },
        xp.coerceAtLeast(0),
    )

    companion object {
        fun from(entry: ActorCatalogEntry): ActorDraft {
            val definition = entry.combatDefinition()
            return ActorDraft(
                id = definition.id(),
                name = definition.name(),
                kind = entry.template().kind(),
                level = entry.template().level(),
                armorClass = definition.armorClass(),
                maxHitPoints = definition.maxHitPoints(),
                speedFeet = definition.speedFeet(),
                initiativeModifier = definition.initiativeModifier(),
                constitutionSaveBonus = definition.constitutionSaveBonus(),
                challengeRating = entry.challengeRating().toPlainString(),
                xp = entry.xp(),
                activePartyMember = entry.activePartyMember(),
                resistances = definition.resistances().toSet(),
                vulnerabilities = definition.vulnerabilities().toSet(),
                damageImmunities = definition.damageImmunities().toSet(),
                conditionImmunities = definition.conditionImmunities().toSet(),
                abilities = definition.abilities().map { AbilityDraft.from(it) }
                    .ifEmpty { listOf(AbilityDraft()) },
            )
        }
    }
}
