package app.d6d.rules.authoring;

import app.d6d.rules.model.RuleFormula;

import java.util.Objects;

/**
 * Formula modificabile senza normalizzare silenziosamente il sorgente esistente.
 * Finché l'albero non cambia, {@link #sourceForSave()} restituisce esattamente il
 * testo compilato originario; dopo una modifica usa la serializzazione canonica.
 */
public record FormulaDraft(
        String originalSource,
        RuleFormula.Expression expression,
        boolean dirty,
        ProjectionStatus projectionStatus
) {
    public FormulaDraft {
        originalSource = Objects.requireNonNull(originalSource, "originalSource").trim();
        expression = Objects.requireNonNull(expression, "expression");
        projectionStatus = Objects.requireNonNull(projectionStatus, "projectionStatus");
        if (originalSource.isEmpty()) throw new IllegalArgumentException("Formula cannot be blank");
    }

    public static FormulaDraft parse(String source) {
        RuleFormula formula = RuleFormula.compile(source);
        return new FormulaDraft(
                formula.source(),
                formula.expression(),
                false,
                FormulaProjection.classify(formula.expression()));
    }

    public FormulaDraft edit(RuleFormula.Expression changedExpression) {
        RuleFormula compiled = RuleFormula.compile(changedExpression);
        return new FormulaDraft(
                originalSource,
                compiled.expression(),
                true,
                FormulaProjection.classify(compiled.expression()));
    }

    public String sourceForSave() {
        return dirty ? RuleFormula.compile(expression).source() : originalSource;
    }
}
