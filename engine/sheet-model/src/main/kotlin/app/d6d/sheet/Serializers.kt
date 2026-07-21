package app.d6d.sheet

import app.d6d.domain.combat.ConditionType
import app.d6d.domain.combat.DamageType
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

    override fun serialize(encoder: Encoder, value: DamageType) = encoder.encodeString(value.name)

    override fun deserialize(decoder: Decoder): DamageType = DamageType.valueOf(decoder.decodeString())
}

object ConditionTypeSerializer : KSerializer<ConditionType> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("app.d6d.domain.combat.ConditionType", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ConditionType) = encoder.encodeString(value.name)

    override fun deserialize(decoder: Decoder): ConditionType = ConditionType.valueOf(decoder.decodeString())
}
