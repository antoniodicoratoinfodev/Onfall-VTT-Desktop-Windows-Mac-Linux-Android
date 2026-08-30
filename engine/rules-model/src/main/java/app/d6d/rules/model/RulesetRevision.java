package app.d6d.rules.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Revisione immutabile, risolta e verificata di un regolamento. */
public record RulesetRevision(
        String projectId,
        String revisionId,
        String version,
        String name,
        String description,
        RulesetOrigin origin,
        String baseCanonicalHash,
        RulesetRuntimeConfig runtime,
        List<RuleEntity> entities,
        String publishedAt,
        String canonicalHash,
        String runtimeHash) {

    public RulesetRevision {
        projectId = requireText(projectId, "projectId");
        revisionId = requireText(revisionId, "revisionId");
        version = requireText(version, "version");
        name = requireText(name, "name");
        description = description == null ? "" : description.trim();
        origin = Objects.requireNonNull(origin, "origin");
        baseCanonicalHash = baseCanonicalHash == null ? "" : baseCanonicalHash.trim();
        runtime = Objects.requireNonNull(runtime, "runtime");
        publishedAt = publishedAt == null ? "" : publishedAt.trim();

        ArrayList<RuleEntity> sorted = new ArrayList<>(Objects.requireNonNull(entities, "entities"));
        sorted.sort(java.util.Comparator.comparing(RuleEntity::id));
        HashSet<String> ids = new HashSet<>();
        for (RuleEntity entity : sorted) {
            Objects.requireNonNull(entity, "entities contains null");
            if (!ids.add(entity.id())) throw new IllegalArgumentException("Duplicate rule id: " + entity.id());
        }
        entities = List.copyOf(sorted);

        String expectedCanonical = RulesetCanonicalizer.canonicalHash(
                name, description, origin, baseCanonicalHash, runtime, entities);
        String expectedRuntime = RulesetCanonicalizer.runtimeHash(runtime, entities);
        canonicalHash = canonicalHash == null || canonicalHash.isBlank()
                ? expectedCanonical
                : canonicalHash.trim();
        runtimeHash = runtimeHash == null || runtimeHash.isBlank()
                ? expectedRuntime
                : runtimeHash.trim();
        if (!canonicalHash.equals(expectedCanonical)) {
            throw new IllegalArgumentException("Ruleset canonical hash does not match its content");
        }
        if (!runtimeHash.equals(expectedRuntime)) {
            throw new IllegalArgumentException("Ruleset runtime hash does not match its content");
        }
    }

    public static RulesetRevision create(
            String projectId,
            String revisionId,
            String version,
            String name,
            String description,
            RulesetOrigin origin,
            String baseCanonicalHash,
            RulesetRuntimeConfig runtime,
            List<RuleEntity> entities,
            String publishedAt) {
        return new RulesetRevision(projectId, revisionId, version, name, description, origin,
                baseCanonicalHash, runtime, entities, publishedAt, "", "");
    }

    public RulesetBinding binding() {
        return new RulesetBinding(projectId, revisionId, canonicalHash, runtimeHash,
                runtime.semanticsVersion(), name, false);
    }

    public RuleEntity entity(String id) {
        return entities.stream().filter(entity -> entity.id().equals(id)).findFirst().orElse(null);
    }

    public boolean readOnly() {
        return origin == RulesetOrigin.BUNDLED_STANDARD;
    }

    public long automationCount(RuleAutomationLevel level) {
        Objects.requireNonNull(level, "level");
        return entities.stream().filter(RuleEntity::enabled)
                .filter(entity -> entity.automationLevel() == level).count();
    }

    /** Compila tutte le primitive generiche e fallisce sui collegamenti non eseguibili. */
    public CompiledRuleset compile() {
        return RulesetCompiler.compile(this);
    }

    /**
     * Verifica se il motore tattico legacy può continuare a usare la propria CPU.
     * Testo, traduzioni e i parametri runtime già estratti sono sicuri; qualunque
     * cambiamento strutturale resta giocabile, ma richiede controllo manuale.
     */
    public boolean legacyCombatAutomationCompatibleWith(RulesetRevision reference) {
        Objects.requireNonNull(reference, "reference");
        if (!runtime.semanticsVersion().equals(reference.runtime.semanticsVersion())) return false;
        if (entities.size() != reference.entities.size()) return false;
        HashMap<String, RuleEntity> referenceById = new HashMap<>();
        reference.entities.forEach(entity -> referenceById.put(entity.id(), entity));
        for (RuleEntity candidate : entities) {
            RuleEntity baseline = referenceById.get(candidate.id());
            if (baseline == null
                    || candidate.kind() != baseline.kind()
                    || candidate.enabled() != baseline.enabled()
                    || candidate.automationLevel() != baseline.automationLevel()) {
                return false;
            }
            java.util.Map<String, String> candidateAttributes = comparableAttributes(candidate);
            java.util.Map<String, String> baselineAttributes = comparableAttributes(baseline);
            if (!candidateAttributes.equals(baselineAttributes)) return false;
        }
        return true;
    }

    private static java.util.Map<String, String> comparableAttributes(RuleEntity entity) {
        HashMap<String, String> result = new HashMap<>(entity.attributes());
        if (CoreRuleIds.CRITICAL_HIT.equals(entity.id())) {
            result.remove("criticalHitMinimumNatural");
            result.remove("naturalOneAlwaysMisses");
        } else if (CoreRuleIds.EXHAUSTION.equals(entity.id())) {
            result.remove("maximumExhaustion");
            result.remove("d20PenaltyPerLevel");
            result.remove("speedPenaltyFeetPerLevel");
        } else if (CoreRuleIds.PROFICIENCY.equals(entity.id())) {
            result.remove("base");
            result.remove("levelsPerIncrease");
            result.remove("maximum");
        }
        return java.util.Map.copyOf(result);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}
