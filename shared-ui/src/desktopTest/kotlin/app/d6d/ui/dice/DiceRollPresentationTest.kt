package app.d6d.ui.dice

import app.d6d.domain.combat.D20Mode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiceRollPresentationTest {

    @Test
    fun `il vassoio standard conserva esattamente i tempi storici`() {
        assertEquals(FULL_ROLL_MILLIS, diceRollDurationMillis(false, DiceRollPresentation.STANDARD))
        assertEquals(REDUCED_ROLL_MILLIS, diceRollDurationMillis(true, DiceRollPresentation.STANDARD))
    }

    @Test
    fun `il tiro in primo piano e piu lento del dieci percento`() {
        assertEquals(1_375, diceRollDurationMillis(false, DiceRollPresentation.FOREGROUND))
        assertEquals(308, diceRollDurationMillis(true, DiceRollPresentation.FOREGROUND))
    }

    @Test
    fun `ogni dado usa il solido corretto e ha una faccia per risultato`() {
        val expectedVerticesPerFace = mapOf(4 to 3, 6 to 4, 8 to 3, 10 to 4, 12 to 5, 20 to 3)

        expectedVerticesPerFace.forEach { (sides, verticesPerFace) ->
            val geometry = dieGeometry(sides)

            assertEquals(sides, geometry.faces.size, "d$sides")
            assertTrue(geometry.faces.all { it.size == verticesPerFace }, "facce del d$sides")
            assertTrue(geometry.faces.flatten().all { it in geometry.vertices.indices }, "vertici del d$sides")
        }

        val d100 = dieGeometry(100)
        assertEquals(100, d100.faces.size)
        assertTrue(d100.faces.all { it.size >= 3 }, "facce del d100")
        assertTrue(d100.faces.flatten().all { it in d100.vertices.indices }, "vertici del d100")
    }

    @Test
    fun `ogni risultato seleziona la propria faccia`() {
        listOf(4, 6, 8, 10, 12, 20, 100).forEach { sides ->
            (1..sides).forEach { result ->
                val expectedFace = result - 1
                assertEquals(expectedFace, resultFaceIndex(sides, result), "mappatura d$sides = $result")
                assertEquals(
                    expectedFace,
                    settledFrontFaceIndex(sides, expectedFace),
                    "faccia frontale d$sides = $result",
                )
                val settled = settledDieFace(sides, expectedFace)
                assertEquals(0.0, settled.normal.x, 1e-9, "normale X d$sides = $result")
                assertEquals(0.0, settled.normal.y, 1e-9, "normale Y d$sides = $result")
                assertEquals(1.0, settled.normal.z, 1e-9, "normale Z d$sides = $result")
                assertEquals(0.0, settled.up.x, 1e-9, "verticale X d$sides = $result")
                assertEquals(1.0, settled.up.y, 1e-9, "verticale Y d$sides = $result")
                assertEquals(0.0, settled.up.z, 1e-9, "verticale Z d$sides = $result")
            }
        }
    }

    @Test
    fun `il d100 cinematografico e un unico solido con cento facce numerate`() {
        val selection = cinematicDiceSelection(
            DiceTrayResult(
                id = 100,
                linkMode = DiceLinkMode.UNLINKED,
                rolls = listOf(
                    PresentedDiceRoll(
                        purpose = DiceRollPurpose.FREE,
                        sides = 100,
                        values = listOf(100),
                        total = 100,
                    ),
                ),
            ),
        )

        val die = selection.dice.single()
        assertEquals(100, die.sides)
        assertEquals((1..100).map(Int::toString), die.faceLabels)
        assertEquals(99, die.targetFaceIndex)
    }

    @Test
    fun `vantaggio e svantaggio linked animano due d20 e scelgono il risultato corretto`() {
        listOf(
            D20Mode.ADVANTAGE to 18,
            D20Mode.DISADVANTAGE to 7,
        ).forEach { (mode, selectedValue) ->
            val selection = cinematicDiceSelection(
                DiceTrayResult(
                    id = 1,
                    linkMode = DiceLinkMode.LINKED,
                    rolls = listOf(d20Roll(listOf(7, 18), mode, selectedValue, kept = true)),
                ),
            )

            assertEquals(2, selection.dice.size, mode.name)
            assertEquals(listOf(6, 17), selection.dice.map(CinematicDieSpec::targetFaceIndex), mode.name)
            assertEquals(
                listOf(7 == selectedValue, 18 == selectedValue),
                selection.dice.map(CinematicDieSpec::kept),
                mode.name,
            )
            assertTrue(selection.dice.all(CinematicDieSpec::competing), mode.name)
        }
    }

    @Test
    fun `vantaggio e svantaggio unlinked mostrano sia il pool tenuto sia quello scartato`() {
        listOf(
            D20Mode.ADVANTAGE to listOf(7 to false, 18 to true),
            D20Mode.DISADVANTAGE to listOf(7 to true, 18 to false),
        ).forEach { (mode, alternatives) ->
            val selection = cinematicDiceSelection(
                DiceTrayResult(
                    id = 2,
                    linkMode = DiceLinkMode.UNLINKED,
                    rolls = alternatives.map { (value, kept) ->
                        d20Roll(listOf(value), mode, value, kept)
                    },
                ),
            )

            assertEquals(2, selection.dice.size, mode.name)
            assertEquals(alternatives.map { it.first - 1 }, selection.dice.map(CinematicDieSpec::targetFaceIndex))
            assertEquals(alternatives.map { it.second }, selection.dice.map(CinematicDieSpec::kept))
            assertEquals(alternatives.single { it.second }.first, selection.primary.values.single())
            assertTrue(selection.dice.all(CinematicDieSpec::competing), mode.name)
        }
    }

    private fun d20Roll(
        values: List<Int>,
        mode: D20Mode,
        selectedValue: Int,
        kept: Boolean,
    ) = PresentedDiceRoll(
        purpose = DiceRollPurpose.FREE,
        sides = 20,
        values = values,
        total = selectedValue,
        mode = mode,
        selectedValue = selectedValue,
        kept = kept,
    )
}
