package app.d6d.rules.persistence;

import app.d6d.rules.model.RulesetDraft;
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
        List<RulesetDraft> drafts) {

    public RulesetLibrary {
        projects = sortedProjects(projects);
        revisions = sortedRevisions(revisions);
        drafts = sortedDrafts(drafts);

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
    }

    public static RulesetLibrary empty() {
        return new RulesetLibrary(List.of(), List.of(), List.of());
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
}
