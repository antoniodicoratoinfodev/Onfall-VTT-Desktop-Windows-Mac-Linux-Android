package app.d6d.rules.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Fallimento completo di composizione; espone tutti i problemi utili all'editor. */
public final class RulesetCompositionException extends IllegalArgumentException {
    private final List<RulesetCompositionIssue> issues;

    public RulesetCompositionException(List<RulesetCompositionIssue> issues) {
        super(message(issues));
        ArrayList<RulesetCompositionIssue> sorted = new ArrayList<>(Objects.requireNonNull(issues, "issues"));
        if (sorted.isEmpty()) throw new IllegalArgumentException("A composition exception needs at least one issue");
        sorted.forEach(value -> Objects.requireNonNull(value, "issues contains null"));
        sorted.sort(Comparator.comparing((RulesetCompositionIssue value) -> value.code().name())
                .thenComparing(RulesetCompositionIssue::moduleId)
                .thenComparing(RulesetCompositionIssue::relatedModuleId)
                .thenComparing(RulesetCompositionIssue::stablePath)
                .thenComparing(RulesetCompositionIssue::detail));
        this.issues = List.copyOf(sorted);
    }

    public List<RulesetCompositionIssue> issues() {
        return issues;
    }

    private static String message(List<RulesetCompositionIssue> raw) {
        Objects.requireNonNull(raw, "issues");
        if (raw.isEmpty()) return "Ruleset composition failed";
        return raw.stream().sorted(Comparator.comparing((RulesetCompositionIssue value) -> value.code().name())
                        .thenComparing(RulesetCompositionIssue::moduleId)
                        .thenComparing(RulesetCompositionIssue::relatedModuleId)
                        .thenComparing(RulesetCompositionIssue::stablePath)
                        .thenComparing(RulesetCompositionIssue::detail))
                .map(issue -> issue.code() + ": " + issue.detail())
                .collect(Collectors.joining("; ", "Ruleset composition failed: ", ""));
    }
}
