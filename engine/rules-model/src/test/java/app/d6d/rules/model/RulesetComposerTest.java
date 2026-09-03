package app.d6d.rules.model;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RulesetComposerTest {
    private static final String TARGET = "core:test";

    @Test
    void composesDisjointModulesAndProducesAnExactReproducibleLock() {
        RulesetRevision base = base(entity(TARGET, RulesetOrigin.BUNDLED_STANDARD, Map.of("a", "1", "b", "2")));
        RulesetModule first = module("module:first", List.of(), Set.of(),
                List.of(attributePatch("patch:first", TARGET, "a", "10")), List.of());
        RulesetModule second = module("module:second", List.of(first.reference()), Set.of(),
                List.of(attributePatch("patch:second", TARGET, "b", "20")), List.of());

        RulesetCompositionResult result = compose(base, List.of(first, second), List.of());
        RulesetCompositionResult repeated = compose(base, List.of(first, second), List.of());

        assertEquals(Map.of("a", "10", "b", "20"), result.revision().entity(TARGET).attributes());
        assertEquals(Map.of("a", "1", "b", "2"), base.entity(TARGET).attributes());
        assertEquals(List.of(first.reference(), second.reference()), result.lock().modules());
        assertEquals(result.lock().canonicalHash(), repeated.lock().canonicalHash());
        assertEquals(result.revision().canonicalHash(), repeated.revision().canonicalHash());
    }

    @Test
    void differentValuesNeedAnExplicitFieldWinner() {
        RulesetRevision base = base(entity(TARGET, RulesetOrigin.BUNDLED_STANDARD, Map.of("a", "1")));
        RulesetModule first = module("module:first", List.of(), Set.of(),
                List.of(attributePatch("patch:first", TARGET, "a", "10")), List.of());
        RulesetModule second = module("module:second", List.of(), Set.of(),
                List.of(attributePatch("patch:second", TARGET, "a", "20")), List.of());

        RulesetCompositionException conflict = assertThrows(RulesetCompositionException.class,
                () -> compose(base, List.of(first, second), List.of()));
        assertEquals(Set.of(RulesetCompositionIssue.Code.FIELD_CONFLICT), codes(conflict));
        RulesetCompositionIssue conflictIssue = conflict.issues().get(0);
        assertEquals(RuleFieldRef.attribute(TARGET, "a"), conflictIssue.field());
        assertEquals(List.of(first.reference(), second.reference()), conflictIssue.candidateWinners());

        RulesetCompositionException reorderedWithDuplicate = assertThrows(RulesetCompositionException.class,
                () -> compose(base, List.of(second, first, second), List.of()));
        RulesetCompositionIssue reorderedConflict = reorderedWithDuplicate.issues().stream()
                .filter(issue -> issue.code() == RulesetCompositionIssue.Code.FIELD_CONFLICT)
                .findFirst()
                .orElseThrow();
        assertEquals(List.of(second.reference(), first.reference()), reorderedConflict.candidateWinners());

        RulesetConflictResolution resolution = new RulesetConflictResolution(
                RuleFieldRef.attribute(TARGET, "a"), second.canonicalHash());
        RulesetCompositionResult resolved = compose(base, List.of(first, second), List.of(resolution));

        assertEquals("20", resolved.revision().entity(TARGET).attributes().get("a"));
        assertEquals(List.of(resolution), resolved.lock().resolutions());
    }

    @Test
    void equalEditsAreCompatibleButAResolutionForThemIsRejectedAsStale() {
        RulesetRevision base = base(entity(TARGET, RulesetOrigin.BUNDLED_STANDARD, Map.of("a", "1")));
        RulesetModule first = module("module:first", List.of(), Set.of(),
                List.of(attributePatch("patch:first", TARGET, "a", "10")), List.of());
        RulesetModule second = module("module:second", List.of(), Set.of(),
                List.of(attributePatch("patch:second", TARGET, "a", "10")), List.of());

        assertEquals("10", compose(base, List.of(first, second), List.of())
                .revision().entity(TARGET).attributes().get("a"));

        RulesetCompositionException stale = assertThrows(RulesetCompositionException.class,
                () -> compose(base, List.of(first, second), List.of(new RulesetConflictResolution(
                        RuleFieldRef.attribute(TARGET, "a"), second.canonicalHash()))));
        assertEquals(Set.of(RulesetCompositionIssue.Code.STALE_RESOLUTION), codes(stale));
    }

    @Test
    void dependenciesAreExactAndMustPrecedeTheirConsumers() {
        RulesetRevision base = base(entity(TARGET, RulesetOrigin.BUNDLED_STANDARD, Map.of()));
        RulesetModule dependency = module("module:dependency", List.of(), Set.of(), List.of(), List.of());
        RulesetModule missing = module("module:missing-consumer",
                List.of(new RulesetModuleRef("module:absent", "absent-hash")), Set.of(), List.of(), List.of());
        RulesetModule wrongHash = module("module:wrong-hash-consumer",
                List.of(new RulesetModuleRef(dependency.id(), "wrong-hash")), Set.of(), List.of(), List.of());
        RulesetModule wrongOrder = module("module:wrong-order-consumer",
                List.of(dependency.reference()), Set.of(), List.of(), List.of());

        assertEquals(Set.of(RulesetCompositionIssue.Code.MISSING_DEPENDENCY),
                codes(assertCompositionFails(base, List.of(missing))));
        assertEquals(Set.of(RulesetCompositionIssue.Code.DEPENDENCY_HASH_MISMATCH),
                codes(assertCompositionFails(base, List.of(dependency, wrongHash))));
        assertEquals(Set.of(RulesetCompositionIssue.Code.DEPENDENCY_ORDER),
                codes(assertCompositionFails(base, List.of(wrongOrder, dependency))));
    }

    @Test
    void patchingAnotherModulesAdditionRequiresThatExactModuleAsADependency() {
        RulesetRevision base = base();
        String addedId = "homebrew:added";
        RulesetModule provider = module("module:provider", List.of(), Set.of(), List.of(),
                List.of(entity(addedId, RulesetOrigin.HOMEBREW, Map.of("value", "1"))));
        RulesetModule undeclared = module("module:undeclared", List.of(), Set.of(),
                List.of(attributePatch("patch:undeclared", addedId, "value", "2")), List.of());

        RulesetCompositionException failure = assertCompositionFails(base, List.of(provider, undeclared));
        assertEquals(Set.of(RulesetCompositionIssue.Code.UNDECLARED_DEPENDENCY), codes(failure));

        RulesetModule declared = module("module:declared", List.of(provider.reference()), Set.of(),
                List.of(attributePatch("patch:declared", addedId, "value", "2")), List.of());
        assertEquals("2", compose(base, List.of(provider, declared), List.of())
                .revision().entity(addedId).attributes().get("value"));
    }

    @Test
    void reportsIncompatibilityAdditionCollisionAndMissingPatchTargetTogether() {
        RulesetRevision base = base(entity(TARGET, RulesetOrigin.BUNDLED_STANDARD, Map.of()));
        RulesetModule first = module("module:first", List.of(), Set.of("module:second"), List.of(),
                List.of(entity(TARGET, RulesetOrigin.HOMEBREW, Map.of())));
        RulesetModule second = module("module:second", List.of(), Set.of(),
                List.of(attributePatch("patch:missing", "core:absent", "x", "1")), List.of());

        RulesetCompositionException failure = assertCompositionFails(base, List.of(first, second));

        assertEquals(Set.of(
                RulesetCompositionIssue.Code.INCOMPATIBLE_MODULES,
                RulesetCompositionIssue.Code.ADDITION_COLLISION,
                RulesetCompositionIssue.Code.PATCH_TARGET_MISSING), codes(failure));
    }

    @Test
    void aWinnerMustActuallyContributeToTheConflictingField() {
        RulesetRevision base = base(entity(TARGET, RulesetOrigin.BUNDLED_STANDARD, Map.of("a", "1")));
        RulesetModule first = module("module:first", List.of(), Set.of(),
                List.of(attributePatch("patch:first", TARGET, "a", "10")), List.of());
        RulesetModule second = module("module:second", List.of(), Set.of(),
                List.of(attributePatch("patch:second", TARGET, "a", "20")), List.of());
        RulesetModule unrelated = module("module:unrelated", List.of(), Set.of(), List.of(), List.of());

        RulesetCompositionException failure = assertThrows(RulesetCompositionException.class,
                () -> compose(base, List.of(first, second, unrelated), List.of(new RulesetConflictResolution(
                        RuleFieldRef.attribute(TARGET, "a"), unrelated.canonicalHash()))));

        assertEquals(Set.of(RulesetCompositionIssue.Code.INVALID_RESOLUTION), codes(failure));
    }

    @Test
    void runtimeMirrorEditsMustMatchTheRuntimeActuallyUsedByCombat() {
        RuleEntity critical = entity(CoreRuleIds.CRITICAL_HIT, RulesetOrigin.BUNDLED_STANDARD,
                RulesetRuntimeConfig.standardSrd521().attributesFor(CoreRuleIds.CRITICAL_HIT));
        RulesetRevision base = base(critical);
        RulesetModule expandedCritical = module("module:expanded-critical", List.of(), Set.of(),
                List.of(attributePatch("patch:critical", CoreRuleIds.CRITICAL_HIT,
                        "criticalHitMinimumNatural", "19")), List.of());

        RulesetCompositionException mismatch = assertCompositionFails(base, List.of(expandedCritical));
        assertEquals(Set.of(RulesetCompositionIssue.Code.RUNTIME_ATTRIBUTE_MISMATCH), codes(mismatch));

        RulesetRuntimeConfig changedRuntime = base.runtime().withCriticalHitMinimumNatural(19);
        RulesetCompositionResult result = RulesetComposer.compose(
                base, List.of(expandedCritical), List.of(), changedRuntime,
                "composed", "revision:composed", "1", "Composed", "",
                RulesetOrigin.HOMEBREW, "now");
        assertEquals(19, result.revision().runtime().criticalHitMinimumNatural());
        assertEquals("19", result.revision().entity(CoreRuleIds.CRITICAL_HIT)
                .attributes().get("criticalHitMinimumNatural"));
    }

    @Test
    void runtimeMirrorValuesInModuleAdditionsCannotBeSilentlyOverwritten() {
        RulesetRevision base = base();
        RuleEntity critical = entity(
                CoreRuleIds.CRITICAL_HIT, RulesetOrigin.HOMEBREW,
                Map.of("criticalHitMinimumNatural", "19"));
        RulesetModule addition = module(
                "module:add-critical", List.of(), Set.of(), List.of(), List.of(critical));

        RulesetCompositionException mismatch = assertCompositionFails(base, List.of(addition));
        assertEquals(Set.of(RulesetCompositionIssue.Code.RUNTIME_ATTRIBUTE_MISMATCH), codes(mismatch));

        RulesetCompositionResult aligned = RulesetComposer.compose(
                base, List.of(addition), List.of(), base.runtime().withCriticalHitMinimumNatural(19),
                "composed", "revision:composed", "1", "Composed", "",
                RulesetOrigin.HOMEBREW, "now");
        assertEquals("19", aligned.revision().entity(CoreRuleIds.CRITICAL_HIT)
                .attributes().get("criticalHitMinimumNatural"));
    }

    @Test
    void moduleHashesIgnoreMapOrderRejectTamperingAndLockModuleOrder() {
        LinkedHashMap<String, String> forward = new LinkedHashMap<>();
        forward.put("a", "1");
        forward.put("b", "2");
        LinkedHashMap<String, String> reversed = new LinkedHashMap<>();
        reversed.put("b", "2");
        reversed.put("a", "1");
        RulesetModule firstShape = module("module:stable", List.of(), Set.of(),
                List.of(new RulePatch("patch", TARGET, null, null, forward, Set.of(), null)), List.of());
        RulesetModule secondShape = module("module:stable", List.of(), Set.of(),
                List.of(new RulePatch("patch", TARGET, null, null, reversed, Set.of(), null)), List.of());
        assertEquals(firstShape.canonicalHash(), secondShape.canonicalHash());

        assertThrows(IllegalArgumentException.class, () -> new RulesetModule(
                firstShape.id(), firstShape.version(), firstShape.name(), firstShape.description(),
                firstShape.origin(), firstShape.requiredSemanticsVersion(), firstShape.dependencies(),
                firstShape.incompatibleModuleIds(), firstShape.patches(), firstShape.additions(), "tampered"));

        RulesetRevision base = base(entity(TARGET, RulesetOrigin.BUNDLED_STANDARD, Map.of()));
        RulesetModule other = module("module:other", List.of(), Set.of(), List.of(), List.of());
        RulesetCompositionLock forwardLock = RulesetCompositionLock.create(
                base.canonicalHash(), List.of(firstShape.reference(), other.reference()), List.of());
        RulesetCompositionLock reverseLock = RulesetCompositionLock.create(
                base.canonicalHash(), List.of(other.reference(), firstShape.reference()), List.of());
        assertNotEquals(forwardLock.canonicalHash(), reverseLock.canonicalHash());
    }

    @Test
    void ruleFieldPathsEscapeIdsAndAttributeKeys() {
        assertEquals("core~1test/attributes/a~0b~1c", RuleFieldRef.attribute("core/test", "a~b/c").path());
    }

    private RulesetCompositionException assertCompositionFails(
            RulesetRevision base,
            List<RulesetModule> modules) {
        return assertThrows(RulesetCompositionException.class, () -> compose(base, modules, List.of()));
    }

    private RulesetCompositionResult compose(
            RulesetRevision base,
            List<RulesetModule> modules,
            List<RulesetConflictResolution> resolutions) {
        return RulesetComposer.compose(base, modules, resolutions,
                "composed", "revision:composed", "1", "Composed", "",
                RulesetOrigin.HOMEBREW, "now");
    }

    private RulesetRevision base(RuleEntity... entities) {
        return RulesetRevision.create(
                "base", "revision:base", "1", "Base", "",
                RulesetOrigin.BUNDLED_STANDARD, "", RulesetRuntimeConfig.standardSrd521(),
                List.of(entities), "now");
    }

    private RulesetModule module(
            String id,
            List<RulesetModuleRef> dependencies,
            Set<String> incompatible,
            List<RulePatch> patches,
            List<RuleEntity> additions) {
        return RulesetModule.create(
                id, "1.0.0", LocalizedRuleText.single("en", id),
                LocalizedRuleText.single("en", "Description " + id), RulesetOrigin.HOMEBREW,
                RulesetRuntimeConfig.CURRENT_SEMANTICS, dependencies, incompatible, patches, additions);
    }

    private RulePatch attributePatch(String id, String target, String key, String value) {
        return new RulePatch(id, target, null, null, Map.of(key, value), Set.of(), null);
    }

    private RuleEntity entity(String id, RulesetOrigin origin, Map<String, String> attributes) {
        return new RuleEntity(
                id, RuleKind.CORE_MECHANIC, origin,
                LocalizedRuleText.single("en", "Name " + id),
                LocalizedRuleText.single("en", "Description " + id),
                "", true, RuleAutomationLevel.FULL, attributes, List.of("test"), "test", "CC0", 0);
    }

    private Set<RulesetCompositionIssue.Code> codes(RulesetCompositionException failure) {
        Set<RulesetCompositionIssue.Code> result = failure.issues().stream()
                .map(RulesetCompositionIssue::code)
                .collect(Collectors.toSet());
        assertTrue(!result.isEmpty());
        return result;
    }
}
