package app.d6d.rules.character

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ExperienceProgressionTest {

    @Test
    fun `le venti soglie PE seguono SRD 5 2 1`() {
        assertEquals(
            listOf(
                0, 300, 900, 2_700, 6_500, 14_000, 23_000, 34_000, 48_000, 64_000,
                85_000, 100_000, 120_000, 140_000, 165_000, 195_000, 225_000,
                265_000, 305_000, 355_000,
            ),
            ExperienceProgression.thresholds,
        )
        ExperienceProgression.thresholds.forEachIndexed { index, xp ->
            assertEquals(index + 1, ExperienceProgression.levelForExperience(xp))
        }
        assertEquals(1, ExperienceProgression.levelForExperience(-1))
        assertEquals(20, ExperienceProgression.levelForExperience(Int.MAX_VALUE))
    }

    @Test
    fun `prossima soglia e talenti oltre il ventesimo sono derivati`() {
        assertEquals(300, ExperienceProgression.nextThreshold(1))
        assertEquals(355_000, ExperienceProgression.nextThreshold(19))
        assertNull(ExperienceProgression.nextThreshold(20))
        assertEquals(0, ExperienceProgression.epicBonusFeatCount(384_999))
        assertEquals(1, ExperienceProgression.epicBonusFeatCount(385_000))
        assertEquals(3, ExperienceProgression.epicBonusFeatCount(445_000))
    }

    @Test
    fun `bonus competenza usa il livello totale`() {
        assertEquals(2, ExperienceProgression.proficiencyBonus(1))
        assertEquals(2, ExperienceProgression.proficiencyBonus(4))
        assertEquals(3, ExperienceProgression.proficiencyBonus(5))
        assertEquals(4, ExperienceProgression.proficiencyBonus(9))
        assertEquals(5, ExperienceProgression.proficiencyBonus(13))
        assertEquals(6, ExperienceProgression.proficiencyBonus(17))
        assertEquals(6, ExperienceProgression.proficiencyBonus(20))
    }
}
