package app.d6d.rules.model;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RulesetModelTest {
    private RuleEntity entity(String id, Map<String, String> attributes) {
        return new RuleEntity(id, RuleKind.CORE_MECHANIC, RulesetOrigin.BUNDLED_STANDARD,
                LocalizedRuleText.bilingual("Nome " + id, "Name " + id),
                LocalizedRuleText.bilingual("Descrizione", "Description"),
                "", true, RuleAutomationLevel.FULL, attributes, List.of("core"), "test", "CC", 0);
    }

    private RulesetRevision base() {
        return RulesetRevision.create("standard", "revision-1", "1", "Standard", "",
                RulesetOrigin.BUNDLED_STANDARD, "", RulesetRuntimeConfig.standardSrd521(),
                List.of(entity(CoreRuleIds.CRITICAL_HIT, Map.of("b", "2", "a", "1"))), "now");
    }

    @Test
    void canonicalHashDoesNotDependOnCollectionOrder() {
        LinkedHashMap<String, String> reversed = new LinkedHashMap<>();
        reversed.put("a", "1");
        reversed.put("b", "2");
        RulesetRevision first = base();
        RulesetRevision second = RulesetRevision.create("standard", "another-id", "other", "Standard", "",
                RulesetOrigin.BUNDLED_STANDARD, "", RulesetRuntimeConfig.standardSrd521(),
                List.of(entity(CoreRuleIds.CRITICAL_HIT, reversed)), "later");

        assertEquals(first.canonicalHash(), second.canonicalHash());
        assertEquals(first.runtimeHash(), second.runtimeHash());
    }

    @Test
    void documentMetadataChangesCanonicalHashButNotRuntimeHash() {
        RulesetRevision first = base();
        RulesetRevision renamed = RulesetRevision.create(
                first.projectId(), "another-id", "2", "Renamed standard", "New description",
                first.origin(), first.baseCanonicalHash(), first.runtime(), first.entities(), "later");

        assertNotEquals(first.canonicalHash(), renamed.canonicalHash());
        assertEquals(first.runtimeHash(), renamed.runtimeHash());
    }

    @Test
    void legacyAutomationAcceptsSupportedRuntimeChangesButRejectsStructuralOnes() {
        RulesetRevision standard = base();
        RulesetRevision runtimeOnly = RulesetRevision.create(
                "homebrew", "runtime", "1", "Runtime", "", RulesetOrigin.HOMEBREW,
                standard.canonicalHash(), standard.runtime().withCriticalHitMinimumNatural(18),
                standard.entities(), "now");
        RuleEntity baseline = standard.entities().get(0);
        RuleEntity structuralEntity = new RuleEntity(
                baseline.id(), RuleKind.CLASS, RulesetOrigin.HOMEBREW,
                baseline.name(), baseline.description(), baseline.id(), true, RuleAutomationLevel.MANUAL,
                baseline.attributes(), baseline.tags(), "", "", 0);
        RulesetRevision structural = RulesetRevision.create(
                "homebrew", "structural", "1", "Structural", "", RulesetOrigin.HOMEBREW,
                standard.canonicalHash(), standard.runtime(), List.of(structuralEntity), "now");

        assertEquals(true, runtimeOnly.legacyCombatAutomationCompatibleWith(standard));
        assertEquals(false, structural.legacyCombatAutomationCompatibleWith(standard));
    }

    @Test
    void aForkResolvesWithoutMutatingItsStandardBase() {
        RulesetRevision base = base();
        RulesetRuntimeConfig changedRuntime = base.runtime().withCriticalHitMinimumNatural(19);
        RulePatch patch = new RulePatch("patch-1", CoreRuleIds.CRITICAL_HIT,
                LocalizedRuleText.bilingual("Critico esteso", "Expanded critical"), null,
                Map.of("criticalHitMinimumNatural", "19"), Set.of(), null);
        RulesetDraft draft = new RulesetDraft("draft-1", "homebrew", base.canonicalHash(), "Homebrew", "",
                RulesetOrigin.HOMEBREW, changedRuntime, List.of(patch), List.of(), 0, "now");

        RulesetRevision resolved = RulesetResolver.preview(base, draft);

        assertEquals(20, base.runtime().criticalHitMinimumNatural());
        assertEquals(19, resolved.runtime().criticalHitMinimumNatural());
        assertEquals("Nome " + CoreRuleIds.CRITICAL_HIT,
                base.entity(CoreRuleIds.CRITICAL_HIT).name().text("it"));
        assertEquals("Critico esteso", resolved.entity(CoreRuleIds.CRITICAL_HIT).name().text("it"));
        assertNotEquals(base.canonicalHash(), resolved.canonicalHash());
    }

    @Test
    void aStoredHashThatDoesNotMatchThePayloadIsRejected() {
        RulesetRevision valid = base();
        assertThrows(IllegalArgumentException.class, () -> new RulesetRevision(
                valid.projectId(), valid.revisionId(), valid.version(), valid.name(), valid.description(),
                valid.origin(), valid.baseCanonicalHash(), valid.runtime(), valid.entities(), valid.publishedAt(),
                "tampered", valid.runtimeHash()));
    }

    @Test
    void aPatchCanChangeEveryGenericRuleFacetWithoutTouchingTheBase() {
        String genericId = "core:generic";
        RulesetRevision base = RulesetRevision.create(
                "standard", "revision-generic", "1", "Standard", "",
                RulesetOrigin.BUNDLED_STANDARD, "", RulesetRuntimeConfig.standardSrd521(),
                List.of(entity(genericId, Map.of("a", "1", "b", "2"))), "now");
        RulePatch patch = new RulePatch(
                "patch-generic",
                genericId,
                LocalizedRuleText.bilingual("Classe libera", "Free-form class"),
                LocalizedRuleText.bilingual("Descrizione nuova", "New description"),
                Map.of("hitDie", "d12"),
                Set.of("a", "b"),
                false,
                RuleKind.CLASS,
                RuleAutomationLevel.MANUAL,
                List.of("homebrew", "class"));
        RulesetDraft draft = new RulesetDraft(
                "draft-generic", "homebrew", base.canonicalHash(), "Homebrew", "",
                RulesetOrigin.HOMEBREW, base.runtime(), List.of(patch), List.of(), 0, "now");

        RuleEntity changed = RulesetResolver.preview(base, draft).entity(genericId);

        assertEquals(RuleKind.CLASS, changed.kind());
        assertEquals(RuleAutomationLevel.MANUAL, changed.automationLevel());
        assertEquals(false, changed.enabled());
        assertEquals(Map.of("hitDie", "d12"), changed.attributes());
        assertEquals(List.of("class", "homebrew"), changed.tags());
        assertEquals(RuleKind.CORE_MECHANIC, base.entity(genericId).kind());
    }

    @Test
    void genericFoundationIsReadOnlyEmptyAndStillProducesAConfiguredSnapshot() {
        RulesetRevision foundation = GenericRulesetFoundation.revision();

        assertTrue(foundation.readOnly());
        assertTrue(foundation.entities().isEmpty());
        assertTrue(foundation.compile().entities().isEmpty());
        RuleSessionSnapshot snapshot = RuleSessionSnapshot.fromRevision(foundation);
        assertTrue(snapshot.configured());
        assertTrue(snapshot.executable());
        assertTrue(snapshot.entities().isEmpty());
        assertEquals(RulesetRuntimeConfig.genericManual(), foundation.runtime());
    }
}
