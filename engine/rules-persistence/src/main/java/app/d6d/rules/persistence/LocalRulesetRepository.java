package app.d6d.rules.persistence;

import app.d6d.persistence.json.AtomicJsonStore;
import app.d6d.persistence.json.Json;
import app.d6d.rules.authoring.RulesetAuthoringState;
import app.d6d.rules.model.RulesetComposer;
import app.d6d.rules.model.RulesetCompositionLock;
import app.d6d.rules.model.RulesetCompositionResult;
import app.d6d.rules.model.RulesetConflictResolution;
import app.d6d.rules.model.RulesetDraft;
import app.d6d.rules.model.RulesetModule;
import app.d6d.rules.model.RulesetModuleRef;
import app.d6d.rules.model.RulesetOrigin;
import app.d6d.rules.model.RulesetProject;
import app.d6d.rules.model.RulesetResolver;
import app.d6d.rules.model.RulesetRevision;
import app.d6d.rules.model.RulesetRuntimeConfig;

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
    public static final int MAX_PORTABLE_MODULES = 512;
    public static final int MAX_PORTABLE_MODULE_OPERATIONS = 20_000;

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

    public synchronized List<RulesetModule> modules() {
        return library.modules();
    }

    public synchronized RulesetAuthoringState authoringState() {
        return library.authoring();
    }

    public synchronized RulesetModule findModule(String canonicalHash) {
        Objects.requireNonNull(canonicalHash, "canonicalHash");
        return library.modules().stream()
                .filter(value -> value.canonicalHash().equals(canonicalHash))
                .findFirst().orElse(null);
    }

    public synchronized RulesetCompositionLock findCompositionLock(String revisionCanonicalHash) {
        Objects.requireNonNull(revisionCanonicalHash, "revisionCanonicalHash");
        return library.compositions().stream()
                .filter(value -> value.revisionCanonicalHash().equals(revisionCanonicalHash))
                .map(StoredRulesetComposition::lock)
                .findFirst().orElse(null);
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
        persist(library.withCoreContent(projects, library.revisions(), drafts));
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
        persist(library.withCoreContent(library.projects(), library.revisions(), drafts));
        return draft;
    }

    /** Salvataggio ottimistico: una schermata vecchia non sovrascrive modifiche più recenti. */
    public synchronized RulesetDraft saveDraft(RulesetDraft changed) throws IOException {
        return saveDraft(changed, library.authoring());
    }

    /** Salva contenuto e metadati visuali nella stessa sostituzione atomica. */
    public synchronized RulesetDraft saveDraft(
            RulesetDraft changed,
            RulesetAuthoringState changedAuthoring
    ) throws IOException {
        Objects.requireNonNull(changed, "changed");
        Objects.requireNonNull(changedAuthoring, "changedAuthoring");
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
        persist(library.withCoreContent(projects, library.revisions(), drafts)
                .withAuthoring(changedAuthoring));
        return changed;
    }

    /** Aggiornamento atomico dei soli metadati, utile per esempi e layout locali. */
    public synchronized void saveAuthoringState(RulesetAuthoringState changedAuthoring)
            throws IOException {
        persist(library.withAuthoring(Objects.requireNonNull(changedAuthoring, "changedAuthoring")));
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
        persist(library.withCoreContent(projects, revisions, drafts)
                .withAuthoring(library.authoring().withoutDraft(draftId)));
        return revision;
    }

    public synchronized void discardDraft(String draftId) throws IOException {
        RulesetDraft draft = requireDraft(draftId);
        ArrayList<RulesetDraft> drafts = new ArrayList<>(library.drafts());
        drafts.removeIf(value -> value.id().equals(draft.id()));
        ArrayList<RulesetProject> projects = new ArrayList<>(library.projects());
        RulesetProject project = projects.stream().filter(value -> value.id().equals(draft.projectId())).findFirst().orElse(null);
        if (project != null && project.revisionHashes().isEmpty()) projects.remove(project);
        persist(library.withCoreContent(projects, library.revisions(), drafts)
                .withAuthoring(library.authoring().withoutDraft(draftId)));
    }

    /** Installa una versione esatta; versioni diverse dello stesso ID possono coesistere. */
    public synchronized RulesetModule installModule(RulesetModule module) throws IOException {
        Objects.requireNonNull(module, "module");
        validatePortableModule(module);
        RulesetModule existing = findModule(module.canonicalHash());
        if (existing != null) return existing;
        if (module.origin() == RulesetOrigin.BUNDLED_STANDARD) {
            throw new IllegalArgumentException("A bundled module cannot be installed as editable local data");
        }
        ArrayList<RulesetModule> modules = new ArrayList<>(library.modules());
        modules.add(module);
        persist(new RulesetLibrary(
                library.projects(), library.revisions(), library.drafts(), modules,
                library.compositions(), library.authoring()));
        return module;
    }

    /**
     * Crea una nuova linea homebrew da base + moduli installati e salva revisione
     * appiattita e lock nella stessa transazione atomica.
     */
    public synchronized RulesetCompositionResult publishComposition(
            String baseCanonicalHash,
            List<String> orderedModuleHashes,
            List<RulesetConflictResolution> resolutions,
            RulesetRuntimeConfig runtime,
            String name,
            String description,
            String version) throws IOException {
        RulesetRevision base = requireRevision(baseCanonicalHash);
        ArrayList<RulesetModule> selected = new ArrayList<>();
        for (String hash : Objects.requireNonNull(orderedModuleHashes, "orderedModuleHashes")) {
            RulesetModule module = findModule(Objects.requireNonNull(hash, "orderedModuleHashes contains null"));
            if (module == null) throw new IllegalArgumentException("Unknown ruleset module: " + hash);
            selected.add(module);
        }

        String suffix = UUID.randomUUID().toString();
        String projectId = "local:ruleset-composition:" + suffix;
        RulesetCompositionResult result = RulesetComposer.compose(
                base, selected, Objects.requireNonNull(resolutions, "resolutions"),
                Objects.requireNonNull(runtime, "runtime"), projectId, "revision:" + UUID.randomUUID(),
                version, name, description, RulesetOrigin.HOMEBREW, Instant.now(clock).toString());
        result.revision().compile();
        if (findRevision(result.revision().canonicalHash()) != null) {
            throw new IllegalArgumentException("This composition is already available");
        }

        RulesetProject project = new RulesetProject(
                projectId, name, description, base.canonicalHash(),
                List.of(result.revision().canonicalHash()), result.revision().canonicalHash(), false);
        ArrayList<RulesetProject> projects = new ArrayList<>(library.projects());
        projects.add(project);
        ArrayList<RulesetRevision> revisions = new ArrayList<>(library.revisions());
        revisions.add(result.revision());
        ArrayList<StoredRulesetComposition> compositions = new ArrayList<>(library.compositions());
        compositions.add(new StoredRulesetComposition(
                result.revision().canonicalHash(), result.lock()));
        persist(new RulesetLibrary(
                projects, revisions, library.drafts(), library.modules(), compositions,
                library.authoring()));
        return result;
    }

    public synchronized void exportModule(String canonicalHash, Path destination) throws IOException {
        RulesetModule module = findModule(canonicalHash);
        if (module == null) throw new IllegalArgumentException("Unknown ruleset module: " + canonicalHash);
        app.d6d.persistence.json.AtomicFiles.writeUtf8(destination,
                Json.encode(RulesetLibraryJsonCodec.encodePortableModule(module)));
    }

    public synchronized RulesetModule importModule(Path source) throws IOException {
        requirePortableSize(source);
        RulesetModule module = RulesetLibraryJsonCodec.decodePortableModule(
                Json.parseObject(Files.readString(source, StandardCharsets.UTF_8)));
        return installModule(module);
    }

    public synchronized void exportBundle(String revisionCanonicalHash, Path destination) throws IOException {
        RulesetRevision revision = requireRevision(revisionCanonicalHash);
        RulesetCompositionLock lock = findCompositionLock(revisionCanonicalHash);
        if (lock == null) {
            throw new IllegalArgumentException("Revision does not have a stored composition lock");
        }
        ArrayList<RulesetModule> modules = new ArrayList<>();
        for (RulesetModuleRef reference : lock.modules()) {
            RulesetModule module = findModule(reference.canonicalHash());
            if (module == null || !module.id().equals(reference.moduleId())) {
                throw new IllegalStateException("Exact module is not installed: " + reference.moduleId());
            }
            modules.add(module);
        }
        RulesetPortableBundle bundle = new RulesetPortableBundle(revision, lock, modules);
        app.d6d.persistence.json.AtomicFiles.writeUtf8(destination,
                Json.encode(RulesetLibraryJsonCodec.encodePortableBundle(bundle)));
    }

    /** Import atomico di snapshot, lock e moduli; nessuna dipendenza viene scaricata dalla rete. */
    public synchronized RulesetCompositionResult importBundle(Path source) throws IOException {
        requirePortableSize(source);
        RulesetPortableBundle bundle = RulesetLibraryJsonCodec.decodePortableBundle(
                Json.parseObject(Files.readString(source, StandardCharsets.UTF_8)));
        validatePortableRevision(bundle.revision());
        validatePortableBundle(bundle);
        bundle.revision().compile();
        validateBundleCompositionWhenBaseAvailable(bundle);

        if (bundle.revision().origin() == RulesetOrigin.BUNDLED_STANDARD) {
            throw new IllegalArgumentException("A bundled standard cannot be imported as editable local data");
        }
        for (RulesetModule module : bundle.modules()) {
            if (module.origin() == RulesetOrigin.BUNDLED_STANDARD) {
                throw new IllegalArgumentException("A bundled module cannot be imported as editable local data");
            }
        }

        RulesetRevision existingRevision = findRevision(bundle.revision().canonicalHash());
        RulesetRevision installed = existingRevision == null
                ? remapImportedProjectOnCollision(bundle.revision())
                : existingRevision;
        RulesetCompositionLock existingLock = findCompositionLock(installed.canonicalHash());
        if (existingLock != null && !existingLock.equals(bundle.lock())) {
            throw new IllegalArgumentException("Revision already has a different composition lock");
        }

        ArrayList<RulesetModule> modules = new ArrayList<>(library.modules());
        for (RulesetModule module : bundle.modules()) {
            if (modules.stream().noneMatch(value -> value.canonicalHash().equals(module.canonicalHash()))) {
                modules.add(module);
            }
        }
        ArrayList<RulesetProject> projects = new ArrayList<>(library.projects());
        ArrayList<RulesetRevision> revisions = new ArrayList<>(library.revisions());
        if (existingRevision == null) {
            projects.add(new RulesetProject(
                    installed.projectId(), installed.name(), installed.description(),
                    installed.baseCanonicalHash().isBlank()
                            ? installed.canonicalHash()
                            : installed.baseCanonicalHash(),
                    List.of(installed.canonicalHash()), installed.canonicalHash(), false));
            revisions.add(installed);
        }
        ArrayList<StoredRulesetComposition> compositions = new ArrayList<>(library.compositions());
        if (existingLock == null) {
            compositions.add(new StoredRulesetComposition(installed.canonicalHash(), bundle.lock()));
        }
        persist(new RulesetLibrary(
                projects, revisions, library.drafts(), modules, compositions, library.authoring()));
        return new RulesetCompositionResult(installed, bundle.lock());
    }

    public synchronized void exportRevision(String canonicalHash, Path destination) throws IOException {
        RulesetRevision revision = requireRevision(canonicalHash);
        app.d6d.persistence.json.AtomicFiles.writeUtf8(destination,
                Json.encode(RulesetLibraryJsonCodec.encodePortableRevision(revision)));
    }

    /** Importa come linea indipendente; collisioni perfettamente identiche sono idempotenti. */
    public synchronized RulesetRevision importRevision(Path source) throws IOException {
        requirePortableSize(source);
        Map<String, Object> document = Json.parseObject(Files.readString(source, StandardCharsets.UTF_8));
        RulesetRevision imported = RulesetLibraryJsonCodec.decodePortableRevision(document);
        validatePortableRevision(imported);
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
            persist(library.withCoreContent(projects, revisions, library.drafts()));
        } else {
            ArrayList<RulesetProject> projects = new ArrayList<>(library.projects());
            int index = indexOfProject(projects, installed.projectId());
            projects.set(index, projects.get(index).withPublishedRevision(installed));
            ArrayList<RulesetRevision> revisions = new ArrayList<>(library.revisions());
            revisions.add(installed);
            persist(library.withCoreContent(projects, revisions, library.drafts()));
        }
        return installed;
    }

    private static void requirePortableSize(Path source) throws IOException {
        Objects.requireNonNull(source, "source");
        long fileSize = Files.size(source);
        if (fileSize > MAX_PORTABLE_BYTES) {
            throw new IllegalArgumentException("Ruleset package exceeds the supported size");
        }
    }

    private static void validatePortableRevision(RulesetRevision revision) {
        if (revision.entities().size() > MAX_PORTABLE_ENTITIES) {
            throw new IllegalArgumentException("Ruleset package contains too many entities");
        }
    }

    private static void validatePortableModule(RulesetModule module) {
        long operations = (long) module.patches().size() + module.additions().size();
        if (operations > MAX_PORTABLE_MODULE_OPERATIONS) {
            throw new IllegalArgumentException("Ruleset module contains too many operations");
        }
    }

    private static void validatePortableBundle(RulesetPortableBundle bundle) {
        if (bundle.modules().size() > MAX_PORTABLE_MODULES) {
            throw new IllegalArgumentException("Ruleset bundle contains too many modules");
        }
        long operations = 0;
        for (RulesetModule module : bundle.modules()) {
            validatePortableModule(module);
            operations += (long) module.patches().size() + module.additions().size();
            if (operations > MAX_PORTABLE_MODULE_OPERATIONS) {
                throw new IllegalArgumentException("Ruleset bundle contains too many module operations");
            }
        }
    }

    private void validateBundleCompositionWhenBaseAvailable(RulesetPortableBundle bundle) {
        RulesetRevision base = findRevision(bundle.lock().baseCanonicalHash());
        if (base == null) {
            // Lo snapshot appiattito resta importabile offline. Il lock e ogni modulo
            // sono verificati per hash, ma il rebase richiederà l'installazione della base esatta.
            return;
        }
        ArrayList<RulesetModule> ordered = new ArrayList<>();
        for (RulesetModuleRef reference : bundle.lock().modules()) {
            RulesetModule module = bundle.modules().stream()
                    .filter(candidate -> candidate.canonicalHash().equals(reference.canonicalHash())
                            && candidate.id().equals(reference.moduleId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Bundle is missing exact module " + reference.moduleId()));
            ordered.add(module);
        }
        RulesetRevision claimed = bundle.revision();
        RulesetCompositionResult recomposed = RulesetComposer.compose(
                base, ordered, bundle.lock().resolutions(), claimed.runtime(),
                claimed.projectId(), claimed.revisionId(), claimed.version(), claimed.name(),
                claimed.description(), claimed.origin(), claimed.publishedAt());
        if (!recomposed.revision().canonicalHash().equals(claimed.canonicalHash())
                || !recomposed.revision().runtimeHash().equals(claimed.runtimeHash())
                || !recomposed.lock().equals(bundle.lock())) {
            throw new IllegalArgumentException(
                    "Flattened revision does not match the composition declared by the bundle");
        }
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
        HashSet<String> draftIds = new HashSet<>();
        value.drafts().forEach(draft -> draftIds.add(draft.id()));
        for (String draftId : value.authoring().byDraftId().keySet()) {
            if (!draftIds.contains(draftId)) {
                throw new IllegalArgumentException("Authoring metadata draft is missing: " + draftId);
            }
        }
        for (RulesetModule module : value.modules()) {
            if (module.origin() == RulesetOrigin.BUNDLED_STANDARD) {
                throw new IllegalArgumentException("Bundled modules do not belong in the local library");
            }
        }
        for (StoredRulesetComposition composition : value.compositions()) {
            RulesetRevision revision = java.util.stream.Stream.concat(
                            bundled.stream(), value.revisions().stream())
                    .filter(candidate -> candidate.canonicalHash().equals(composition.revisionCanonicalHash()))
                    .findFirst().orElse(null);
            if (revision == null) {
                throw new IllegalArgumentException(
                        "Composition revision is missing: " + composition.revisionCanonicalHash());
            }
            if (!revision.baseCanonicalHash().equals(composition.lock().baseCanonicalHash())) {
                throw new IllegalArgumentException("Composition lock base differs from its flattened revision");
            }
            // I moduli possono mancare: la revisione appiattita resta eseguibile offline.
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
