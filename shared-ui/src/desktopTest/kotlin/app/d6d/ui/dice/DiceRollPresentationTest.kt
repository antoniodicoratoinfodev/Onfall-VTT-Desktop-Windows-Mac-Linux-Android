package app.d6d.ui.dice

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
    }

    @Test
    fun `ogni risultato seleziona la propria faccia`() {
        listOf(4, 6, 8, 10, 12, 20).forEach { sides ->
            (1..sides).forEach { result ->
                assertEquals(result - 1, resultFaceIndex(sides, result), "d$sides = $result")
            }
        }
    }
}
