package app.d6d.rules.persistence;

import app.d6d.persistence.json.Json;
import app.d6d.rules.authoring.RuleAuthoringMetadata;
import app.d6d.rules.authoring.RulesetAuthoringState;
import app.d6d.rules.model.LocalizedRuleText;
import app.d6d.rules.model.RuleAutomationLevel;
import app.d6d.rules.model.RuleEntity;
import app.d6d.rules.model.RuleKind;
import app.d6d.rules.model.RulePatch;
import app.d6d.rules.model.RulesetDraft;
import app.d6d.rules.model.RulesetCompositionResult;
import app.d6d.rules.model.RulesetModule;
import app.d6d.rules.model.RulesetOrigin;
import app.d6d.rules.model.RulesetRevision;
import app.d6d.rules.model.RulesetRuntimeConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalRulesetRepositoryTest {
    @TempDir Path directory;

    private RulesetRevision standard() {
        RuleEntity entity = new RuleEntity("core:test", RuleKind.CORE_MECHANIC,
                RulesetOrigin.BUNDLED_STANDARD, LocalizedRuleText.bilingual("Regola", "Rule"),
                LocalizedRuleText.bilingual("Test", "Test"), "", true, RuleAutomationLevel.FULL,
                Map.of(), List.of("core"), "SRD", "CC", 0);
        return RulesetRevision.create("standard", "standard-1", "1", "Standard", "",
                RulesetOrigin.BUNDLED_STANDARD, "", RulesetRuntimeConfig.standardSrd521(),
                List.of(entity), "now");
    }

    @Test
    void forkDraftAndPublishedRevisionSurviveReload() throws IOException {
        RulesetRevision standard = standard();
        LocalRulesetRepository repository = new LocalRulesetRepository(directory, List.of(standard));
        RulesetDraft draft = repository.createHomebrew(standard.canonicalHash(), "La mia versione", "");
        RulesetDraft changed = draft.withContent(draft.name(), draft.description(),
                draft.runtime().withCriticalHitMinimumNatural(19), draft.patches(), draft.additions(), "later");
        repository.saveDraft(changed);
        RulesetRevision published = repository.publish(changed.id(), "1.0.0");

        LocalRulesetRepository reloaded = new LocalRulesetRepository(directory, List.of(standard));

        assertEquals(2, reloaded.revisions().size());
        assertTrue(reloaded.drafts().isEmpty());
        assertNotNull(reloaded.findRevision(published.canonicalHash()));
        assertEquals(19, reloaded.findRevision(published.canonicalHash()).runtime().criticalHitMinimumNatural());
        assertTrue(standard.readOnly());
        assertFalse(published.readOnly());
    }

    @Test
    void staleDraftCannotOverwriteANewerSave() throws IOException {
        RulesetRevision standard = standard();
        LocalRulesetRepository repository = new LocalRulesetRepository(directory, List.of(standard));
        RulesetDraft draft = repository.createHomebrew(standard.canonicalHash(), "Homebrew", "");
        RulesetDraft first = draft.withContent(draft.name(), "first", draft.runtime(),
                draft.patches(), draft.additions(), "one");
        repository.saveDraft(first);
        RulesetDraft stale = draft.withContent(draft.name(), "stale", draft.runtime(),
                draft.patches(), draft.additions(), "two");

        assertThrows(IllegalStateException.class, () -> repository.saveDraft(stale));
    }

    @Test
    void draftAndVisualAuthoringMetadataAreSavedAndRemovedAtomically() throws IOException {
        RulesetRevision standard = standard();
        LocalRulesetRepository repository = new LocalRulesetRepository(directory, List.of(standard));
        RulesetDraft draft = repository.createHomebrew(standard.canonicalHash(), "Visual", "");
        RuleAuthoringMetadata metadata = new RuleAuthoringMetadata(
                "builtin.stat", 1, List.of("core:test"), Map.of("calculation", "blocks"),
                Set.of(), Map.of("core:test", "hash"), List.of());
        RulesetAuthoringState authoring = repository.authoringState()
                .withGroup(draft.id(), "group:core-test", metadata);
        RulesetDraft changed = draft.withContent(
                draft.name(), "saved with blocks", draft.runtime(),
                draft.patches(), draft.additions(), "later");

        repository.saveDraft(changed, authoring);
        LocalRulesetRepository reloaded = new LocalRulesetRepository(directory, List.of(standard));

        assertEquals("saved with blocks", reloaded.findDraft(draft.id()).description());
        assertEquals(metadata, reloaded.authoringState().groups(draft.id()).get("group:core-test"));

        reloaded.publish(draft.id(), "1");
        assertTrue(reloaded.authoringState().byDraftId().isEmpty());
    }

    @Test
    void portableExportIsValidatedOnImport() throws IOException {
        RulesetRevision standard = standard();
        LocalRulesetRepository source = new LocalRulesetRepository(directory.resolve("source"), List.of(standard));
        RulesetDraft draft = source.createHomebrew(standard.canonicalHash(), "Portable", "");
        RulesetDraft changed = draft.withContent(draft.name(), draft.description(),
                draft.runtime().withMaximumExhaustion(8), draft.patches(), draft.additions(), "changed");
        source.saveDraft(changed);
        RulesetRevision published = source.publish(changed.id(), "2");
        Path exported = directory.resolve("portable.json");
        source.exportRevision(published.canonicalHash(), exported);

        LocalRulesetRepository target = new LocalRulesetRepository(directory.resolve("target"), List.of(standard));
        assertEquals(published.canonicalHash(), target.importRevision(exported).canonicalHash());
        assertEquals(2, target.revisions().size());
    }

    @Test
    void repositoryCannotPublishAnExecutableRevisionWithBrokenRuntimeLinks() throws IOException {
        RulesetRevision standard = standard();
        LocalRulesetRepository repository = new LocalRulesetRepository(directory, List.of(standard));
        RulesetDraft draft = repository.createHomebrew(standard.canonicalHash(), "Broken", "");
        RuleEntity brokenAction = new RuleEntity(
                "homebrew:action:broken", RuleKind.ACTION, RulesetOrigin.HOMEBREW,
                LocalizedRuleText.bilingual("Rotta", "Broken"),
                LocalizedRuleText.bilingual("Test", "Test"), "", true, RuleAutomationLevel.FULL,
                Map.of("costs", "turn:missing=1"), List.of("homebrew"), "Homebrew", "", 0);
        RulesetDraft changed = draft.withContent(
                draft.name(), draft.description(), draft.runtime(), draft.patches(),
                List.of(brokenAction), "changed");
        repository.saveDraft(changed);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> repository.publish(changed.id(), "1"));

        assertTrue(failure.getMessage().contains("missing turn resource"));
        assertNotNull(repository.findDraft(changed.id()));
        assertEquals(1, repository.revisions().size());
    }

    @Test
    void aPublishedHomebrewContinuesInANewDraftWithoutMutatingHistory() throws IOException {
        RulesetRevision standard = standard();
        LocalRulesetRepository repository = new LocalRulesetRepository(directory, List.of(standard));
        RulesetDraft firstDraft = repository.createHomebrew(standard.canonicalHash(), "Campaign rules", "");
        RulesetDraft changed = firstDraft.withContent(firstDraft.name(), firstDraft.description(),
                firstDraft.runtime().withMaximumExhaustion(8), firstDraft.patches(), firstDraft.additions(), "one");
        repository.saveDraft(changed);
        RulesetRevision firstRevision = repository.publish(changed.id(), "1.0.0");

        RulesetDraft next = repository.createNextDraft(firstRevision.canonicalHash());

        assertEquals(firstRevision.projectId(), next.projectId());
        assertEquals(firstRevision.canonicalHash(), next.baseCanonicalHash());
        assertEquals(firstRevision.runtime(), next.runtime());
        assertNotNull(repository.findRevision(firstRevision.canonicalHash()));
        assertThrows(IllegalStateException.class,
                () -> repository.createNextDraft(firstRevision.canonicalHash()));
    }

    @Test
    void aResolvedPortableRevisionDoesNotRequireItsAncestryAndProjectCollisionsAreRemapped()
            throws IOException {
        RulesetRevision standard = standard();
        LocalRulesetRepository repository = new LocalRulesetRepository(directory, List.of(standard));
        RulesetDraft localDraft = repository.createHomebrew(standard.canonicalHash(), "Local", "");
        RulesetDraft localChange = localDraft.withContent(localDraft.name(), localDraft.description(),
                localDraft.runtime().withMaximumExhaustion(7), localDraft.patches(), localDraft.additions(), "one");
        repository.saveDraft(localChange);
        RulesetRevision local = repository.publish(localChange.id(), "1");

        RulesetRevision portable = RulesetRevision.create(
                local.projectId(),
                "foreign-revision",
                "9",
                "Imported collision",
                "",
                RulesetOrigin.HOMEBREW,
                "missing-ancestry-hash",
                standard.runtime().withCriticalHitMinimumNatural(18),
                standard.entities(),
                "later");
        Path file = directory.resolve("collision.onfall-ruleset");
        Files.writeString(
                file,
                Json.encode(RulesetLibraryJsonCodec.encodePortableRevision(portable)),
                StandardCharsets.UTF_8);

        RulesetRevision imported = repository.importRevision(file);

        assertEquals(portable.canonicalHash(), imported.canonicalHash());
        assertNotEquals(local.projectId(), imported.projectId());
        assertEquals("missing-ancestry-hash", imported.baseCanonicalHash());
        assertNotNull(repository.findRevision(imported.canonicalHash()));
    }

    @Test
    void aCorruptCurrentLibraryRecoversTheNewestSemanticallyValidBackup() throws IOException {
        RulesetRevision standard = standard();
        LocalRulesetRepository repository = new LocalRulesetRepository(directory, List.of(standard));
        RulesetDraft draft = repository.createHomebrew(standard.canonicalHash(), "Recoverable", "");
        RulesetDraft changed = draft.withContent(draft.name(), draft.description(),
                draft.runtime().withMaximumExhaustion(7), draft.patches(), draft.additions(), "changed");
        repository.saveDraft(changed);
        RulesetRevision published = repository.publish(changed.id(), "1");
        // Una scrittura ulteriore crea un backup il cui stato contiene già la revisione pubblicata.
        repository.createHomebrew(standard.canonicalHash(), "Later draft", "");
        Files.writeString(directory.resolve("library.json"), "{ broken json", StandardCharsets.UTF_8);

        LocalRulesetRepository recovered = new LocalRulesetRepository(directory, List.of(standard));

        assertNotNull(recovered.findRevision(published.canonicalHash()));
        assertEquals(2, recovered.revisions().size());
        assertTrue(Json.parseObject(Files.readString(directory.resolve("library.json")))
                .containsKey("schemaVersion"));
    }

    @Test
    void installedModulesAndPublishedCompositionsSurviveReloadAndLegacyWrites() throws IOException {
        RulesetRevision standard = standard();
        LocalRulesetRepository repository = new LocalRulesetRepository(directory, List.of(standard));
        RulesetModule module = module("module:variant", "variant", "enabled");
        repository.installModule(module);
        // Un percorso schema-1 preesistente non deve cancellare i nuovi campi della libreria.
        repository.createHomebrew(standard.canonicalHash(), "Ordinary draft", "");

        RulesetCompositionResult published = repository.publishComposition(
                standard.canonicalHash(), List.of(module.canonicalHash()), List.of(), standard.runtime(),
                "Modular rules", "", "1.0.0");
        LocalRulesetRepository reloaded = new LocalRulesetRepository(directory, List.of(standard));

        assertEquals(List.of(module), reloaded.modules());
        assertNotNull(reloaded.findRevision(published.revision().canonicalHash()));
        assertEquals(published.lock(), reloaded.findCompositionLock(published.revision().canonicalHash()));
        assertEquals("enabled", reloaded.findRevision(published.revision().canonicalHash())
                .entity("core:test").attributes().get("variant"));
    }

    @Test
    void portableModuleImportIsVerifiedAndIdempotent() throws IOException {
        RulesetRevision standard = standard();
        RulesetModule module = module("module:portable", "portable", "yes");
        LocalRulesetRepository source = new LocalRulesetRepository(directory.resolve("source-module"), List.of(standard));
        source.installModule(module);
        Path file = directory.resolve("module.onfall-rules-module");
        source.exportModule(module.canonicalHash(), file);

        LocalRulesetRepository target = new LocalRulesetRepository(directory.resolve("target-module"), List.of(standard));
        assertEquals(module, target.importModule(file));
        assertEquals(module, target.importModule(file));
        assertEquals(1, target.modules().size());
    }

    @Test
    void portableBundleInstallsSnapshotLockAndExactModulesAtomically() throws IOException {
        RulesetRevision standard = standard();
        RulesetModule module = module("module:bundle", "bundle", "yes");
        LocalRulesetRepository source = new LocalRulesetRepository(directory.resolve("source-bundle"), List.of(standard));
        source.installModule(module);
        RulesetCompositionResult published = source.publishComposition(
                standard.canonicalHash(), List.of(module.canonicalHash()), List.of(), standard.runtime(),
                "Bundle rules", "", "1.0.0");
        Path file = directory.resolve("rules.onfall-rules-bundle");
        source.exportBundle(published.revision().canonicalHash(), file);

        LocalRulesetRepository target = new LocalRulesetRepository(directory.resolve("target-bundle"), List.of(standard));
        RulesetCompositionResult imported = target.importBundle(file);
        RulesetCompositionResult repeated = target.importBundle(file);

        assertEquals(published.revision().canonicalHash(), imported.revision().canonicalHash());
        assertEquals(published.lock(), imported.lock());
        assertEquals(imported, repeated);
        assertEquals(1, target.modules().size());
        assertEquals(2, target.revisions().size());
        assertEquals(imported.lock(), target.findCompositionLock(imported.revision().canonicalHash()));
    }

    @Test
    void portableBundleCannotClaimAFlattenedRevisionItsModulesDoNotProduce() throws IOException {
        RulesetRevision standard = standard();
        RulesetModule module = module("module:claimed", "claimed", "yes");
        LocalRulesetRepository source = new LocalRulesetRepository(directory.resolve("source-forged"), List.of(standard));
        source.installModule(module);
        RulesetCompositionResult legitimate = source.publishComposition(
                standard.canonicalHash(), List.of(module.canonicalHash()), List.of(), standard.runtime(),
                "Claimed rules", "", "1.0.0");
        RulesetRevision forged = RulesetRevision.create(
                legitimate.revision().projectId(), legitimate.revision().revisionId(),
                legitimate.revision().version(), legitimate.revision().name(),
                legitimate.revision().description(), legitimate.revision().origin(),
                legitimate.revision().baseCanonicalHash(), legitimate.revision().runtime(),
                List.of(standard.entity("core:test").withAttributes(Map.of("claimed", "forged"))),
                legitimate.revision().publishedAt());
        RulesetPortableBundle forgedBundle = new RulesetPortableBundle(
                forged, legitimate.lock(), List.of(module));
        Path file = directory.resolve("forged.onfall-rules-bundle");
        Files.writeString(file, Json.encode(RulesetLibraryJsonCodec.encodePortableBundle(forgedBundle)),
                StandardCharsets.UTF_8);

        LocalRulesetRepository target = new LocalRulesetRepository(directory.resolve("target-forged"), List.of(standard));
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> target.importBundle(file));

        assertTrue(failure.getMessage().contains("does not match"));
        assertTrue(target.modules().isEmpty());
        assertEquals(1, target.revisions().size());
    }

    private RulesetModule module(String id, String key, String value) {
        return RulesetModule.create(
                id, "1.0.0", LocalizedRuleText.single("en", id),
                LocalizedRuleText.single("en", "Test module"), RulesetOrigin.HOMEBREW,
                RulesetRuntimeConfig.CURRENT_SEMANTICS, List.of(), Set.of(),
                List.of(new RulePatch(
                        "patch:" + id, "core:test", null, null,
                        Map.of(key, value), Set.of(), null)), List.of());
    }
}
