package app.d6d.domain.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class XpBudgetTable2024Test {

    @ParameterizedTest(name = "level {0}: Low {1}, Moderate {2}, High {3}")
    @MethodSource("allTwentyRows")
    void containsEveryOfficial2024Row(int level, long low, long moderate, long high) {
        assertEquals(new XpBudget(low, moderate, high), XpBudgetTable2024.forLevel(level));
    }

    @Test
    void addsEveryMixedPartyLevelIndependently() {
        assertEquals(
                new XpBudget(6_950, 14_025, 23_200),
                XpBudgetTable2024.forPartyLevels(List.of(1, 5, 20)));
    }

    @Test
    void rejectsLevelsOutsideTheSupportedRange() {
        assertThrows(IllegalArgumentException.class, () -> XpBudgetTable2024.forLevel(0));
        assertThrows(IllegalArgumentException.class, () -> XpBudgetTable2024.forLevel(21));
    }

    private static Stream<Arguments> allTwentyRows() {
        return Stream.of(
                Arguments.of(1, 50, 75, 100),
                Arguments.of(2, 100, 150, 200),
                Arguments.of(3, 150, 225, 400),
                Arguments.of(4, 250, 375, 500),
                Arguments.of(5, 500, 750, 1_100),
                Arguments.of(6, 600, 1_000, 1_400),
                Arguments.of(7, 750, 1_300, 1_700),
                Arguments.of(8, 1_000, 1_700, 2_100),
                Arguments.of(9, 1_300, 2_000, 2_600),
                Arguments.of(10, 1_600, 2_300, 3_100),
                Arguments.of(11, 1_900, 2_900, 4_100),
                Arguments.of(12, 2_200, 3_700, 4_700),
                Arguments.of(13, 2_600, 4_200, 5_400),
                Arguments.of(14, 2_900, 4_900, 6_200),
                Arguments.of(15, 3_300, 5_400, 7_800),
                Arguments.of(16, 3_800, 6_100, 9_800),
                Arguments.of(17, 4_500, 7_200, 11_700),
                Arguments.of(18, 5_000, 8_700, 14_200),
                Arguments.of(19, 5_500, 10_700, 17_200),
                Arguments.of(20, 6_400, 13_200, 22_000));
    }
}
