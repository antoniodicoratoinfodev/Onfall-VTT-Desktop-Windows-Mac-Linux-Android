package app.d6d.content.srd521it

import app.d6d.rules.character.CharacterClassId
import app.d6d.rules.character.CharacterProgression
import app.d6d.rules.character.ChoiceDefinition
import app.d6d.rules.character.ChoiceKind
import app.d6d.rules.character.ChoiceSelection
import app.d6d.rules.character.ClassLevelState
import app.d6d.rules.character.ExperienceProgression
import app.d6d.rules.character.LevelUpRequest
import app.d6d.sheet.CharacterSheet
import app.d6d.sheet.GuidedCharacterService
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SrdFeatChoiceRegressionTest {
    private val pack = Srd521ItContent.pack
    private val service = GuidedCharacterService(pack)

    @Test
    fun `il resolver non offre un talento gia scelto nello stesso avanzamento`() {
        val sheet = warlockBeforeLevelTwelve(
            featIds = listOf(SKILLED_FEAT_ID),
        )
        val (generalFeatChoice, invocationChoice) = levelTwelveBaseChoices(sheet)

        val generalOptions = SrdChoiceResolver.options(
            generalFeatChoice,
            CharacterClassId.WARLOCK,
            12,
            sheet,
        )
        assertTrue(
            generalOptions.any { it.id == SKILLED_FEAT_ID },
            "Un talento ripetibile deve restare disponibile in un livello successivo.",
        )

        listOf(ALERT_FEAT_ID, SKILLED_FEAT_ID).forEach { featId ->
            val parents = listOf(
                ChoiceSelection(generalFeatChoice.id, listOf(featId)),
                ChoiceSelection(invocationChoice.id, listOf(ANCIENT_KNOWLEDGE_ID)),
            )
            val ancientKnowledgeFeatChoice = ancientKnowledgeFeatChoice(sheet, parents)
            val ancientKnowledgeOptions = SrdChoiceResolver.options(
                ancientKnowledgeFeatChoice,
                CharacterClassId.WARLOCK,
                12,
                sheet,
                parents,
            )

            assertFalse(
                ancientKnowledgeOptions.any { it.id == featId },
                "Il talento $featId non deve essere offerto due volte nello stesso avanzamento.",
            )
        }
    }

    @Test
    fun `la validazione rifiuta talenti duplicati nello stesso avanzamento`() {
        val sheet = warlockBeforeLevelTwelve()

        listOf(ALERT_FEAT_ID, SKILLED_FEAT_ID).forEach { featId ->
            val request = duplicateFeatRequest(sheet, featId)
            val validation = service.validate(sheet, request)

            assertTrue(
                validation.issues.any { it.code == "DUPLICATE_ACQUISITION" },
                "La doppia acquisizione di $featId deve essere rifiutata: ${validation.issues}",
            )
        }
    }

    private fun duplicateFeatRequest(
        sheet: CharacterSheet,
        featId: String,
    ): LevelUpRequest {
        val (generalFeatChoice, invocationChoice) = levelTwelveBaseChoices(sheet)
        val parents = listOf(
            ChoiceSelection(generalFeatChoice.id, listOf(featId)),
            ChoiceSelection(invocationChoice.id, listOf(ANCIENT_KNOWLEDGE_ID)),
        )
        val ancientKnowledgeFeatChoice = ancientKnowledgeFeatChoice(sheet, parents)
        val featSelections = parents + ChoiceSelection(
            ancientKnowledgeFeatChoice.id,
            listOf(featId),
        )
        val childSelections = service
            .requirements(sheet, CharacterClassId.WARLOCK, featSelections)
            .filter { requirement ->
                requirement.id !in featSelections.mapTo(mutableSetOf()) { it.choiceId }
            }
            .map { requirement ->
                val options = SrdChoiceResolver.options(
                    requirement,
                    CharacterClassId.WARLOCK,
                    12,
                    sheet,
                    featSelections,
                )
                ChoiceSelection(
                    requirement.id,
                    if (requirement.minimumCount == 0) {
                        emptyList()
                    } else {
                        options.take(requirement.count).map { it.id }
                    },
                )
            }
        return LevelUpRequest(
            classId = CharacterClassId.WARLOCK,
            hitPointIncrease = service.fixedHitPointIncrease(sheet, CharacterClassId.WARLOCK),
            usedFixedHitPoints = true,
            selections = featSelections + childSelections,
        )
    }

    private fun levelTwelveBaseChoices(
        sheet: CharacterSheet,
    ): Pair<ChoiceDefinition, ChoiceDefinition> {
        val requirements = service.requirements(sheet, CharacterClassId.WARLOCK)
        return requirements.single { it.id.endsWith(":12:aumento-o-talento") } to
            requirements.single { it.kind == ChoiceKind.ELDRITCH_INVOCATION }
    }

    private fun ancientKnowledgeFeatChoice(
        sheet: CharacterSheet,
        parentSelections: List<ChoiceSelection>,
    ): ChoiceDefinition = service
        .requirements(sheet, CharacterClassId.WARLOCK, parentSelections)
        .single { it.id.contains(":conoscenze-degli-antichi:talento") }

    private fun warlockBeforeLevelTwelve(
        featIds: List<String> = emptyList(),
    ): CharacterSheet = CharacterSheet(
        experiencePoints = ExperienceProgression.thresholdForLevel(12),
        progression = CharacterProgression(
            contentPackId = pack.manifest.id,
            contentPackVersion = pack.manifest.version,
            classLevels = listOf(ClassLevelState(CharacterClassId.WARLOCK, 11)),
            featIds = featIds,
        ),
    )

    private companion object {
        const val ALERT_FEAT_ID = "srd521-it:feat:origin:allerta"
        const val SKILLED_FEAT_ID = "srd521-it:feat:origin:abile"
        const val ANCIENT_KNOWLEDGE_ID =
            "srd521-it:feature:warlock:conoscenze-degli-antichi"
    }
}
