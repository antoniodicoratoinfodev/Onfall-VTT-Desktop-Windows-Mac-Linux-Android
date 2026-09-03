package app.d6d.rules.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Compone una base e una sequenza ordinata di moduli senza usare il principio
 * implicito "l'ultimo vince".
 *
 * <p>Le dipendenze sono riferimenti esatti per hash. Due moduli possono
 * modificare lo stesso campo senza intervento soltanto quando propongono lo
 * stesso valore; ogni altro conflitto richiede una {@link RulesetConflictResolution}.</p>
 */
public final class RulesetComposer {
    private RulesetComposer() {
    }

    public static RulesetCompositionResult compose(
            RulesetRevision base,
            List<RulesetModule> orderedModules,
            List<RulesetConflictResolution> resolutions,
            String projectId,
            String revisionId,
            String version,
            String name,
            String description,
            RulesetOrigin origin,
            String publishedAt) {
        Objects.requireNonNull(base, "base");
        return compose(base, orderedModules, resolutions, base.runtime(), projectId, revisionId,
                version, name, description, origin, publishedAt);
    }

    /**
     * Variante che consente di cambiare i parametri dell'ABI runtime corrente.
     * I moduli che toccano gli attributi specchio del runtime devono proporre
     * valori coerenti con questa configurazione, altrimenti la composizione fallisce.
     */
    public static RulesetCompositionResult compose(
            RulesetRevision base,
            List<RulesetModule> orderedModules,
            List<RulesetConflictResolution> resolutions,
            RulesetRuntimeConfig runtime,
            String projectId,
            String revisionId,
            String version,
            String name,
            String description,
            RulesetOrigin origin,
            String publishedAt) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(runtime, "runtime");
        List<RulesetModule> modules = List.copyOf(Objects.requireNonNull(orderedModules, "orderedModules"));
        List<RulesetConflictResolution> requestedResolutions = List.copyOf(
                Objects.requireNonNull(resolutions, "resolutions"));
        modules.forEach(value -> Objects.requireNonNull(value, "orderedModules contains null"));
        requestedResolutions.forEach(value -> Objects.requireNonNull(value, "resolutions contains null"));

        ArrayList<RulesetCompositionIssue> issues = new ArrayList<>();
        if (!base.runtime().semanticsVersion().equals(runtime.semanticsVersion())) {
            issues.add(issue(RulesetCompositionIssue.Code.SEMANTICS_MISMATCH, "", "", null,
                    "Output runtime semantics " + runtime.semanticsVersion()
                            + " differs from base semantics " + base.runtime().semanticsVersion()));
        }

        LinkedHashMap<String, IndexedModule> byId = new LinkedHashMap<>();
        HashMap<String, IndexedModule> byHash = new HashMap<>();
        for (int index = 0; index < modules.size(); index++) {
            RulesetModule module = modules.get(index);
            IndexedModule indexed = new IndexedModule(module, index);
            IndexedModule sameId = byId.putIfAbsent(module.id(), indexed);
            if (sameId != null) {
                issues.add(issue(RulesetCompositionIssue.Code.DUPLICATE_MODULE,
                        module.id(), sameId.module().id(), null,
                        "Module id appears more than once in the ordered composition: " + module.id()));
            }
            IndexedModule sameHash = byHash.putIfAbsent(module.canonicalHash(), indexed);
            if (sameHash != null) {
                issues.add(issue(RulesetCompositionIssue.Code.DUPLICATE_MODULE,
                        module.id(), sameHash.module().id(), null,
                        "Module hash appears more than once in the ordered composition: "
                                + module.canonicalHash()));
            }
            if (!module.requiredSemanticsVersion().equals(base.runtime().semanticsVersion())) {
                issues.add(issue(RulesetCompositionIssue.Code.SEMANTICS_MISMATCH,
                        module.id(), "", null,
                        "Module requires semantics " + module.requiredSemanticsVersion()
                                + " but the base provides " + base.runtime().semanticsVersion()));
            }
        }

        validateDependencies(modules, byId, issues);
        validateIncompatibilities(modules, issues);

        LinkedHashSet<String> knownEntityIds = new LinkedHashSet<>();
        base.entities().forEach(entity -> knownEntityIds.add(entity.id()));
        LinkedHashMap<String, RulesetModule> additionOwners = new LinkedHashMap<>();
        LinkedHashMap<RuleFieldRef, List<FieldEdit>> editsByField = new LinkedHashMap<>();
        for (RulesetModule module : modules) {
            for (RuleEntity addition : module.additions()) {
                if (!knownEntityIds.add(addition.id())) {
                    issues.add(issue(RulesetCompositionIssue.Code.ADDITION_COLLISION,
                            module.id(), "", null,
                            "Addition collides with an existing rule: " + addition.id()));
                } else {
                    additionOwners.put(addition.id(), module);
                }
            }
            for (RulePatch patch : module.patches()) {
                if (!knownEntityIds.contains(patch.targetEntityId())) {
                    issues.add(issue(RulesetCompositionIssue.Code.PATCH_TARGET_MISSING,
                            module.id(), "", null,
                            "Patch target does not exist at this point in the module order: "
                                    + patch.targetEntityId()));
                    continue;
                }
                RulesetModule additionOwner = additionOwners.get(patch.targetEntityId());
                if (additionOwner != null && !hasExactDependency(module, additionOwner)) {
                    issues.add(issue(RulesetCompositionIssue.Code.UNDECLARED_DEPENDENCY,
                            module.id(), additionOwner.id(), null,
                            "A patch of a rule introduced by another module requires an exact dependency: "
                                    + patch.targetEntityId()));
                }
                editsOf(patch).forEach((field, value) -> editsByField
                        .computeIfAbsent(field, ignored -> new ArrayList<>())
                        .add(new FieldEdit(module, value)));
            }
        }

        ResolutionIndex resolutionIndex = indexResolutions(requestedResolutions, byHash.keySet(), issues);
        LinkedHashMap<RuleFieldRef, FieldEdit> winners = selectWinners(
                editsByField, resolutionIndex.byField(), issues);
        for (Map.Entry<RuleFieldRef, RulesetConflictResolution> entry : resolutionIndex.byField().entrySet()) {
            if (!editsByField.containsKey(entry.getKey())
                    && resolutionIndex.globallyValidFields().contains(entry.getKey())) {
                issues.add(issue(RulesetCompositionIssue.Code.STALE_RESOLUTION,
                        "", "", entry.getKey(),
                        "Resolution targets a field that no selected module modifies"));
            }
        }
        validateRuntimeMirrors(runtime, modules, winners, issues);

        if (!issues.isEmpty()) throw new RulesetCompositionException(issues);

        LinkedHashMap<String, RuleEntity> effective = new LinkedHashMap<>();
        base.entities().forEach(entity -> effective.put(entity.id(), entity));
        for (RulesetModule module : modules) {
            module.additions().forEach(addition -> effective.put(addition.id(), addition));
            for (RulePatch patch : module.patches()) {
                RulePatch selected = selectedPatch(module, patch, winners);
                if (selected == null) continue;
                RuleEntity source = effective.get(patch.targetEntityId());
                effective.put(source.id(), selected.apply(source, module.origin()));
            }
        }
        RulesetResolver.synchronizeRuntimeAttributes(effective, runtime);

        RulesetRevision revision = RulesetRevision.create(
                projectId, revisionId, version, name, description, origin,
                base.canonicalHash(), runtime, List.copyOf(effective.values()), publishedAt);
        RulesetCompositionLock lock = RulesetCompositionLock.create(
                base.canonicalHash(), modules.stream().map(RulesetModule::reference).toList(),
                requestedResolutions);
        return new RulesetCompositionResult(revision, lock);
    }

    private static void validateDependencies(
            List<RulesetModule> modules,
            Map<String, IndexedModule> byId,
            List<RulesetCompositionIssue> issues) {
        for (int index = 0; index < modules.size(); index++) {
            RulesetModule module = modules.get(index);
            for (RulesetModuleRef dependency : module.dependencies()) {
                IndexedModule available = byId.get(dependency.moduleId());
                if (available == null) {
                    issues.add(issue(RulesetCompositionIssue.Code.MISSING_DEPENDENCY,
                            module.id(), dependency.moduleId(), null,
                            "Required module is absent: " + dependency.moduleId()));
                } else if (!available.module().canonicalHash().equals(dependency.canonicalHash())) {
                    issues.add(issue(RulesetCompositionIssue.Code.DEPENDENCY_HASH_MISMATCH,
                            module.id(), dependency.moduleId(), null,
                            "Required hash " + dependency.canonicalHash() + " differs from selected hash "
                                    + available.module().canonicalHash()));
                } else if (available.index() >= index) {
                    issues.add(issue(RulesetCompositionIssue.Code.DEPENDENCY_ORDER,
                            module.id(), dependency.moduleId(), null,
                            "Dependency must appear before the module that requires it"));
                }
            }
        }
    }

    private static void validateIncompatibilities(
            List<RulesetModule> modules,
            List<RulesetCompositionIssue> issues) {
        for (int left = 0; left < modules.size(); left++) {
            RulesetModule first = modules.get(left);
            for (int right = left + 1; right < modules.size(); right++) {
                RulesetModule second = modules.get(right);
                if (first.incompatibleModuleIds().contains(second.id())
                        || second.incompatibleModuleIds().contains(first.id())) {
                    issues.add(issue(RulesetCompositionIssue.Code.INCOMPATIBLE_MODULES,
                            first.id(), second.id(), null,
                            "Selected modules declare each other incompatible"));
                }
            }
        }
    }

    private static ResolutionIndex indexResolutions(
            List<RulesetConflictResolution> resolutions,
            Set<String> selectedModuleHashes,
            List<RulesetCompositionIssue> issues) {
        LinkedHashMap<RuleFieldRef, RulesetConflictResolution> byField = new LinkedHashMap<>();
        HashSet<RuleFieldRef> globallyValidFields = new HashSet<>();
        for (RulesetConflictResolution resolution : resolutions) {
            if (byField.putIfAbsent(resolution.field(), resolution) != null) {
                issues.add(issue(RulesetCompositionIssue.Code.INVALID_RESOLUTION,
                        "", "", resolution.field(),
                        "More than one resolution targets the same field"));
                continue;
            }
            if (!selectedModuleHashes.contains(resolution.winnerModuleHash())) {
                issues.add(issue(RulesetCompositionIssue.Code.INVALID_RESOLUTION,
                        "", "", resolution.field(),
                        "Winner hash is not part of the selected module composition: "
                                + resolution.winnerModuleHash()));
            } else {
                globallyValidFields.add(resolution.field());
            }
        }
        return new ResolutionIndex(Map.copyOf(byField), Set.copyOf(globallyValidFields));
    }

    private static LinkedHashMap<RuleFieldRef, FieldEdit> selectWinners(
            Map<RuleFieldRef, List<FieldEdit>> editsByField,
            Map<RuleFieldRef, RulesetConflictResolution> resolutions,
            List<RulesetCompositionIssue> issues) {
        LinkedHashMap<RuleFieldRef, FieldEdit> winners = new LinkedHashMap<>();
        editsByField.entrySet().stream()
                .sorted(Map.Entry.comparingByKey((left, right) -> left.path().compareTo(right.path())))
                .forEach(entry -> {
                    RuleFieldRef field = entry.getKey();
                    List<FieldEdit> edits = entry.getValue();
                    boolean conflict = edits.stream().map(FieldEdit::value).distinct().count() > 1;
                    RulesetConflictResolution resolution = resolutions.get(field);
                    if (!conflict) {
                        winners.put(field, edits.get(0));
                        if (resolution != null) {
                            issues.add(issue(RulesetCompositionIssue.Code.STALE_RESOLUTION,
                                    "", "", field,
                                    "Resolution is unnecessary because every module proposes the same value"));
                        }
                        return;
                    }
                    if (resolution == null) {
                        List<RulesetModuleRef> candidateWinners = edits.stream()
                                .map(edit -> edit.module().reference())
                                .distinct()
                                .toList();
                        String participants = candidateWinners.stream().map(RulesetModuleRef::moduleId)
                                .reduce((left, right) -> left + ", " + right).orElse("");
                        issues.add(issue(RulesetCompositionIssue.Code.FIELD_CONFLICT,
                                participants, "", field,
                                "Different values require an explicit winning module", candidateWinners));
                        return;
                    }
                    FieldEdit winner = edits.stream()
                            .filter(edit -> edit.module().canonicalHash().equals(resolution.winnerModuleHash()))
                            .findFirst().orElse(null);
                    if (winner == null) {
                        issues.add(issue(RulesetCompositionIssue.Code.INVALID_RESOLUTION,
                                "", "", field,
                                "Winning module does not modify the conflicting field"));
                    } else {
                        winners.put(field, winner);
                    }
                });
        return winners;
    }

    private static void validateRuntimeMirrors(
            RulesetRuntimeConfig runtime,
            List<RulesetModule> modules,
            Map<RuleFieldRef, FieldEdit> winners,
            List<RulesetCompositionIssue> issues) {
        for (String entityId : List.of(
                CoreRuleIds.CRITICAL_HIT, CoreRuleIds.EXHAUSTION, CoreRuleIds.PROFICIENCY)) {
            runtime.attributesFor(entityId).forEach((key, expected) -> {
                RuleFieldRef field = RuleFieldRef.attribute(entityId, key);
                FieldEdit edit = winners.get(field);
                if (edit != null && !edit.value().equals(FieldValue.present(expected))) {
                    issues.add(issue(RulesetCompositionIssue.Code.RUNTIME_ATTRIBUTE_MISMATCH,
                            edit.module().id(), "", field,
                            "Module value differs from the authoritative runtime value " + expected));
                    return;
                }
                if (edit != null) return;
                for (RulesetModule module : modules) {
                    RuleEntity addition = module.additions().stream()
                            .filter(candidate -> candidate.id().equals(entityId))
                            .findFirst().orElse(null);
                    if (addition == null || !addition.attributes().containsKey(key)) continue;
                    if (!expected.equals(addition.attributes().get(key))) {
                        issues.add(issue(RulesetCompositionIssue.Code.RUNTIME_ATTRIBUTE_MISMATCH,
                                module.id(), "", field,
                                "Added rule value differs from the authoritative runtime value " + expected));
                    }
                    break;
                }
            });
        }
    }

    private static boolean hasExactDependency(RulesetModule module, RulesetModule dependency) {
        return module.dependencies().stream().anyMatch(reference ->
                reference.moduleId().equals(dependency.id())
                        && reference.canonicalHash().equals(dependency.canonicalHash()));
    }

    private static Map<RuleFieldRef, FieldValue> editsOf(RulePatch patch) {
        LinkedHashMap<RuleFieldRef, FieldValue> result = new LinkedHashMap<>();
        if (patch.nameOverride() != null) {
            result.put(RuleFieldRef.name(patch.targetEntityId()), FieldValue.present(patch.nameOverride()));
        }
        if (patch.descriptionOverride() != null) {
            result.put(RuleFieldRef.description(patch.targetEntityId()),
                    FieldValue.present(patch.descriptionOverride()));
        }
        if (patch.kindOverride() != null) {
            result.put(RuleFieldRef.kind(patch.targetEntityId()), FieldValue.present(patch.kindOverride()));
        }
        if (patch.enabledOverride() != null) {
            result.put(RuleFieldRef.enabled(patch.targetEntityId()), FieldValue.present(patch.enabledOverride()));
        }
        if (patch.automationLevelOverride() != null) {
            result.put(RuleFieldRef.automationLevel(patch.targetEntityId()),
                    FieldValue.present(patch.automationLevelOverride()));
        }
        if (patch.tagsOverride() != null) {
            result.put(RuleFieldRef.tags(patch.targetEntityId()), FieldValue.present(patch.tagsOverride()));
        }
        patch.removedAttributes().forEach(key -> result.put(
                RuleFieldRef.attribute(patch.targetEntityId(), key), FieldValue.tombstone()));
        patch.attributeOverrides().forEach((key, value) -> result.put(
                RuleFieldRef.attribute(patch.targetEntityId(), key), FieldValue.present(value)));
        return result;
    }

    private static RulePatch selectedPatch(
            RulesetModule module,
            RulePatch source,
            Map<RuleFieldRef, FieldEdit> winners) {
        String target = source.targetEntityId();
        LocalizedRuleText name = selected(module, winners.get(RuleFieldRef.name(target)))
                ? source.nameOverride() : null;
        LocalizedRuleText description = selected(module, winners.get(RuleFieldRef.description(target)))
                ? source.descriptionOverride() : null;
        RuleKind kind = selected(module, winners.get(RuleFieldRef.kind(target)))
                ? source.kindOverride() : null;
        Boolean enabled = selected(module, winners.get(RuleFieldRef.enabled(target)))
                ? source.enabledOverride() : null;
        RuleAutomationLevel automation = selected(module, winners.get(RuleFieldRef.automationLevel(target)))
                ? source.automationLevelOverride() : null;
        List<String> tags = selected(module, winners.get(RuleFieldRef.tags(target)))
                ? source.tagsOverride() : null;

        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        source.attributeOverrides().forEach((key, value) -> {
            if (selected(module, winners.get(RuleFieldRef.attribute(target, key)))) {
                attributes.put(key, value);
            }
        });
        LinkedHashSet<String> removed = new LinkedHashSet<>();
        source.removedAttributes().forEach(key -> {
            if (!source.attributeOverrides().containsKey(key)
                    && selected(module, winners.get(RuleFieldRef.attribute(target, key)))) {
                removed.add(key);
            }
        });
        if (name == null && description == null && kind == null && enabled == null && automation == null
                && tags == null && attributes.isEmpty() && removed.isEmpty()) {
            return null;
        }
        return new RulePatch(source.id(), target, name, description, attributes, removed,
                enabled, kind, automation, tags);
    }

    private static boolean selected(RulesetModule module, FieldEdit winner) {
        return winner != null && winner.module().canonicalHash().equals(module.canonicalHash());
    }

    private static RulesetCompositionIssue issue(
            RulesetCompositionIssue.Code code,
            String moduleId,
            String relatedModuleId,
            RuleFieldRef field,
            String detail) {
        return new RulesetCompositionIssue(code, moduleId, relatedModuleId, field, detail);
    }

    private static RulesetCompositionIssue issue(
            RulesetCompositionIssue.Code code,
            String moduleId,
            String relatedModuleId,
            RuleFieldRef field,
            String detail,
            List<RulesetModuleRef> candidateWinners) {
        return new RulesetCompositionIssue(
                code, moduleId, relatedModuleId, field, detail, candidateWinners);
    }

    private record IndexedModule(RulesetModule module, int index) {
    }

    private record FieldEdit(RulesetModule module, FieldValue value) {
    }

    private record FieldValue(boolean removed, Object value) {
        private FieldValue {
            if (removed == (value != null)) {
                throw new IllegalArgumentException("A field value must be present or removed");
            }
        }

        static FieldValue present(Object value) {
            return new FieldValue(false, Objects.requireNonNull(value, "value"));
        }

        static FieldValue tombstone() {
            return new FieldValue(true, null);
        }
    }

    private record ResolutionIndex(
            Map<RuleFieldRef, RulesetConflictResolution> byField,
            Set<RuleFieldRef> globallyValidFields) {
    }
}
