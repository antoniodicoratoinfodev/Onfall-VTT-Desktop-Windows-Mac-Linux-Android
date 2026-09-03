package app.d6d.rules.authoring;

import app.d6d.rules.model.RuleFormula;

/** Classifica i nodi che il primo editor a blocchi può modificare direttamente. */
public final class FormulaProjection {
    private FormulaProjection() { }

    public static ProjectionStatus classify(RuleFormula.Expression expression) {
        if (expression instanceof RuleFormula.NumberExpression
                || expression instanceof RuleFormula.ValueExpression) {
            return ProjectionStatus.EXACT;
        }
        if (expression instanceof RuleFormula.UnaryExpression unary) {
            ProjectionStatus child = classify(unary.operand());
            return unary.operator().equals("+") || unary.operator().equals("-") || unary.operator().equals("!")
                    ? child : child.combine(ProjectionStatus.PARTIAL);
        }
        if (expression instanceof RuleFormula.BinaryExpression binary) {
            ProjectionStatus children = classify(binary.left()).combine(classify(binary.right()));
            return switch (binary.operator()) {
                case "+", "-", "*", "/", "%", "<", "<=", ">", ">=", "==", "!=", "&&", "||" -> children;
                default -> children.combine(ProjectionStatus.PARTIAL);
            };
        }
        if (expression instanceof RuleFormula.FunctionExpression function) {
            ProjectionStatus children = function.arguments().stream()
                    .map(FormulaProjection::classify)
                    .reduce(ProjectionStatus.EXACT, ProjectionStatus::combine);
            return switch (function.name()) {
                case "min", "max", "clamp", "abs", "floor", "ceil", "round" -> children;
                default -> children.combine(ProjectionStatus.PARTIAL);
            };
        }
        return ProjectionStatus.EXPERT_ONLY;
    }
}
