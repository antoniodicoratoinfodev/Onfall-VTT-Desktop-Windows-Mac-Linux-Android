package app.d6d.ui.theme

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BackdropAnimationTest {

    @Test
    fun `il tempo del fondale completa il ciclo senza accumulare deriva`() {
        val cycle = 18_000_000_000L

        assertEquals(0f, backdropCycleProgress(0L))
        assertEquals(0.5f, backdropCycleProgress(cycle / 2))
        assertEquals(0f, backdropCycleProgress(cycle))
        assertEquals(0.25f, backdropCycleProgress(cycle + cycle / 4))
    }

    @Test
    fun `la ripresa continua dall'ultimo fotogramma congelato`() {
        val cycle = 18_000_000_000L

        assertEquals(0.75f, backdropResumedCycleProgress(0.25f, cycle / 2), 0.000001f)
        assertEquals(0.25f, backdropResumedCycleProgress(0.75f, cycle / 2), 0.000001f)
    }
}
