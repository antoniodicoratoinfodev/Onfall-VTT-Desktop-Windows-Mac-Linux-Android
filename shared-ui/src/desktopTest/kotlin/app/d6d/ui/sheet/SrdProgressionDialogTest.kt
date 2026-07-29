package app.d6d.ui.sheet

import app.d6d.rules.character.Ability
import app.d6d.rules.character.ChoiceDefinition
import app.d6d.rules.character.ChoiceKind
import app.d6d.rules.character.ChoiceSelection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SrdProgressionDialogTest {

    @Test
    fun `gli aumenti espliciti non sopravvivono al cambio del talento`() {
        val stale = mapOf(Ability.CHARISMA to 2)

        assertEquals(
            emptyMap<Ability, Int>(),
            resolvedAbilityScoreIncreases(
                hasExplicitIncreaseFeat = false,
                explicitIncreases = stale,
                conditionalIncreases = emptyMap(),
            ),
        )
        assertEquals(
            stale,
            resolvedAbilityScoreIncreases(
                hasExplicitIncreaseFeat = true,
                explicitIncreases = stale,
                conditionalIncreases = emptyMap(),
            ),
        )

        val conditional = mapOf(Ability.STRENGTH to 1)
        assertEquals(
            conditional,
            resolvedAbilityScoreIncreases(
                hasExplicitIncreaseFeat = true,
                explicitIncreases = stale,
                conditionalIncreases = conditional,
            ),
        )
    }

    @Test
    fun `le scelte figlie inattive vengono eliminate fino a stabilizzazione`() {
        val root = choice("talento")
        val magicChild = choice("iniziato-lista")
        val magicGrandchild = choice("iniziato-incantesimo")
        val requirements: (List<ChoiceSelection>) -> List<ChoiceDefinition> = { provisional ->
            val rootOption = provisional
                .firstOrNull { it.choiceId == root.id }
                ?.optionIds
                ?.singleOrNull()
            val allOptions = provisional.flatMap { it.optionIds }
            buildList {
                add(root)
                if (rootOption == "iniziato") add(magicChild)
                if ("lista-magia" in allOptions) add(magicGrandchild)
            }
        }

        val staleDraft = stabilizeProgressionDraft(
            selections = linkedMapOf(
                root.id to listOf("allerta"),
                magicChild.id to listOf("lista-magia"),
                magicGrandchild.id to listOf("dardo"),
            ),
            requirementsFor = requirements,
        )

        assertEquals(mapOf(root.id to listOf("allerta")), staleDraft.selections)
        assertEquals(listOf(root.id), staleDraft.requirements.map { it.id })

        val activeDraft = stabilizeProgressionDraft(
            selections = linkedMapOf(
                root.id to listOf("iniziato"),
                magicChild.id to listOf("lista-magia"),
                magicGrandchild.id to listOf("dardo"),
            ),
            requirementsFor = requirements,
        )

        assertEquals(
            listOf(root.id, magicChild.id, magicGrandchild.id),
            activeDraft.requirements.map { it.id },
        )
        assertEquals(3, activeDraft.selections.size)
    }

    private fun choice(id: String): ChoiceDefinition = ChoiceDefinition(
        id = id,
        title = id,
        kind = ChoiceKind.FEAT,
        count = 1,
        optionIds = listOf("opzione"),
    )
}
