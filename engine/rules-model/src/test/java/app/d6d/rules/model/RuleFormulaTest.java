package app.d6d.rules.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuleFormulaTest {

    @Test
    void evaluatesExactArithmeticConditionsAndTables() {
        RuleFormula formula = RuleFormula.compile(
                "if(${ability:mind} >= 13, floor((${ability:mind} - 10) / 2), 0) + lookup(\"table:rank\", 2)");

        BigDecimal result = formula.evaluate(RuleFormula.context(
                Map.of("ability:mind", new BigDecimal("15")),
                Map.of("table:rank", Map.of(new BigDecimal("2"), new BigDecimal("3")))));

        assertEquals(new BigDecimal("5"), result);
        assertEquals(java.util.Set.of("ability:mind"), formula.valueReferences());
        assertEquals(java.util.Set.of("table:rank"), formula.tableReferences());
    }

    @Test
    void rejectsUnsafeOrUndefinedOperations() {
        assertThrows(IllegalArgumentException.class, () -> RuleFormula.compile("system(1)"));
        RuleFormula division = RuleFormula.compile("10 / ${zero}");
        assertThrows(ArithmeticException.class, () -> division.evaluate(
                RuleFormula.context(Map.of("zero", BigDecimal.ZERO), Map.of())));
    }

    @Test
    void rejectsInvalidFunctionArityAtCompilationTime() {
        assertThrows(IllegalArgumentException.class, () -> RuleFormula.compile("if(1)"));
        assertThrows(IllegalArgumentException.class, () -> RuleFormula.compile("clamp(1, 0)"));
        assertThrows(IllegalArgumentException.class, () -> RuleFormula.compile("abs(1, 2)"));
        assertThrows(IllegalArgumentException.class, () -> RuleFormula.compile("min()"));
    }

    @Test
    void rejectsUnaryChainsBeyondTheNestingBudgetWithoutOverflowingTheStack() {
        String source = "!".repeat(65) + "1";

        assertThrows(IllegalArgumentException.class, () -> RuleFormula.compile(source));
    }

    @Test
    void tableLookupUsesNumericEqualityIndependentlyFromDecimalScale() {
        RuleFormula formula = RuleFormula.compile("lookup(\"table:rank\", 1 + 1)");

        BigDecimal result = formula.evaluate(RuleFormula.context(
                Map.of(), Map.of("table:rank", Map.of(
                        new BigDecimal("2.0"), new BigDecimal("3.00")))));

        assertEquals(new BigDecimal("3"), result);
    }
}
