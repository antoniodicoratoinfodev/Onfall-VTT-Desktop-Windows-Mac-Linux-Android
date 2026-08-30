package app.d6d.rules.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

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
