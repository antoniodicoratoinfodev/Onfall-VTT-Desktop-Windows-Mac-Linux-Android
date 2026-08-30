package app.d6d.sheet

import app.d6d.domain.combat.AbilityEffect
import app.d6d.domain.combat.ActivationCost
import app.d6d.domain.combat.AutomationStatus
import app.d6d.domain.combat.ConditionType
import app.d6d.domain.combat.DamageType
import app.d6d.domain.combat.HealingTarget
import app.d6d.domain.combat.ResolutionMethod
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Serializer per gli enum del dominio.
 *
 * Sono enum Java, quindi kotlinx.serialization non li tratta da solo. Vengono
 * scritti come nome costante: e' stabile e leggibile nel file salvato, e un
 * valore sconosciuto fallisce in modo esplicito invece di essere ignorato.
 */
object DamageTypeSerializer : KSerializer<DamageType> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("app.d6d.domain.combat.DamageType", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: DamageType) = encoder.encodeString(value.name())

    override fun deserialize(decoder: Decoder): DamageType = DamageType.valueOf(decoder.decodeString())
}

object ConditionTypeSerializer : KSerializer<ConditionType> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("app.d6d.domain.combat.ConditionType", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ConditionType) = encoder.encodeString(value.name())

    override fun deserialize(decoder: Decoder): ConditionType = ConditionType.valueOf(decoder.decodeString())
}

object ActivationCostSerializer : KSerializer<ActivationCost> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("app.d6d.domain.combat.ActivationCost", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ActivationCost) = encoder.encodeString(value.name)

    override fun deserialize(decoder: Decoder): ActivationCost = ActivationCost.valueOf(decoder.decodeString())
}

object AbilityEffectSerializer : KSerializer<AbilityEffect> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("app.d6d.domain.combat.AbilityEffect", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: AbilityEffect) = encoder.encodeString(value.name)

    override fun deserialize(decoder: Decoder): AbilityEffect = AbilityEffect.valueOf(decoder.decodeString())
}

object ResolutionMethodSerializer : KSerializer<ResolutionMethod> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("app.d6d.domain.combat.ResolutionMethod", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ResolutionMethod) = encoder.encodeString(value.name)

    override fun deserialize(decoder: Decoder): ResolutionMethod = ResolutionMethod.valueOf(decoder.decodeString())
}

object AutomationStatusSerializer : KSerializer<AutomationStatus> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("app.d6d.domain.combat.AutomationStatus", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: AutomationStatus) = encoder.encodeString(value.name)

    override fun deserialize(decoder: Decoder): AutomationStatus = AutomationStatus.valueOf(decoder.decodeString())
}

object HealingTargetSerializer : KSerializer<HealingTarget> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("app.d6d.domain.combat.HealingTarget", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: HealingTarget) = encoder.encodeString(value.name)

    override fun deserialize(decoder: Decoder): HealingTarget = HealingTarget.valueOf(decoder.decodeString())
}
