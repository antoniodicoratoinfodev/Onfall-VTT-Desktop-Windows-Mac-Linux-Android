package app.d6d.rules.persistence;

import app.d6d.rules.authoring.RulesetAuthoringState;
import app.d6d.rules.model.RulesetDraft;
import app.d6d.rules.model.RulesetModule;
import app.d6d.rules.model.RulesetProject;
import app.d6d.rules.model.RulesetRevision;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Contenuto modificabile dell'archivio locale; i regolamenti inclusi non vengono mai scritti qui. */
public record RulesetLibrary(
        List<RulesetProject> projects,
        List<RulesetRevision> revisions,
        List<RulesetDraft> drafts,
        List<RulesetModule> modules,
        List<StoredRulesetComposition> compositions,
        RulesetAuthoringState authoring) {

    public RulesetLibrary {
        projects = sortedProjects(projects);
        revisions = sortedRevisions(revisions);
        drafts = sortedDrafts(drafts);
        modules = sortedModules(modules);
        compositions = sortedCompositions(compositions);
        authoring = Objects.requireNonNull(authoring, "authoring");

        HashSet<String> projectIds = new HashSet<>();
        for (RulesetProject project : projects) {
            if (!projectIds.add(project.id())) {
                throw new IllegalArgumentException("Duplicate ruleset project: " + project.id());
            }
        }
        HashSet<String> hashes = new HashSet<>();
        for (RulesetRevision revision : revisions) {
            if (!hashes.add(revision.canonicalHash())) {
                throw new IllegalArgumentException("Duplicate ruleset revision: " + revision.canonicalHash());
            }
        }
        HashSet<String> draftIds = new HashSet<>();
        for (RulesetDraft draft : drafts) {
            if (!draftIds.add(draft.id())) {
                throw new IllegalArgumentException("Duplicate ruleset draft: " + draft.id());
            }
        }
        HashSet<String> moduleHashes = new HashSet<>();
        for (RulesetModule module : modules) {
            if (!moduleHashes.add(module.canonicalHash())) {
                throw new IllegalArgumentException("Duplicate ruleset module: " + module.canonicalHash());
            }
        }
        HashSet<String> composedRevisionHashes = new HashSet<>();
        for (StoredRulesetComposition composition : compositions) {
            if (!composedRevisionHashes.add(composition.revisionCanonicalHash())) {
                throw new IllegalArgumentException(
                        "Duplicate composition for revision: " + composition.revisionCanonicalHash());
            }
        }
    }

    /** Costruttore compatibile con lo schema 1 e con i call site precedenti. */
    public RulesetLibrary(
            List<RulesetProject> projects,
            List<RulesetRevision> revisions,
            List<RulesetDraft> drafts) {
        this(projects, revisions, drafts, List.of(), List.of(), RulesetAuthoringState.empty());
    }

    /** Costruttore compatibile con lo schema 2 e con i call site precedenti. */
    public RulesetLibrary(
            List<RulesetProject> projects,
            List<RulesetRevision> revisions,
            List<RulesetDraft> drafts,
            List<RulesetModule> modules,
            List<StoredRulesetComposition> compositions) {
        this(projects, revisions, drafts, modules, compositions, RulesetAuthoringState.empty());
    }

    public static RulesetLibrary empty() {
        return new RulesetLibrary(
                List.of(), List.of(), List.of(), List.of(), List.of(), RulesetAuthoringState.empty());
    }

    /** Aggiorna il contenuto storico senza perdere catalogo moduli e lock. */
    public RulesetLibrary withCoreContent(
            List<RulesetProject> changedProjects,
            List<RulesetRevision> changedRevisions,
            List<RulesetDraft> changedDrafts) {
        return new RulesetLibrary(
                changedProjects, changedRevisions, changedDrafts, modules, compositions, authoring);
    }

    public RulesetLibrary withAuthoring(RulesetAuthoringState changedAuthoring) {
        return new RulesetLibrary(projects, revisions, drafts, modules, compositions, changedAuthoring);
    }

    private static List<RulesetProject> sortedProjects(List<RulesetProject> values) {
        ArrayList<RulesetProject> result = new ArrayList<>(Objects.requireNonNull(values, "projects"));
        result.forEach(value -> Objects.requireNonNull(value, "projects contains null"));
        result.sort(java.util.Comparator.comparing(RulesetProject::id));
        return List.copyOf(result);
    }

    private static List<RulesetRevision> sortedRevisions(List<RulesetRevision> values) {
        ArrayList<RulesetRevision> result = new ArrayList<>(Objects.requireNonNull(values, "revisions"));
        result.forEach(value -> Objects.requireNonNull(value, "revisions contains null"));
        result.sort(java.util.Comparator.comparing(RulesetRevision::canonicalHash));
        return List.copyOf(result);
    }

    private static List<RulesetDraft> sortedDrafts(List<RulesetDraft> values) {
        ArrayList<RulesetDraft> result = new ArrayList<>(Objects.requireNonNull(values, "drafts"));
        result.forEach(value -> Objects.requireNonNull(value, "drafts contains null"));
        result.sort(java.util.Comparator.comparing(RulesetDraft::id));
        return List.copyOf(result);
    }

    private static List<RulesetModule> sortedModules(List<RulesetModule> values) {
        ArrayList<RulesetModule> result = new ArrayList<>(Objects.requireNonNull(values, "modules"));
        result.forEach(value -> Objects.requireNonNull(value, "modules contains null"));
        result.sort(java.util.Comparator.comparing(RulesetModule::id)
                .thenComparing(RulesetModule::version)
                .thenComparing(RulesetModule::canonicalHash));
        return List.copyOf(result);
    }

    private static List<StoredRulesetComposition> sortedCompositions(
            List<StoredRulesetComposition> values) {
        ArrayList<StoredRulesetComposition> result = new ArrayList<>(
                Objects.requireNonNull(values, "compositions"));
        result.forEach(value -> Objects.requireNonNull(value, "compositions contains null"));
        result.sort(java.util.Comparator.comparing(StoredRulesetComposition::revisionCanonicalHash));
        return List.copyOf(result);
    }
}
