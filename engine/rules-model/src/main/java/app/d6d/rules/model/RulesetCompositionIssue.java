package app.d6d.rules.model;

import java.util.List;
import java.util.Objects;

/** Diagnostica strutturata e stabile prodotta prima che una composizione venga pubblicata. */
public record RulesetCompositionIssue(
        Code code,
        String moduleId,
        String relatedModuleId,
        RuleFieldRef field,
        String detail,
        List<RulesetModuleRef> candidateWinners) {

    public enum Code {
        DUPLICATE_MODULE,
        SEMANTICS_MISMATCH,
        MISSING_DEPENDENCY,
        DEPENDENCY_HASH_MISMATCH,
        DEPENDENCY_ORDER,
        UNDECLARED_DEPENDENCY,
        INCOMPATIBLE_MODULES,
        ADDITION_COLLISION,
        PATCH_TARGET_MISSING,
        FIELD_CONFLICT,
        INVALID_RESOLUTION,
        STALE_RESOLUTION,
        RUNTIME_ATTRIBUTE_MISMATCH
    }

    public RulesetCompositionIssue {
        code = Objects.requireNonNull(code, "code");
        moduleId = normalize(moduleId);
        relatedModuleId = normalize(relatedModuleId);
        detail = Objects.requireNonNull(detail, "detail").trim();
        if (detail.isEmpty()) throw new IllegalArgumentException("detail cannot be blank");
        candidateWinners = List.copyOf(Objects.requireNonNull(candidateWinners, "candidateWinners"));
    }

    /** Mantiene compatibili i call site precedenti, privi di candidati strutturati. */
    public RulesetCompositionIssue(
            Code code,
            String moduleId,
            String relatedModuleId,
            RuleFieldRef field,
            String detail) {
        this(code, moduleId, relatedModuleId, field, detail, List.of());
    }

    public String stablePath() {
        return field == null ? "" : field.path();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
