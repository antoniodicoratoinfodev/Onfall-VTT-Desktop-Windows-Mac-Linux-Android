package app.d6d.rules.model;

import java.util.Objects;

/** Riferimento esatto a un modulo; non significa mai “ultima versione”. */
public record RulesetModuleRef(String moduleId, String canonicalHash) {
    public RulesetModuleRef {
        moduleId = requireText(moduleId, "moduleId");
        canonicalHash = requireText(canonicalHash, "canonicalHash");
    }

    public static RulesetModuleRef from(RulesetModule module) {
        Objects.requireNonNull(module, "module");
        return new RulesetModuleRef(module.id(), module.canonicalHash());
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}
