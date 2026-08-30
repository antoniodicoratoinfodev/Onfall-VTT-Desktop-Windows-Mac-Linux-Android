package app.d6d.rules.persistence;

import app.d6d.persistence.json.AtomicJsonStore;
import app.d6d.persistence.json.Json;
import app.d6d.rules.model.RulesetDraft;
import app.d6d.rules.model.RulesetOrigin;
import app.d6d.rules.model.RulesetProject;
import app.d6d.rules.model.RulesetResolver;
import app.d6d.rules.model.RulesetRevision;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Repository locale thread-safe con standard inclusi read-only e homebrew atomici. */
public final class LocalRulesetRepository {
    public static final String DEFAULT_BASE_NAME = "library";
    public static final int DEFAULT_MAX_BACKUPS = 10;
    public static final long MAX_PORTABLE_BYTES = 16L * 1024L * 1024L;
    public static final int MAX_PORTABLE_ENTITIES = 20_000;

    private final AtomicJsonStore store;
    private final List<RulesetRevision> bundled;
    private final Clock clock;
    private RulesetLibrary library;

    public LocalRulesetRepository(Path dataDirectory, List<RulesetRevision> bundled) throws IOException {
        this(new AtomicJsonStore(dataDirectory, DEFAULT_BASE_NAME, DEFAULT_MAX_BACKUPS), bundled, Clock.systemUTC());
    }

    public LocalRulesetRepository(AtomicJsonStore store, List<RulesetRevision> bundled, Clock clock) throws IOException {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
        ArrayList<RulesetRevision> standards = new ArrayList<>(Objects.requireNonNull(bundled, "bundled"));
        if (standards.isEmpty()) throw new IllegalArgumentException("At least one bundled ruleset is required");
        HashSet<String> hashes = new HashSet<>();
        for (RulesetRevision revision : standards) {
            if (!revision.readOnly()) throw new IllegalArgumentException("Bundled revision is not marked as standard");
            if (!hashes.add(revision.canonicalHash())) throw new IllegalArgumentException("Duplicate bundled hash");
        }
        standards.sort(Comparator.comparing(RulesetRevision::name));
        this.bundled = List.copyOf(standards);
        this.library = store.exists() ? loadRecoveringBackup() : RulesetLibrary.empty();
        validateReferences(this.library);
    }

    public synchronized List<RulesetRevision> revisions() {
        ArrayList<RulesetRevision> result = new ArrayList<>(bundled);
        result.addAll(library.revisions());
        result.sort(Comparator.comparing(RulesetRevision::name).thenComparing(RulesetRevision::version));
        return List.copyOf(result);
    }

    public synchronized List<RulesetProject> projects() {
        return library.projects();
    }

    public synchronized List<RulesetDraft> drafts() {
        return library.drafts();
    }

    public synchronized RulesetRevision findRevision(String canonicalHash) {
        return revisions().stream().filter(value -> value.canonicalHash().equals(canonicalHash)).findFirst().orElse(null);
    }

    public synchronized RulesetDraft findDraft(String draftId) {
        return library.drafts().stream().filter(value -> value.id().equals(draftId)).findFirst().orElse(null);
    }

    public synchronized RulesetDraft createHomebrew(String baseCanonicalHash, String name, String description)
            throws IOException {
        RulesetRevision base = requireRevision(baseCanonicalHash);
        String suffix = UUID.randomUUID().toString();
        String projectId = "local:ruleset:" + suffix;
        String now = Instant.now(clock).toString();
        RulesetProject project = new RulesetProject(projectId, name, description, base.canonicalHash(),
                List.of(), "", false);
        RulesetDraft draft = new RulesetDraft("draft:" + suffix, projectId, base.canonicalHash(), name,
                description, RulesetOrigin.HOMEBREW, base.runtime(), List.of(), List.of(), 0, now);

        ArrayList<RulesetProject> projects = new ArrayList<>(library.projects());
        projects.add(project);
        ArrayList<RulesetDraft> drafts = new ArrayList<>(library.drafts());
        drafts.add(draft);
        persist(new RulesetLibrary(projects, library.revisions(), drafts));
        return draft;
    }

    /** Apre la bozza della revisione successiva senza modificare quella già pubblicata. */
    public synchronized RulesetDraft createNextDraft(String baseCanonicalHash) throws IOException {
        RulesetRevision base = requireRevision(baseCanonicalHash);
        if (base.readOnly()) {
            throw new IllegalArgumentException("A bundled standard must first be copied to a homebrew project");
        }
        RulesetProject project = library.projects().stream()
                .filter(value -> value.id().equals(base.projectId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Ruleset project is missing"));
        if (project.archived()) throw new IllegalStateException("An archived ruleset cannot be edited");
        if (library.drafts().stream().anyMatch(value -> value.projectId().equals(project.id()))) {
            throw new IllegalStateException("This ruleset already has an editable draft");
        }

        String suffix = UUID.randomUUID().toString();
        RulesetOrigin origin = base.origin() == RulesetOrigin.SESSION_LOCAL
                ? RulesetOrigin.SESSION_LOCAL
                : RulesetOrigin.HOMEBREW;
        RulesetDraft draft = new RulesetDraft(
                "draft:" + suffix,
                project.id(),
                base.canonicalHash(),
                base.name(),
                base.description(),
                origin,
                base.runtime(),
                List.of(),
                List.of(),
                0,
                Instant.now(clock).toString());
        ArrayList<RulesetDraft> drafts = new ArrayList<>(library.drafts());
        drafts.add(draft);
        persist(new RulesetLibrary(library.projects(), library.revisions(), drafts));
        return draft;
    }

    /** Salvataggio ottimistico: una schermata vecchia non sovrascrive modifiche più recenti. */
    public synchronized RulesetDraft saveDraft(RulesetDraft changed) throws IOException {
        Objects.requireNonNull(changed, "changed");
        RulesetDraft current = findDraft(changed.id());
        if (current == null) throw new IllegalArgumentException("Unknown draft: " + changed.id());
        if (changed.saveRevision() != current.saveRevision() + 1) {
            throw new IllegalStateException("Draft changed elsewhere; reload it before saving");
        }
        requireRevision(changed.baseCanonicalHash());
        ArrayList<RulesetDraft> drafts = new ArrayList<>(library.drafts());
        drafts.removeIf(value -> value.id().equals(changed.id()));
        drafts.add(changed);
        ArrayList<RulesetProject> projects = new ArrayList<>(library.projects());
        int projectIndex = indexOfProject(projects, changed.projectId());
        if (projectIndex < 0) throw new IllegalStateException("Draft project is missing");
        projects.set(projectIndex, projects.get(projectIndex)
                .withMetadata(changed.name(), changed.description()));
        persist(new RulesetLibrary(projects, library.revisions(), drafts));
        return changed;
    }

    public synchronized RulesetRevision preview(String draftId) {
        RulesetDraft draft = requireDraft(draftId);
        return RulesetResolver.preview(requireRevision(draft.baseCanonicalHash()), draft);
    }

    public synchronized RulesetRevision publish(String draftId, String version) throws IOException {
        RulesetDraft draft = requireDraft(draftId);
        RulesetRevision revision = RulesetResolver.resolve(
                requireRevision(draft.baseCanonicalHash()), draft,
                "revision:" + UUID.randomUUID(), version, Instant.now(clock).toString());
        // Il repository è un confine di integrità anche quando viene usato senza UI.
        revision.compile();
        if (findRevision(revision.canonicalHash()) != null) {
            throw new IllegalArgumentException(
                    "This draft resolves to an already available revision; change at least one rule before publishing");
        }

        ArrayList<RulesetRevision> revisions = new ArrayList<>(library.revisions());
        revisions.add(revision);
        ArrayList<RulesetDraft> drafts = new ArrayList<>(library.drafts());
        drafts.removeIf(value -> value.id().equals(draftId));
        ArrayList<RulesetProject> projects = new ArrayList<>(library.projects());
        int projectIndex = indexOfProject(projects, draft.projectId());
        if (projectIndex < 0) throw new IllegalStateException("Draft project is missing");
        projects.set(projectIndex, projects.get(projectIndex).withPublishedRevision(revision));
        persist(new RulesetLibrary(projects, revisions, drafts));
        return revision;
    }

    public synchronized void discardDraft(String draftId) throws IOException {
        RulesetDraft draft = requireDraft(draftId);
        ArrayList<RulesetDraft> drafts = new ArrayList<>(library.drafts());
        drafts.removeIf(value -> value.id().equals(draft.id()));
        ArrayList<RulesetProject> projects = new ArrayList<>(library.projects());
        RulesetProject project = projects.stream().filter(value -> value.id().equals(draft.projectId())).findFirst().orElse(null);
        if (project != null && project.revisionHashes().isEmpty()) projects.remove(project);
        persist(new RulesetLibrary(projects, library.revisions(), drafts));
    }

    public synchronized void exportRevision(String canonicalHash, Path destination) throws IOException {
        RulesetRevision revision = requireRevision(canonicalHash);
        app.d6d.persistence.json.AtomicFiles.writeUtf8(destination,
                Json.encode(RulesetLibraryJsonCodec.encodePortableRevision(revision)));
    }

    /** Importa come linea indipendente; collisioni perfettamente identiche sono idempotenti. */
    public synchronized RulesetRevision importRevision(Path source) throws IOException {
        long fileSize = Files.size(source);
        if (fileSize > MAX_PORTABLE_BYTES) {
            throw new IllegalArgumentException("Ruleset package exceeds the supported size");
        }
        Map<String, Object> document = Json.parseObject(Files.readString(source, StandardCharsets.UTF_8));
        RulesetRevision imported = RulesetLibraryJsonCodec.decodePortableRevision(document);
        if (imported.entities().size() > MAX_PORTABLE_ENTITIES) {
            throw new IllegalArgumentException("Ruleset package contains too many entities");
        }
        imported.compile();
        RulesetRevision existing = findRevision(imported.canonicalHash());
        if (existing != null) return existing;
        if (imported.origin() == RulesetOrigin.BUNDLED_STANDARD) {
            throw new IllegalArgumentException("A bundled standard cannot be imported as editable local data");
        }
        RulesetRevision installed = remapImportedProjectOnCollision(imported);
        if (library.projects().stream().noneMatch(value -> value.id().equals(installed.projectId()))) {
            ArrayList<RulesetProject> projects = new ArrayList<>(library.projects());
            projects.add(new RulesetProject(installed.projectId(), installed.name(), installed.description(),
                    installed.baseCanonicalHash().isBlank()
                            ? installed.canonicalHash()
                            : installed.baseCanonicalHash(),
                    List.of(installed.canonicalHash()), installed.canonicalHash(), false));
            ArrayList<RulesetRevision> revisions = new ArrayList<>(library.revisions());
            revisions.add(installed);
            persist(new RulesetLibrary(projects, revisions, library.drafts()));
        } else {
            ArrayList<RulesetProject> projects = new ArrayList<>(library.projects());
            int index = indexOfProject(projects, installed.projectId());
            projects.set(index, projects.get(index).withPublishedRevision(installed));
            ArrayList<RulesetRevision> revisions = new ArrayList<>(library.revisions());
            revisions.add(installed);
            persist(new RulesetLibrary(projects, revisions, library.drafts()));
        }
        return installed;
    }

    private void persist(RulesetLibrary changed) throws IOException {
        validateReferences(changed);
        store.save(RulesetLibraryJsonCodec.encode(changed));
        library = changed;
    }

    private RulesetLibrary loadRecoveringBackup() throws IOException {
        RuntimeException lastFailure = null;
        for (AtomicJsonStore.ObjectCandidate candidate : store.loadObjectCandidatesNewestFirst()) {
            try {
                RulesetLibrary decoded = RulesetLibraryJsonCodec.decode(candidate.document());
                validateReferences(decoded);
                if (!candidate.current()) {
                    // Ripristina atomicamente il primo backup semanticamente valido.
                    store.save(RulesetLibraryJsonCodec.encode(decoded));
                }
                return decoded;
            } catch (RuntimeException failure) {
                lastFailure = failure;
            }
        }
        throw new IOException("No valid ruleset library or backup is available", lastFailure);
    }

    private RulesetRevision requireRevision(String hash) {
        RulesetRevision value = findRevision(hash);
        if (value == null) throw new IllegalArgumentException("Unknown base ruleset: " + hash);
        return value;
    }

    private RulesetDraft requireDraft(String id) {
        RulesetDraft value = findDraft(id);
        if (value == null) throw new IllegalArgumentException("Unknown draft: " + id);
        return value;
    }

    private void validateReferences(RulesetLibrary value) {
        HashSet<String> knownHashes = new HashSet<>();
        bundled.forEach(revision -> knownHashes.add(revision.canonicalHash()));
        value.revisions().forEach(revision -> knownHashes.add(revision.canonicalHash()));
        HashSet<String> projects = new HashSet<>();
        value.projects().forEach(project -> projects.add(project.id()));
        for (RulesetRevision revision : value.revisions()) {
            if (!projects.contains(revision.projectId())) throw new IllegalArgumentException("Revision project is missing");
            // A published revision is fully resolved and can therefore remain portable even when
            // its ancestry is not installed. Drafts still require their exact base below.
        }
        for (RulesetProject project : value.projects()) {
            for (String revisionHash : project.revisionHashes()) {
                RulesetRevision revision = value.revisions().stream()
                        .filter(candidate -> candidate.canonicalHash().equals(revisionHash))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Project revision is missing: " + revisionHash));
                if (!revision.projectId().equals(project.id())) {
                    throw new IllegalArgumentException("Project references a revision owned by another project");
                }
            }
        }
        for (RulesetDraft draft : value.drafts()) {
            if (!projects.contains(draft.projectId())) throw new IllegalArgumentException("Draft project is missing");
            if (!knownHashes.contains(draft.baseCanonicalHash())) throw new IllegalArgumentException("Draft base is missing");
        }
    }

    private static int indexOfProject(List<RulesetProject> projects, String id) {
        for (int index = 0; index < projects.size(); index++) if (projects.get(index).id().equals(id)) return index;
        return -1;
    }

    /** Un ID uguale con contenuto diverso viene installato affiancato, mai fuso implicitamente. */
    private RulesetRevision remapImportedProjectOnCollision(RulesetRevision imported) {
        if (library.projects().stream().noneMatch(value -> value.id().equals(imported.projectId()))) {
            return imported;
        }
        String prefix = imported.projectId() + ":import:" + imported.canonicalHash().substring(0, 12);
        String projectId = prefix;
        int suffix = 2;
        while (indexOfProject(library.projects(), projectId) >= 0) {
            projectId = prefix + '-' + suffix++;
        }
        return new RulesetRevision(
                projectId,
                imported.revisionId(),
                imported.version(),
                imported.name(),
                imported.description(),
                imported.origin(),
                imported.baseCanonicalHash(),
                imported.runtime(),
                imported.entities(),
                imported.publishedAt(),
                imported.canonicalHash(),
                imported.runtimeHash());
    }
}
