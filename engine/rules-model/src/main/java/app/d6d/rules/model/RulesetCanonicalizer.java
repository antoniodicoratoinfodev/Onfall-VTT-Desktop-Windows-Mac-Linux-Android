package app.d6d.rules.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Canonicalizzazione deterministica, indipendente dall'ordine delle mappe in ingresso. */
public final class RulesetCanonicalizer {
    private RulesetCanonicalizer() {
    }

    public static String canonicalHash(
            String name,
            String description,
            RulesetOrigin origin,
            String baseCanonicalHash,
            RulesetRuntimeConfig runtime,
            List<RuleEntity> entities) {
        StringBuilder value = new StringBuilder("onfall-rules-document-v1");
        token(value, name);
        token(value, description);
        token(value, origin.name());
        token(value, baseCanonicalHash);
        appendRuntime(value, runtime);
        sortedEntities(entities).forEach(entity -> appendEntity(value, entity, true));
        return sha256(value.toString());
    }

    public static String runtimeHash(RulesetRuntimeConfig runtime, List<RuleEntity> entities) {
        StringBuilder value = new StringBuilder("onfall-rules-runtime-v1");
        appendRuntime(value, runtime);
        sortedEntities(entities).forEach(entity -> appendEntity(value, entity, false));
        return sha256(value.toString());
    }

    /** Hash di contenuto di una singola entità, usato soltanto per invalidare proiezioni UI. */
    public static String entityContentHash(RuleEntity entity) {
        StringBuilder value = new StringBuilder("onfall-rule-entity-v1");
        appendEntity(value, java.util.Objects.requireNonNull(entity, "entity"), true);
        return sha256(value.toString());
    }

    /** Hash del documento modulo; include presentazione, licenze e operazioni di patch. */
    public static String moduleHash(
            String id,
            String version,
            LocalizedRuleText name,
            LocalizedRuleText description,
            RulesetOrigin origin,
            String requiredSemanticsVersion,
            List<RulesetModuleRef> dependencies,
            Set<String> incompatibleModuleIds,
            List<RulePatch> patches,
            List<RuleEntity> additions) {
        StringBuilder value = new StringBuilder("onfall-rules-module-v1");
        token(value, id);
        token(value, version);
        appendModuleLocalized(value, name);
        appendModuleLocalized(value, description);
        token(value, origin.name());
        token(value, requiredSemanticsVersion);
        token(value, "dependencies");
        token(value, dependencies.size());
        dependencies.stream()
                .sorted(Comparator.comparing(RulesetModuleRef::moduleId)
                        .thenComparing(RulesetModuleRef::canonicalHash))
                .forEach(reference -> {
                    token(value, reference.moduleId());
                    token(value, reference.canonicalHash());
                });
        token(value, "incompatibleModuleIds");
        token(value, incompatibleModuleIds.size());
        incompatibleModuleIds.stream().sorted().forEach(candidate -> token(value, candidate));
        token(value, "patches");
        token(value, patches.size());
        patches.stream().sorted(Comparator.comparing(RulePatch::id))
                .forEach(patch -> appendPatch(value, patch));
        token(value, "additions");
        token(value, additions.size());
        sortedEntities(additions).forEach(entity -> appendModuleEntity(value, entity));
        return sha256(value.toString());
    }

    /** L'ordine dei moduli è semantico; l'ordine delle risoluzioni non lo è. */
    public static String compositionLockHash(
            String baseCanonicalHash,
            List<RulesetModuleRef> modules,
            List<RulesetConflictResolution> resolutions) {
        StringBuilder value = new StringBuilder("onfall-rules-composition-lock-v1");
        token(value, baseCanonicalHash);
        token(value, "modules");
        token(value, modules.size());
        modules.forEach(reference -> {
            token(value, reference.moduleId());
            token(value, reference.canonicalHash());
        });
        token(value, "resolutions");
        token(value, resolutions.size());
        resolutions.stream().sorted(Comparator.comparing(candidate -> candidate.field().path()))
                .forEach(resolution -> {
                    appendField(value, resolution.field());
                    token(value, resolution.winnerModuleHash());
                });
        return sha256(value.toString());
    }

    private static List<RuleEntity> sortedEntities(List<RuleEntity> entities) {
        ArrayList<RuleEntity> sorted = new ArrayList<>(entities);
        sorted.sort(Comparator.comparing(RuleEntity::id));
        return sorted;
    }

    private static void appendRuntime(StringBuilder out, RulesetRuntimeConfig runtime) {
        token(out, runtime.semanticsVersion());
        token(out, runtime.criticalHitMinimumNatural());
        token(out, runtime.naturalOneAlwaysMisses());
        token(out, runtime.maximumExhaustion());
        token(out, runtime.exhaustionD20PenaltyPerLevel());
        token(out, runtime.exhaustionSpeedPenaltyFeetPerLevel());
        token(out, runtime.proficiencyBonusBase());
        token(out, runtime.proficiencyLevelsPerIncrease());
        token(out, runtime.proficiencyBonusMaximum());
    }

    private static void appendEntity(StringBuilder out, RuleEntity entity, boolean includePresentation) {
        token(out, entity.id());
        token(out, entity.kind().name());
        token(out, entity.enabled());
        token(out, entity.automationLevel().name());
        entity.attributes().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            token(out, entry.getKey());
            token(out, entry.getValue());
        });
        if (!includePresentation) return;
        token(out, entity.origin().name());
        token(out, entity.derivedFrom());
        appendLocalized(out, entity.name());
        appendLocalized(out, entity.description());
        entity.tags().forEach(tag -> token(out, tag));
        token(out, entity.source());
        token(out, entity.license());
        token(out, entity.sourcePage());
    }

    private static void appendPatch(StringBuilder out, RulePatch patch) {
        token(out, patch.id());
        token(out, patch.targetEntityId());
        token(out, patch.nameOverride() != null);
        if (patch.nameOverride() != null) appendModuleLocalized(out, patch.nameOverride());
        token(out, patch.descriptionOverride() != null);
        if (patch.descriptionOverride() != null) appendModuleLocalized(out, patch.descriptionOverride());
        token(out, "attributeOverrides");
        token(out, patch.attributeOverrides().size());
        patch.attributeOverrides().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            token(out, entry.getKey());
            token(out, entry.getValue());
        });
        token(out, "removedAttributes");
        token(out, patch.removedAttributes().size());
        patch.removedAttributes().stream().sorted().forEach(attribute -> token(out, attribute));
        token(out, patch.enabledOverride() == null ? "" : patch.enabledOverride());
        token(out, patch.kindOverride() == null ? "" : patch.kindOverride().name());
        token(out, patch.automationLevelOverride() == null ? "" : patch.automationLevelOverride().name());
        token(out, patch.tagsOverride() != null);
        if (patch.tagsOverride() != null) {
            token(out, patch.tagsOverride().size());
            patch.tagsOverride().forEach(tag -> token(out, tag));
        }
    }

    /** Formato v1 dei moduli: non altera gli hash storici delle revisioni. */
    private static void appendModuleEntity(StringBuilder out, RuleEntity entity) {
        token(out, entity.id());
        token(out, entity.kind().name());
        token(out, entity.enabled());
        token(out, entity.automationLevel().name());
        token(out, "attributes");
        token(out, entity.attributes().size());
        entity.attributes().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            token(out, entry.getKey());
            token(out, entry.getValue());
        });
        token(out, entity.origin().name());
        token(out, entity.derivedFrom());
        appendModuleLocalized(out, entity.name());
        appendModuleLocalized(out, entity.description());
        token(out, "tags");
        token(out, entity.tags().size());
        entity.tags().forEach(tag -> token(out, tag));
        token(out, entity.source());
        token(out, entity.license());
        token(out, entity.sourcePage());
    }

    private static void appendModuleLocalized(StringBuilder out, LocalizedRuleText text) {
        token(out, text.primaryLanguage());
        token(out, "values");
        token(out, text.values().size());
        text.values().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            token(out, entry.getKey());
            token(out, entry.getValue());
        });
    }

    private static void appendField(StringBuilder out, RuleFieldRef field) {
        token(out, field.entityId());
        token(out, field.field().name());
        token(out, field.attributeKey());
    }

    private static void appendLocalized(StringBuilder out, LocalizedRuleText text) {
        token(out, text.primaryLanguage());
        text.values().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            token(out, entry.getKey());
            token(out, entry.getValue());
        });
    }

    private static void token(StringBuilder out, Object raw) {
        String value = Normalizer.normalize(String.valueOf(raw), Normalizer.Form.NFC)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        out.append(value.length()).append(':').append(value).append(';');
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte part : digest) result.append(String.format("%02x", part & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }
}
