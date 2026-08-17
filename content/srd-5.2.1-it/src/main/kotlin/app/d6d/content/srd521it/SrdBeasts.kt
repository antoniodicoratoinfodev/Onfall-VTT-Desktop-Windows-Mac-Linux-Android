package app.d6d.content.srd521it

import app.d6d.i18n.AppLanguage
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
    /** L'edizione da cui viene la scheda: decide come rileggerla. */
    val language: AppLanguage = AppLanguage.ITALIAN,
) {
    private val dialect: BeastDialect get() = BeastDialect.of(language)

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
        get() = dialect.summary(challengeRating, speed)

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
        prerequisite = dialect.wildShapePrerequisite(minimumDruidLevel),
        sourcePage = sourcePage,
    )

    /** Proiezione operativa usata quando il druido assume davvero questa forma. */
    fun toActorDefinition(): ActorDefinition {
        val normalized = statBlock.replace('−', '-').replace('–', '-')
        val header = requireNotNull(dialect.header.find(normalized)) {
            "Intestazione non valida per la forma $name."
        }
        val hitPoints = dialect.hitPoints.find(normalized)?.groupValues?.get(1)?.toInt()
            ?: error("Punti ferita mancanti per la forma $name.")
        val speedFeet = dialect.speed.find(normalized)
            ?.groupValues?.get(1)
            ?.let(dialect.speedToFeet)
            ?: 0
        val saves = parseSavingThrows(normalized, dialect)
        val actionsText = normalized.substringAfter(dialect.actionsHeading, "")
            .substringBefore(dialect.bonusActionsHeading)
            .replace('\n', ' ')
        val attacks = dialect.attack.findAll(actionsText).mapIndexed { index, match ->
            val values = match.groupValues
            val damageType = dialect.damageType(values[10])
            val damage = if (values[6].isNotBlank()) {
                val modifier = values[9].toIntOrNull().orZero() * if (values[8] == "-") -1 else 1
                DamageFormula.dice(damageType, values[6].toInt(), values[7].toInt(), modifier)
            } else {
                DamageFormula.fixed(damageType, values[5].toInt())
            }
            AbilityDefinition.builder("$id:attack:$index", values[1].trim())
                .version("1.0.0")
                .source(SrdIdentity.CANONICAL_PREFIX)
                .rulesetVersion("5.2.1")
                .resolutionMethod(ResolutionMethod.ATTACK_ROLL)
                .attackBonus(values[3].toInt())
                .rangeFeet(dialect.speedToFeet(values[4]))
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
            .speedFeet(speedFeet)
            .initiativeModifier(header.groupValues[2].toInt())
            .initiativeScore(header.groupValues[3].toInt())
            .constitutionSaveBonus(saves[SaveAbility.CONSTITUTION].orZero())
            .savingThrowBonuses(saves)
            .resistances(parseDamageList(normalized, dialect.resistancesLabel, dialect))
            .vulnerabilities(parseDamageList(normalized, dialect.vulnerabilitiesLabel, dialect))
            .damageImmunities(parseDamageList(normalized, dialect.damageImmunitiesLabel, dialect))
            .abilities(attacks)
            .attacksPerAction(if (dialect.multiattack in actionsText) 2 else 1)
            .build()
    }
}

private fun parseSavingThrows(text: String, dialect: BeastDialect): Map<SaveAbility, Int> =
    dialect.savingThrows
        .findAll(text)
        .associate { match ->
            // Il meno tipografico dell'SRD non e' il meno ASCII che Int.toInt() capisce.
            dialect.saveAbility(match.groupValues[1]) to match.groupValues[2].replace('−', '-').toInt()
        }

private fun parseDamageList(text: String, label: String, dialect: BeastDialect): Set<DamageType> {
    val line = Regex("(?m)^${Regex.escape(label)}(?: ai danni)? (.+)$")
        .find(text)?.groupValues?.get(1).orEmpty()
    return dialect.damageTypesIn(line)
}

private fun Int?.orZero(): Int = this ?: 0

/** Tutte le Bestie SRD con GS massimo 1, complete di scheda delle statistiche. */
object SrdBeasts {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = HashMap<AppLanguage, List<SrdBeastForm>>()

    fun all(language: AppLanguage = AppLanguage.ITALIAN): List<SrdBeastForm> = synchronized(cache) {
        cache.getOrPut(language) { load(language) }
    }

    private fun load(language: AppLanguage): List<SrdBeastForm> {
        val identity = SrdIdentity.of(language)
        val path = "/srd/${identity.resourceDirectory}/beasts.json"
        val stream = checkNotNull(SrdBeasts::class.java.getResourceAsStream(path)) {
            "Risorsa SRD delle forme bestiali non trovata: $path."
        }
        val document = stream.bufferedReader(Charsets.UTF_8).use {
            json.decodeFromString<BeastDocument>(it.readText())
        }
        check(document.schemaVersion == 1)
        check(document.records.size == document.counts.records)
        return document.records.map { record ->
            SrdBeastForm(
                id = identity.beastId(record.name, record.id),
                name = record.name,
                challengeRating = record.challengeRating,
                hasFlySpeed = record.hasFlySpeed,
                speed = record.speed,
                sourcePage = record.page,
                statBlock = record.statBlock,
                language = language,
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

    fun elements(language: AppLanguage): List<RuleElementDefinition> =
        all(language).map(SrdBeastForm::toRuleElement)

    fun byId(id: String, language: AppLanguage = AppLanguage.ITALIAN): SrdBeastForm? =
        all(language).firstOrNull { it.id == id }

    fun availableAt(
        druidLevel: Int,
        language: AppLanguage = AppLanguage.ITALIAN,
    ): List<SrdBeastForm> =
        all(language).filter { it.isAvailableAt(druidLevel) }
}
