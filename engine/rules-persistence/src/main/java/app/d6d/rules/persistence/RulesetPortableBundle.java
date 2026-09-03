package app.d6d.rules.persistence;

import app.d6d.rules.model.RulesetCompositionLock;
import app.d6d.rules.model.RulesetModule;
import app.d6d.rules.model.RulesetModuleRef;
import app.d6d.rules.model.RulesetRevision;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Revisione eseguibile, lock e grafo chiuso dei moduli esatti.
 * La revisione base non è incorporata: lo snapshot resta giocabile senza di essa,
 * mentre ricomposizione e rebase richiedono che la base sia installata separatamente.
 */
public record RulesetPortableBundle(
        RulesetRevision revision,
        RulesetCompositionLock lock,
        List<RulesetModule> modules) {

    public RulesetPortableBundle {
        revision = Objects.requireNonNull(revision, "revision");
        lock = Objects.requireNonNull(lock, "lock");
        if (!revision.baseCanonicalHash().equals(lock.baseCanonicalHash())) {
            throw new IllegalArgumentException("Revision and composition lock have different bases");
        }

        ArrayList<RulesetModule> normalized = new ArrayList<>(Objects.requireNonNull(modules, "modules"));
        normalized.forEach(value -> Objects.requireNonNull(value, "modules contains null"));
        normalized.sort(java.util.Comparator.comparing(RulesetModule::id)
                .thenComparing(RulesetModule::canonicalHash));
        HashSet<String> hashes = new HashSet<>();
        HashMap<String, RulesetModule> byId = new HashMap<>();
        for (RulesetModule module : normalized) {
            if (!hashes.add(module.canonicalHash())) {
                throw new IllegalArgumentException("Duplicate module hash in portable bundle");
            }
            if (byId.putIfAbsent(module.id(), module) != null) {
                throw new IllegalArgumentException("Duplicate module id in portable bundle: " + module.id());
            }
        }
        for (RulesetModuleRef reference : lock.modules()) {
            RulesetModule module = byId.get(reference.moduleId());
            if (module == null || !module.canonicalHash().equals(reference.canonicalHash())) {
                throw new IllegalArgumentException("Portable bundle is missing exact module " + reference.moduleId());
            }
        }
        if (byId.size() != lock.modules().size()) {
            throw new IllegalArgumentException("Portable bundle contains modules not referenced by its lock");
        }

        HashMap<String, Integer> positions = new HashMap<>();
        for (int index = 0; index < lock.modules().size(); index++) {
            positions.put(lock.modules().get(index).moduleId(), index);
        }
        for (RulesetModuleRef reference : lock.modules()) {
            RulesetModule module = byId.get(reference.moduleId());
            int modulePosition = positions.get(module.id());
            for (RulesetModuleRef dependency : module.dependencies()) {
                RulesetModule exactDependency = byId.get(dependency.moduleId());
                if (exactDependency == null) {
                    throw new IllegalArgumentException(
                            "Portable bundle is missing dependency " + dependency.moduleId()
                                    + " required by " + module.id());
                }
                if (!exactDependency.canonicalHash().equals(dependency.canonicalHash())) {
                    throw new IllegalArgumentException(
                            "Portable bundle is missing exact dependency " + dependency.moduleId()
                                    + " required by " + module.id());
                }
                if (positions.get(dependency.moduleId()) >= modulePosition) {
                    throw new IllegalArgumentException(
                            "Portable bundle dependency " + dependency.moduleId()
                                    + " must precede " + module.id() + " in the composition lock");
                }
            }
        }
        modules = List.copyOf(normalized);
    }
}
