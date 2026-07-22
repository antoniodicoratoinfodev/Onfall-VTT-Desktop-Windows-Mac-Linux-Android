package app.d6d.sheet

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DistanceUnitsTest {
    @Test
    fun `converte i piedi in metri senza arrotondare`() {
        assertEquals("1,524", metresFromFeet(5))
        assertEquals("9,144", metresFromFeet(30))
        assertEquals("45,72", metresFromFeet(150))
        assertEquals("−1,524", metresFromFeet(-5))
    }

    @Test
    fun `aggiunge i metri anche alle gittate espresse in piedi`() {
        assertEquals(
            "Gittata 30/120 piedi (9,144/36,576 m).",
            "Gittata 30/120 piedi.".withMetricFeet(),
        )
        assertEquals("Portata 5 ft. (1,524 m)", "Portata 5 ft.".withMetricFeet())
    }
}
