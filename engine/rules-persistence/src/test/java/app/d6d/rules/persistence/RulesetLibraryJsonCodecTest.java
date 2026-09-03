package app.d6d.rules.persistence;

import app.d6d.rules.authoring.RuleAuthoringExample;
import app.d6d.rules.authoring.RuleAuthoringMetadata;
import app.d6d.rules.authoring.RulesetAuthoringState;
import app.d6d.rules.model.LocalizedRuleText;
import app.d6d.rules.model.RuleAutomationLevel;
import app.d6d.rules.model.RuleEntity;
import app.d6d.rules.model.RuleKind;
import app.d6d.rules.model.RulePatch;
import app.d6d.rules.model.RulesetComposer;
import app.d6d.rules.model.RulesetCompositionLock;
import app.d6d.rules.model.RulesetCompositionResult;
import app.d6d.rules.model.RulesetDraft;
import app.d6d.rules.model.RulesetModule;
import app.d6d.rules.model.RulesetModuleRef;
import app.d6d.rules.model.RulesetOrigin;
import app.d6d.rules.model.RulesetProject;
import app.d6d.rules.model.RulesetRevision;
import app.d6d.rules.model.RulesetRuntimeConfig;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RulesetLibraryJsonCodecTest {
    private static final String TARGET = "core:test";

    @Test
    void schemaThreeRoundTripsModulesCompositionLocksAndAuthoringMetadata() {
        Fixture fixture = fixture();
        RulesetProject project = new RulesetProject(
                fixture.result().revision().projectId(), "Composed", "",
                fixture.base().canonicalHash(), List.of(fixture.result().revision().canonicalHash()),
                fixture.result().revision().canonicalHash(), false);
        RulesetDraft draft = new RulesetDraft(
                "draft:test", project.id(), fixture.result().revision().canonicalHash(),
                "Composed", "", RulesetOrigin.HOMEBREW,
                RulesetRuntimeConfig.standardSrd521(), List.of(), List.of(), 0, "now");
        RuleAuthoringMetadata metadata = new RuleAuthoringMetadata(
                "builtin.stat", 1, List.of(TARGET),
                Map.of("calculation", "visual-tree-v1"), Set.of("derivedFormula"),
                Map.of(TARGET, "content-hash"),
                List.of(new RuleAuthoringExample("ordinary", Map.of("level", "3"), "13")));
        RulesetAuthoringState authoring = RulesetAuthoringState.empty()
                .withGroup(draft.id(), "group:focus", metadata);
        RulesetLibrary library = new RulesetLibrary(
                List.of(project), List.of(fixture.result().revision()), List.of(draft),
                List.of(fixture.module()), List.of(new StoredRulesetComposition(
                        fixture.result().revision().canonicalHash(), fixture.result().lock())),
                authoring);

        Map<String, Object> encoded = RulesetLibraryJsonCodec.encode(library);
        RulesetLibrary decoded = RulesetLibraryJsonCodec.decode(encoded);

        assertEquals(RulesetLibraryJsonCodec.SCHEMA_VERSION, encoded.get("schemaVersion"));
        assertEquals(library, decoded);
    }

    @Test
    void schemaTwoLibrariesRemainReadableWithEmptyAuthoringMetadata() {
        LinkedHashMap<String, Object> schemaTwo = new LinkedHashMap<>(
                RulesetLibraryJsonCodec.encode(RulesetLibrary.empty()));
        schemaTwo.put("schemaVersion", 2);
        schemaTwo.remove("authoring");

        RulesetLibrary decoded = RulesetLibraryJsonCodec.decode(schemaTwo);

        assertEquals(RulesetAuthoringState.empty(), decoded.authoring());
    }

    @Test
    void schemaOneLibrariesRemainReadableWithEmptyModuleMetadata() {
        Fixture fixture = fixture();
        RulesetProject project = new RulesetProject(
                fixture.result().revision().projectId(), "Composed", "",
                fixture.base().canonicalHash(), List.of(fixture.result().revision().canonicalHash()),
                fixture.result().revision().canonicalHash(), false);
        RulesetLibrary legacy = new RulesetLibrary(
                List.of(project), List.of(fixture.result().revision()), List.of());
        LinkedHashMap<String, Object> schemaOne = new LinkedHashMap<>(RulesetLibraryJsonCodec.encode(legacy));
        schemaOne.put("schemaVersion", 1);
        schemaOne.remove("modules");
        schemaOne.remove("compositions");

        RulesetLibrary decoded = RulesetLibraryJsonCodec.decode(schemaOne);

        assertEquals(legacy, decoded);
        assertTrue(decoded.modules().isEmpty());
        assertTrue(decoded.compositions().isEmpty());
    }

    @Test
    void aFlattenedRevisionAndItsLockRemainReadableWithoutInstalledModules() {
        Fixture fixture = fixture();
        RulesetProject project = new RulesetProject(
                fixture.result().revision().projectId(), "Composed", "",
                fixture.base().canonicalHash(), List.of(fixture.result().revision().canonicalHash()),
                fixture.result().revision().canonicalHash(), false);
        RulesetLibrary withoutSources = new RulesetLibrary(
                List.of(project), List.of(fixture.result().revision()), List.of(), List.of(),
                List.of(new StoredRulesetComposition(
                        fixture.result().revision().canonicalHash(), fixture.result().lock())));

        RulesetLibrary decoded = RulesetLibraryJsonCodec.decode(
                RulesetLibraryJsonCodec.encode(withoutSources));

        assertEquals(fixture.result().revision(), decoded.revisions().get(0));
        assertTrue(decoded.modules().isEmpty());
        assertEquals(fixture.result().lock(), decoded.compositions().get(0).lock());
    }

    @Test
    void portableModulesAndBundlesRoundTripAndVerifyHashes() {
        Fixture fixture = fixture();

        RulesetModule module = RulesetLibraryJsonCodec.decodePortableModule(
                RulesetLibraryJsonCodec.encodePortableModule(fixture.module()));
        RulesetPortableBundle bundle = new RulesetPortableBundle(
                fixture.result().revision(), fixture.result().lock(), List.of(fixture.module()));
        RulesetPortableBundle decodedBundle = RulesetLibraryJsonCodec.decodePortableBundle(
                RulesetLibraryJsonCodec.encodePortableBundle(bundle));

        assertEquals(fixture.module(), module);
        assertEquals(bundle, decodedBundle);

        LinkedHashMap<String, Object> tampered = new LinkedHashMap<>(
                RulesetLibraryJsonCodec.encodePortableModule(fixture.module()));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) tampered.get("module");
        payload.put("canonicalHash", "tampered");
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> RulesetLibraryJsonCodec.decodePortableModule(tampered));
        assertTrue(failure.getMessage().contains("canonical hash"));
    }

    @Test
    void portableRevisionDecoderAcceptsSchemaOneButRejectsFutureSchemas() {
        Fixture fixture = fixture();
        LinkedHashMap<String, Object> schemaOne = new LinkedHashMap<>(
                RulesetLibraryJsonCodec.encodePortableRevision(fixture.result().revision()));
        schemaOne.put("schemaVersion", 1);
        assertEquals(fixture.result().revision(), RulesetLibraryJsonCodec.decodePortableRevision(schemaOne));

        LinkedHashMap<String, Object> future = new LinkedHashMap<>(schemaOne);
        future.put("schemaVersion", RulesetLibraryJsonCodec.SCHEMA_VERSION + 1);
        assertThrows(IllegalArgumentException.class,
                () -> RulesetLibraryJsonCodec.decodePortableRevision(future));
    }

    @Test
    void portableBundlesRequireAClosedExactlyOrderedModuleDependencyGraph() {
        Fixture fixture = fixture();
        RulesetModule dependency = emptyModule("module:dependency", List.of());
        RulesetModule consumer = emptyModule("module:consumer", List.of(dependency.reference()));

        RulesetCompositionLock missingDependency = RulesetCompositionLock.create(
                fixture.base().canonicalHash(), List.of(consumer.reference()), List.of());
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> new RulesetPortableBundle(
                        fixture.result().revision(), missingDependency, List.of(consumer)));
        assertTrue(missing.getMessage().contains("dependency"));

        RulesetModule wrongHashConsumer = emptyModule("module:wrong-hash-consumer", List.of(
                new RulesetModuleRef(dependency.id(), "wrong-hash")));
        RulesetCompositionLock wrongHash = RulesetCompositionLock.create(
                fixture.base().canonicalHash(),
                List.of(dependency.reference(), wrongHashConsumer.reference()), List.of());
        IllegalArgumentException hash = assertThrows(IllegalArgumentException.class,
                () -> new RulesetPortableBundle(
                        fixture.result().revision(), wrongHash,
                        List.of(dependency, wrongHashConsumer)));
        assertTrue(hash.getMessage().contains("exact dependency"));

        RulesetCompositionLock wrongOrder = RulesetCompositionLock.create(
                fixture.base().canonicalHash(),
                List.of(consumer.reference(), dependency.reference()), List.of());
        IllegalArgumentException order = assertThrows(IllegalArgumentException.class,
                () -> new RulesetPortableBundle(
                        fixture.result().revision(), wrongOrder,
                        List.of(dependency, consumer)));
        assertTrue(order.getMessage().contains("precede"));
    }

    private Fixture fixture() {
        RulesetRevision base = RulesetRevision.create(
                "base", "revision:base", "1", "Base", "", RulesetOrigin.BUNDLED_STANDARD,
                "", RulesetRuntimeConfig.standardSrd521(),
                List.of(entity(RulesetOrigin.BUNDLED_STANDARD)), "now");
        RulesetModule module = RulesetModule.create(
                "module:test", "1.0.0", LocalizedRuleText.single("en", "Test module"),
                LocalizedRuleText.single("en", "Changes one field"), RulesetOrigin.HOMEBREW,
                RulesetRuntimeConfig.CURRENT_SEMANTICS, List.of(), Set.of(),
                List.of(new RulePatch("patch:test", TARGET, null, null,
                        Map.of("value", "2"), Set.of(), null)), List.of());
        RulesetCompositionResult result = RulesetComposer.compose(
                base, List.of(module), List.of(), "composed", "revision:composed", "1",
                "Composed", "", RulesetOrigin.HOMEBREW, "now");
        return new Fixture(base, module, result);
    }

    private RulesetModule emptyModule(String id, List<RulesetModuleRef> dependencies) {
        return RulesetModule.create(
                id, "1.0.0", LocalizedRuleText.single("en", id),
                LocalizedRuleText.single("en", "Dependency test"), RulesetOrigin.HOMEBREW,
                RulesetRuntimeConfig.CURRENT_SEMANTICS, dependencies, Set.of(), List.of(), List.of());
    }

    private RuleEntity entity(RulesetOrigin origin) {
        return new RuleEntity(
                TARGET, RuleKind.CORE_MECHANIC, origin,
                LocalizedRuleText.single("en", "Test"),
                LocalizedRuleText.single("en", "Test rule"), "", true,
                RuleAutomationLevel.FULL, Map.of("value", "1"), List.of("test"), "test", "CC0", 0);
    }

    private record Fixture(
            RulesetRevision base,
            RulesetModule module,
            RulesetCompositionResult result) {
    }
}
