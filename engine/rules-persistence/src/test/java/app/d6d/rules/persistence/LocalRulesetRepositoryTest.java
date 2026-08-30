package app.d6d.rules.persistence;

import app.d6d.persistence.json.Json;
import app.d6d.rules.model.LocalizedRuleText;
import app.d6d.rules.model.RuleAutomationLevel;
import app.d6d.rules.model.RuleEntity;
import app.d6d.rules.model.RuleKind;
import app.d6d.rules.model.RulesetDraft;
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
}
