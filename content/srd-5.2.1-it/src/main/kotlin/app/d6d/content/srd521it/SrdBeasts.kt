package app.d6d.content.srd521it

import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.ClassEligibility
import app.d6d.rules.character.RuleElementDefinition
import app.d6d.rules.character.RuleElementKind
import app.d6d.domain.combat.AbilityDefinition
import app.d6d.domain.combat.ActorDefinition
import app.d6d.domain.combat.AutomationStatus
import app.d6d.domain.combat.DamageFormula
import app.d6d.domain.combat.DamageType
import app.d6d.domain.combat.ResolutionMethod
import app.d6d.domain.combat.SaveAbility
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class BeastDocument(
    @SerialName("schema_version") val schemaVersion: Int,
    val counts: BeastCounts,
    val records: List<BeastRecord>,
)

@Serializable
private data class BeastCounts(val records: Int)

@Serializable
private data class BeastRecord(
    val id: String,
    val name: String,
    @SerialName("challenge_rating") val challengeRating: String,
    @SerialName("has_fly_speed") val hasFlySpeed: Boolean,
    val speed: String,
    val page: Int,
    @SerialName("stat_block") val statBlock: String,
)

/** Scheda completa di una forma bestiale selezionabile con Forma selvatica. */
data class SrdBeastForm(
    val id: String,
    val name: String,
    val challengeRating: String,
    val hasFlySpeed: Boolean,
    val speed: String,
    val sourcePage: Int,
    val statBlock: String,
) {
    private val challengeRank: Int
        get() = when (challengeRating) {
            "0" -> 0
            "1/8" -> 1
            "1/4" -> 2
            "1/2" -> 4
            "1" -> 8
            else -> error("GS non supportato per Forma selvatica: $challengeRating")
        }

    val minimumDruidLevel: Int
        get() = when {
            hasFlySpeed -> 8
            challengeRank <= 2 -> 2
            challengeRank <= 4 -> 4
            else -> 8
        }

    val summary: String
        get() = "GS $challengeRating · Velocità $speed"

    fun isAvailableAt(druidLevel: Int): Boolean {
        val maximumRank = when {
            druidLevel < 4 -> 2
            druidLevel < 8 -> 4
            else -> 8
        }
        return druidLevel >= 2 && challengeRank <= maximumRank && (druidLevel >= 8 || !hasFlySpeed)
    }

    fun toRuleElement(): RuleElementDefinition = RuleElementDefinition(
        id = id,
        name = name,
        kind = RuleElementKind.CLASS_OPTION,
        description = statBlock,
        classEligibility = listOf(ClassEligibility(CharacterClassId.DRUID, minimumDruidLevel)),
        prerequisite = "Forma selvatica · Druido di ${minimumDruidLevel}º livello",
        sourcePage = sourcePage,
    )

    /** Proiezione operativa usata quando il druido assume davvero questa forma. */
    fun toActorDefinition(): ActorDefinition {
        val normalized = statBlock.replace('−', '-').replace('–', '-')
        val header = requireNotNull(
            Regex("(?m)^CA (\\d+) Iniziativa ([+-]?\\d+) \\((\\d+)\\)").find(normalized),
        ) { "Intestazione non valida per la forma $name." }
        val hitPoints = Regex("(?m)^PF (\\d+)").find(normalized)?.groupValues?.get(1)?.toInt()
            ?: error("PF mancanti per la forma $name.")
        val speedMetres = Regex("(?m)^Velocità ([\\d,]+) m").find(normalized)
            ?.groupValues?.get(1)?.replace(',', '.')?.toDouble()
            ?: 0.0
        val saves = parseSavingThrows(normalized)
        val actionsText = normalized.substringAfter("\nAzioni\n", "")
            .substringBefore("\nAzioni bonus\n")
            .replace('\n', ' ')
        val attacks = attackRegex.findAll(actionsText).mapIndexed { index, match ->
            val values = match.groupValues
            val damageType = damageType(values[10])
            val damage = if (values[6].isNotBlank()) {
                val modifier = values[9].toIntOrNull().orZero() * if (values[8] == "-") -1 else 1
                DamageFormula.dice(damageType, values[6].toInt(), values[7].toInt(), modifier)
            } else {
                DamageFormula.fixed(damageType, values[5].toInt())
            }
            AbilityDefinition.builder("$id:attack:$index", values[1].trim())
                .version("1.0.0")
                .source("srd521-it")
                .rulesetVersion("5.2.1")
                .resolutionMethod(ResolutionMethod.ATTACK_ROLL)
                .attackBonus(values[3].toInt())
                .rangeFeet(metresToFeet(values[4]))
                .damage(listOf(damage))
                .automationStatus(AutomationStatus.AUTOMATED)
                .rulesText(match.value.trim())
                .build()
        }.toList()
        return ActorDefinition.builder(id, name)
            .definitionVersion("1.0.0")
            .rulesetVersion("5.2.1")
            .armorClass(header.groupValues[1].toInt())
            // Forma Selvatica conserva i PF del druido; questo valore descrive la
            // bestia e viene sostituito dal comando di trasformazione.
            .maxHitPoints(hitPoints)
            .speedFeet((speedMetres / 1.5 * 5).toInt())
            .initiativeModifier(header.groupValues[2].toInt())
            .initiativeScore(header.groupValues[3].toInt())
            .constitutionSaveBonus(saves[SaveAbility.CONSTITUTION].orZero())
            .savingThrowBonuses(saves)
            .resistances(parseDamageList(normalized, "Resistenze"))
            .vulnerabilities(parseDamageList(normalized, "Vulnerabilità"))
            .damageImmunities(parseDamageList(normalized, "Immunità ai danni"))
            .abilities(attacks)
            .attacksPerAction(if ("Multiattacco." in actionsText) 2 else 1)
            .build()
    }
}

private val attackRegex = Regex(
    "([A-ZÀ-Ü][^.]*)\\. Tiro per colpire (in mischia|a distanza): ([+-]?\\d+)(?: \\([^)]*\\))?, " +
        "(?:portata|gittata) ([\\d,]+)(?:/[\\d,]+)? m\\.?\\s*Colpito: (\\d+)" +
        "(?: \\((\\d+)d(\\d+)(?:\\s*([+-])?\\s*(\\d+))?\\))? dann[oi] (?:da )?([a-zàèéìòù]+)",
)

private fun parseSavingThrows(text: String): Map<SaveAbility, Int> {
    val labels = mapOf(
        "For" to SaveAbility.STRENGTH,
        "Des" to SaveAbility.DEXTERITY,
        "Cos" to SaveAbility.CONSTITUTION,
        "Int" to SaveAbility.INTELLIGENCE,
        "Sag" to SaveAbility.WISDOM,
        "Car" to SaveAbility.CHARISMA,
    )
    return Regex("(For|Des|Cos|Int|Sag|Car) \\d+ [+-]?\\d+ ([+-]?\\d+)")
        .findAll(text)
        .associate { match -> labels.getValue(match.groupValues[1]) to match.groupValues[2].toInt() }
}

private fun parseDamageList(text: String, label: String): Set<DamageType> {
    val line = Regex("(?m)^${Regex.escape(label)}(?: ai danni)? (.+)$")
        .find(text)?.groupValues?.get(1).orEmpty().lowercase()
    return DamageType.entries.filterTo(mutableSetOf()) { type ->
        val italian = when (type) {
            DamageType.ACID -> "acido"
            DamageType.BLUDGEONING -> "contundente"
            DamageType.COLD -> "freddo"
            DamageType.FIRE -> "fuoco"
            DamageType.FORCE -> "forza"
            DamageType.LIGHTNING -> "fulmine"
            DamageType.NECROTIC -> "necrotico"
            DamageType.PIERCING -> "perforante"
            DamageType.POISON -> "veleno"
            DamageType.PSYCHIC -> "psichico"
            DamageType.RADIANT -> "radioso"
            DamageType.SLASHING -> "tagliente"
            DamageType.THUNDER -> "tuono"
            DamageType.UNTYPED -> ""
        }
        italian.isNotEmpty() && italian in line
    }
}

private fun damageType(label: String): DamageType = when (label.lowercase()) {
    "acido" -> DamageType.ACID
    "contundenti", "contundente" -> DamageType.BLUDGEONING
    "freddo" -> DamageType.COLD
    "fuoco" -> DamageType.FIRE
    "forza" -> DamageType.FORCE
    "fulmine" -> DamageType.LIGHTNING
    "necrotici", "necrotico" -> DamageType.NECROTIC
    "perforanti", "perforante" -> DamageType.PIERCING
    "veleno" -> DamageType.POISON
    "psichici", "psichico" -> DamageType.PSYCHIC
    "radiosi", "radioso" -> DamageType.RADIANT
    "taglienti", "tagliente" -> DamageType.SLASHING
    "tuono" -> DamageType.THUNDER
    else -> error("Tipo di danno non supportato: $label")
}

private fun metresToFeet(value: String): Int =
    (value.replace(',', '.').toDouble() / 1.5 * 5).toInt()

private fun Int?.orZero(): Int = this ?: 0

/** Tutte le Bestie SRD con GS massimo 1, complete di scheda delle statistiche. */
object SrdBeasts {
    private val json = Json { ignoreUnknownKeys = true }

    val all: List<SrdBeastForm> by lazy {
        val stream = checkNotNull(
            SrdBeasts::class.java.getResourceAsStream("/srd/5.2.1-it/beasts.json"),
        ) { "Risorsa SRD delle forme bestiali non trovata." }
        val document = stream.bufferedReader(Charsets.UTF_8).use {
            json.decodeFromString<BeastDocument>(it.readText())
        }
        check(document.schemaVersion == 1)
        check(document.records.size == document.counts.records)
        document.records.map { record ->
            SrdBeastForm(
                id = record.id,
                name = record.name,
                challengeRating = record.challengeRating,
                hasFlySpeed = record.hasFlySpeed,
                speed = record.speed,
                sourcePage = record.page,
                statBlock = record.statBlock,
            )
        }.also { forms ->
            check(forms.size == 64) {
                "Attese 64 forme bestiali SRD con GS massimo 1, trovate ${forms.size}."
            }
            check(forms.distinctBy { it.id }.size == forms.size) {
                "Gli ID delle forme bestiali SRD non sono univoci."
            }
        }
    }

    val elements: List<RuleElementDefinition> by lazy { all.map(SrdBeastForm::toRuleElement) }

    fun byId(id: String): SrdBeastForm? = all.firstOrNull { it.id == id }

    fun availableAt(druidLevel: Int): List<SrdBeastForm> =
        all.filter { it.isAvailableAt(druidLevel) }
}
