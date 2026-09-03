package app.d6d.rules.persistence;

import app.d6d.rules.authoring.RuleAuthoringExample;
import app.d6d.rules.authoring.RuleAuthoringMetadata;
import app.d6d.rules.authoring.RulesetAuthoringState;
import app.d6d.rules.model.LocalizedRuleText;
import app.d6d.rules.model.RuleAutomationLevel;
import app.d6d.rules.model.RuleEntity;
import app.d6d.rules.model.RuleFieldRef;
import app.d6d.rules.model.RuleKind;
import app.d6d.rules.model.RulePatch;
import app.d6d.rules.model.RulesetCompositionLock;
import app.d6d.rules.model.RulesetConflictResolution;
import app.d6d.rules.model.RulesetDraft;
import app.d6d.rules.model.RulesetModule;
import app.d6d.rules.model.RulesetModuleRef;
import app.d6d.rules.model.RulesetOrigin;
import app.d6d.rules.model.RulesetProject;
import app.d6d.rules.model.RulesetRevision;
import app.d6d.rules.model.RulesetRuntimeConfig;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Codec esplicito e verificabile del formato portabile dei regolamenti. */
public final class RulesetLibraryJsonCodec {
    public static final int SCHEMA_VERSION = 3;
    private static final int FIRST_SUPPORTED_SCHEMA_VERSION = 1;

    private RulesetLibraryJsonCodec() {
    }

    public static Map<String, Object> encode(RulesetLibrary library) {
        LinkedHashMap<String, Object> document = new LinkedHashMap<>();
        document.put("schemaVersion", SCHEMA_VERSION);
        document.put("projects", library.projects().stream().map(RulesetLibraryJsonCodec::encodeProject).toList());
        document.put("revisions", library.revisions().stream().map(RulesetLibraryJsonCodec::encodeRevision).toList());
        document.put("drafts", library.drafts().stream().map(RulesetLibraryJsonCodec::encodeDraft).toList());
        document.put("modules", library.modules().stream().map(RulesetLibraryJsonCodec::encodeModule).toList());
        document.put("compositions", library.compositions().stream()
                .map(RulesetLibraryJsonCodec::encodeStoredComposition).toList());
        document.put("authoring", encodeAuthoring(library.authoring()));
        return document;
    }

    public static RulesetLibrary decode(Map<String, Object> document) {
        String root = "$";
        int schema = integer(required(document, "schemaVersion", root), "$.schemaVersion");
        if (!supportedSchema(schema)) {
            throw format("$.schemaVersion", "unsupported schema version " + schema);
        }
        return new RulesetLibrary(
                mapArray(required(document, "projects", root), "$.projects", RulesetLibraryJsonCodec::decodeProject),
                mapArray(required(document, "revisions", root), "$.revisions", RulesetLibraryJsonCodec::decodeRevision),
                mapArray(required(document, "drafts", root), "$.drafts", RulesetLibraryJsonCodec::decodeDraft),
                schema >= 2
                        ? mapArray(required(document, "modules", root), "$.modules", RulesetLibraryJsonCodec::decodeModule)
                        : List.of(),
                schema >= 2
                        ? mapArray(required(document, "compositions", root), "$.compositions",
                                RulesetLibraryJsonCodec::decodeStoredComposition)
                        : List.of(),
                schema >= 3 && document.get("authoring") != null
                        ? decodeAuthoring(object(document.get("authoring"), "$.authoring"), "$.authoring")
                        : RulesetAuthoringState.empty());
    }

    private static Map<String, Object> encodeAuthoring(RulesetAuthoringState value) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("schemaVersion", value.schemaVersion());
        LinkedHashMap<String, Object> drafts = new LinkedHashMap<>();
        value.byDraftId().forEach((draftId, groups) -> {
            LinkedHashMap<String, Object> encodedGroups = new LinkedHashMap<>();
            groups.forEach((groupId, metadata) ->
                    encodedGroups.put(groupId, encodeAuthoringMetadata(metadata)));
            drafts.put(draftId, encodedGroups);
        });
        map.put("byDraftId", drafts);
        return map;
    }

    private static RulesetAuthoringState decodeAuthoring(Map<String, Object> map, String path) {
        int schema = integer(required(map, "schemaVersion", path), path + ".schemaVersion");
        Map<String, Object> encodedDrafts = object(required(map, "byDraftId", path), path + ".byDraftId");
        LinkedHashMap<String, Map<String, RuleAuthoringMetadata>> drafts = new LinkedHashMap<>();
        encodedDrafts.forEach((draftId, rawGroups) -> {
            String draftPath = path + ".byDraftId." + draftId;
            Map<String, Object> encodedGroups = object(rawGroups, draftPath);
            LinkedHashMap<String, RuleAuthoringMetadata> groups = new LinkedHashMap<>();
            encodedGroups.forEach((groupId, rawMetadata) -> groups.put(
                    groupId,
                    decodeAuthoringMetadata(object(rawMetadata, draftPath + '.' + groupId),
                            draftPath + '.' + groupId)));
            drafts.put(draftId, groups);
        });
        try {
            return new RulesetAuthoringState(schema, drafts);
        } catch (IllegalArgumentException failure) {
            throw format(path, failure.getMessage());
        }
    }

    private static Map<String, Object> encodeAuthoringMetadata(RuleAuthoringMetadata value) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("recipeId", value.recipeId());
        map.put("recipeVersion", value.recipeVersion());
        map.put("generatedEntityIds", value.generatedEntityIds());
        map.put("visualSections", value.visualSections());
        map.put("protectedFields", value.protectedFields().stream().sorted().toList());
        map.put("lastProjectedContentHashes", value.lastProjectedContentHashes());
        map.put("examples", value.examples().stream()
                .map(RulesetLibraryJsonCodec::encodeAuthoringExample).toList());
        return map;
    }

    private static RuleAuthoringMetadata decodeAuthoringMetadata(Map<String, Object> map, String path) {
        return new RuleAuthoringMetadata(
                text(required(map, "recipeId", path), path + ".recipeId"),
                integer(required(map, "recipeVersion", path), path + ".recipeVersion"),
                stringList(required(map, "generatedEntityIds", path), path + ".generatedEntityIds"),
                stringMap(required(map, "visualSections", path), path + ".visualSections"),
                Set.copyOf(stringList(required(map, "protectedFields", path), path + ".protectedFields")),
                stringMap(required(map, "lastProjectedContentHashes", path),
                        path + ".lastProjectedContentHashes"),
                mapArray(required(map, "examples", path), path + ".examples",
                        RulesetLibraryJsonCodec::decodeAuthoringExample));
    }

    private static Map<String, Object> encodeAuthoringExample(RuleAuthoringExample value) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("id", value.id());
        map.put("inputs", value.inputs());
        map.put("expectedResult", value.expectedResult());
        return map;
    }

    private static RuleAuthoringExample decodeAuthoringExample(Map<String, Object> map, String path) {
        return new RuleAuthoringExample(
                text(required(map, "id", path), path + ".id"),
                stringMap(required(map, "inputs", path), path + ".inputs"),
                text(required(map, "expectedResult", path), path + ".expectedResult"));
    }

    /** Un solo regolamento pubblicato, utile per import/export senza includere la libreria intera. */
    public static Map<String, Object> encodePortableRevision(RulesetRevision revision) {
        LinkedHashMap<String, Object> document = new LinkedHashMap<>();
        document.put("schemaVersion", SCHEMA_VERSION);
        document.put("format", "onfall-ruleset-revision");
        document.put("revision", encodeRevision(revision));
        return document;
    }

    public static RulesetRevision decodePortableRevision(Map<String, Object> document) {
        int schema = integer(required(document, "schemaVersion", "$"), "$.schemaVersion");
        if (!supportedSchema(schema)) throw format("$.schemaVersion", "unsupported schema version " + schema);
        String format = text(required(document, "format", "$"), "$.format");
        if (!"onfall-ruleset-revision".equals(format)) throw format("$.format", "unknown ruleset format");
        return decodeRevision(object(required(document, "revision", "$"), "$.revision"), "$.revision");
    }

    public static Map<String, Object> encodePortableModule(RulesetModule module) {
        LinkedHashMap<String, Object> document = new LinkedHashMap<>();
        document.put("schemaVersion", SCHEMA_VERSION);
        document.put("format", "onfall-ruleset-module");
        document.put("module", encodeModule(module));
        return document;
    }

    public static RulesetModule decodePortableModule(Map<String, Object> document) {
        validatePortableHeader(document, "onfall-ruleset-module");
        return decodeModule(object(required(document, "module", "$"), "$.module"), "$.module");
    }

    public static Map<String, Object> encodePortableBundle(RulesetPortableBundle bundle) {
        LinkedHashMap<String, Object> document = new LinkedHashMap<>();
        document.put("schemaVersion", SCHEMA_VERSION);
        document.put("format", "onfall-ruleset-bundle");
        document.put("revision", encodeRevision(bundle.revision()));
        document.put("lock", encodeCompositionLock(bundle.lock()));
        document.put("modules", bundle.modules().stream().map(RulesetLibraryJsonCodec::encodeModule).toList());
        return document;
    }

    public static RulesetPortableBundle decodePortableBundle(Map<String, Object> document) {
        validatePortableHeader(document, "onfall-ruleset-bundle");
        try {
            return new RulesetPortableBundle(
                    decodeRevision(object(required(document, "revision", "$"), "$.revision"), "$.revision"),
                    decodeCompositionLock(object(required(document, "lock", "$"), "$.lock"), "$.lock"),
                    mapArray(required(document, "modules", "$"), "$.modules",
                            RulesetLibraryJsonCodec::decodeModule));
        } catch (IllegalArgumentException exception) {
            throw format("$", exception.getMessage());
        }
    }

    private static void validatePortableHeader(Map<String, Object> document, String expectedFormat) {
        int schema = integer(required(document, "schemaVersion", "$"), "$.schemaVersion");
        if (!supportedSchema(schema)) throw format("$.schemaVersion", "unsupported schema version " + schema);
        String format = text(required(document, "format", "$"), "$.format");
        if (!expectedFormat.equals(format)) throw format("$.format", "unknown ruleset format");
    }

    private static boolean supportedSchema(int schema) {
        return schema >= FIRST_SUPPORTED_SCHEMA_VERSION && schema <= SCHEMA_VERSION;
    }

    private static Map<String, Object> encodeModule(RulesetModule value) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("id", value.id());
        map.put("version", value.version());
        map.put("name", encodeLocalized(value.name()));
        map.put("description", encodeLocalized(value.description()));
        map.put("origin", value.origin().name());
        map.put("requiredSemanticsVersion", value.requiredSemanticsVersion());
        map.put("dependencies", value.dependencies().stream()
                .map(RulesetLibraryJsonCodec::encodeModuleRef).toList());
        map.put("incompatibleModuleIds", value.incompatibleModuleIds().stream().sorted().toList());
        map.put("patches", value.patches().stream().map(RulesetLibraryJsonCodec::encodePatch).toList());
        map.put("additions", value.additions().stream().map(RulesetLibraryJsonCodec::encodeEntity).toList());
        map.put("canonicalHash", value.canonicalHash());
        return map;
    }

    private static RulesetModule decodeModule(Map<String, Object> map, String path) {
        try {
            return new RulesetModule(
                    text(required(map, "id", path), path + ".id"),
                    text(required(map, "version", path), path + ".version"),
                    decodeLocalized(object(required(map, "name", path), path + ".name"), path + ".name"),
                    decodeLocalized(object(required(map, "description", path), path + ".description"),
                            path + ".description"),
                    enumeration(RulesetOrigin.class, required(map, "origin", path), path + ".origin"),
                    text(required(map, "requiredSemanticsVersion", path), path + ".requiredSemanticsVersion"),
                    mapArray(required(map, "dependencies", path), path + ".dependencies",
                            RulesetLibraryJsonCodec::decodeModuleRef),
                    Set.copyOf(stringList(required(map, "incompatibleModuleIds", path),
                            path + ".incompatibleModuleIds")),
                    mapArray(required(map, "patches", path), path + ".patches",
                            RulesetLibraryJsonCodec::decodePatch),
                    mapArray(required(map, "additions", path), path + ".additions",
                            RulesetLibraryJsonCodec::decodeEntity),
                    text(required(map, "canonicalHash", path), path + ".canonicalHash"));
        } catch (IllegalArgumentException exception) {
            throw format(path, exception.getMessage());
        }
    }

    private static Map<String, Object> encodeModuleRef(RulesetModuleRef value) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("moduleId", value.moduleId());
        map.put("canonicalHash", value.canonicalHash());
        return map;
    }

    private static RulesetModuleRef decodeModuleRef(Map<String, Object> map, String path) {
        try {
            return new RulesetModuleRef(
                    text(required(map, "moduleId", path), path + ".moduleId"),
                    text(required(map, "canonicalHash", path), path + ".canonicalHash"));
        } catch (IllegalArgumentException exception) {
            throw format(path, exception.getMessage());
        }
    }

    private static Map<String, Object> encodeStoredComposition(StoredRulesetComposition value) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("revisionCanonicalHash", value.revisionCanonicalHash());
        map.put("lock", encodeCompositionLock(value.lock()));
        return map;
    }

    private static StoredRulesetComposition decodeStoredComposition(Map<String, Object> map, String path) {
        try {
            return new StoredRulesetComposition(
                    text(required(map, "revisionCanonicalHash", path), path + ".revisionCanonicalHash"),
                    decodeCompositionLock(object(required(map, "lock", path), path + ".lock"),
                            path + ".lock"));
        } catch (IllegalArgumentException exception) {
            throw format(path, exception.getMessage());
        }
    }

    private static Map<String, Object> encodeCompositionLock(RulesetCompositionLock value) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("baseCanonicalHash", value.baseCanonicalHash());
        map.put("modules", value.modules().stream().map(RulesetLibraryJsonCodec::encodeModuleRef).toList());
        map.put("resolutions", value.resolutions().stream()
                .map(RulesetLibraryJsonCodec::encodeConflictResolution).toList());
        map.put("canonicalHash", value.canonicalHash());
        return map;
    }

    private static RulesetCompositionLock decodeCompositionLock(Map<String, Object> map, String path) {
        try {
            return new RulesetCompositionLock(
                    text(required(map, "baseCanonicalHash", path), path + ".baseCanonicalHash"),
                    mapArray(required(map, "modules", path), path + ".modules",
                            RulesetLibraryJsonCodec::decodeModuleRef),
                    mapArray(required(map, "resolutions", path), path + ".resolutions",
                            RulesetLibraryJsonCodec::decodeConflictResolution),
                    text(required(map, "canonicalHash", path), path + ".canonicalHash"));
        } catch (IllegalArgumentException exception) {
            throw format(path, exception.getMessage());
        }
    }

    private static Map<String, Object> encodeConflictResolution(RulesetConflictResolution value) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("field", encodeField(value.field()));
        map.put("winnerModuleHash", value.winnerModuleHash());
        return map;
    }

    private static RulesetConflictResolution decodeConflictResolution(Map<String, Object> map, String path) {
        try {
            return new RulesetConflictResolution(
                    decodeField(object(required(map, "field", path), path + ".field"), path + ".field"),
                    text(required(map, "winnerModuleHash", path), path + ".winnerModuleHash"));
        } catch (IllegalArgumentException exception) {
            throw format(path, exception.getMessage());
        }
    }

    private static Map<String, Object> encodeField(RuleFieldRef value) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("entityId", value.entityId());
        map.put("field", value.field().name());
        map.put("attributeKey", value.attributeKey());
        return map;
    }

    private static RuleFieldRef decodeField(Map<String, Object> map, String path) {
        try {
            return new RuleFieldRef(
                    text(required(map, "entityId", path), path + ".entityId"),
                    enumeration(RuleFieldRef.Field.class, required(map, "field", path), path + ".field"),
                    text(required(map, "attributeKey", path), path + ".attributeKey"));
        } catch (IllegalArgumentException exception) {
            throw format(path, exception.getMessage());
        }
    }

    private static Map<String, Object> encodeProject(RulesetProject value) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("id", value.id());
        map.put("name", value.name());
        map.put("description", value.description());
        map.put("baseCanonicalHash", value.baseCanonicalHash());
        map.put("revisionHashes", value.revisionHashes());
        map.put("defaultRevisionHash", value.defaultRevisionHash());
        map.put("archived", value.archived());
        return map;
    }

    private static RulesetProject decodeProject(Map<String, Object> map, String path) {
        try {
            return new RulesetProject(
                    text(required(map, "id", path), path + ".id"),
                    text(required(map, "name", path), path + ".name"),
                    text(required(map, "description", path), path + ".description"),
                    text(required(map, "baseCanonicalHash", path), path + ".baseCanonicalHash"),
                    stringList(required(map, "revisionHashes", path), path + ".revisionHashes"),
                    text(required(map, "defaultRevisionHash", path), path + ".defaultRevisionHash"),
                    bool(required(map, "archived", path), path + ".archived"));
        } catch (IllegalArgumentException exception) {
            throw format(path, exception.getMessage());
        }
    }

    private static Map<String, Object> encodeRevision(RulesetRevision value) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("projectId", value.projectId());
        map.put("revisionId", value.revisionId());
        map.put("version", value.version());
        map.put("name", value.name());
        map.put("description", value.description());
        map.put("origin", value.origin().name());
        map.put("baseCanonicalHash", value.baseCanonicalHash());
        map.put("runtime", encodeRuntime(value.runtime()));
        map.put("entities", value.entities().stream().map(RulesetLibraryJsonCodec::encodeEntity).toList());
        map.put("publishedAt", value.publishedAt());
        map.put("canonicalHash", value.canonicalHash());
        map.put("runtimeHash", value.runtimeHash());
        return map;
    }

    private static RulesetRevision decodeRevision(Map<String, Object> map, String path) {
        try {
            return new RulesetRevision(
                    text(required(map, "projectId", path), path + ".projectId"),
                    text(required(map, "revisionId", path), path + ".revisionId"),
                    text(required(map, "version", path), path + ".version"),
                    text(required(map, "name", path), path + ".name"),
                    text(required(map, "description", path), path + ".description"),
                    enumeration(RulesetOrigin.class, required(map, "origin", path), path + ".origin"),
                    text(required(map, "baseCanonicalHash", path), path + ".baseCanonicalHash"),
                    decodeRuntime(object(required(map, "runtime", path), path + ".runtime"), path + ".runtime"),
                    mapArray(required(map, "entities", path), path + ".entities", RulesetLibraryJsonCodec::decodeEntity),
                    text(required(map, "publishedAt", path), path + ".publishedAt"),
                    text(required(map, "canonicalHash", path), path + ".canonicalHash"),
                    text(required(map, "runtimeHash", path), path + ".runtimeHash"));
        } catch (IllegalArgumentException exception) {
            throw format(path, exception.getMessage());
        }
    }

    private static Map<String, Object> encodeDraft(RulesetDraft value) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("id", value.id());
        map.put("projectId", value.projectId());
        map.put("baseCanonicalHash", value.baseCanonicalHash());
        map.put("name", value.name());
        map.put("description", value.description());
        map.put("origin", value.origin().name());
        map.put("runtime", encodeRuntime(value.runtime()));
        map.put("patches", value.patches().stream().map(RulesetLibraryJsonCodec::encodePatch).toList());
        map.put("additions", value.additions().stream().map(RulesetLibraryJsonCodec::encodeEntity).toList());
        map.put("saveRevision", value.saveRevision());
        map.put("modifiedAt", value.modifiedAt());
        return map;
    }

    private static RulesetDraft decodeDraft(Map<String, Object> map, String path) {
        try {
            return new RulesetDraft(
                    text(required(map, "id", path), path + ".id"),
                    text(required(map, "projectId", path), path + ".projectId"),
                    text(required(map, "baseCanonicalHash", path), path + ".baseCanonicalHash"),
                    text(required(map, "name", path), path + ".name"),
                    text(required(map, "description", path), path + ".description"),
                    enumeration(RulesetOrigin.class, required(map, "origin", path), path + ".origin"),
                    decodeRuntime(object(required(map, "runtime", path), path + ".runtime"), path + ".runtime"),
                    mapArray(required(map, "patches", path), path + ".patches", RulesetLibraryJsonCodec::decodePatch),
                    mapArray(required(map, "additions", path), path + ".additions", RulesetLibraryJsonCodec::decodeEntity),
                    longInteger(required(map, "saveRevision", path), path + ".saveRevision"),
                    text(required(map, "modifiedAt", path), path + ".modifiedAt"));
        } catch (IllegalArgumentException exception) {
            throw format(path, exception.getMessage());
        }
    }

    private static Map<String, Object> encodePatch(RulePatch value) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("id", value.id());
        map.put("targetEntityId", value.targetEntityId());
        map.put("nameOverride", value.nameOverride() == null ? null : encodeLocalized(value.nameOverride()));
        map.put("descriptionOverride", value.descriptionOverride() == null ? null : encodeLocalized(value.descriptionOverride()));
        map.put("attributeOverrides", new LinkedHashMap<>(new TreeMap<>(value.attributeOverrides())));
        map.put("removedAttributes", value.removedAttributes().stream().sorted().toList());
        map.put("enabledOverride", value.enabledOverride());
        map.put("kindOverride", value.kindOverride() == null ? null : value.kindOverride().name());
        map.put("automationLevelOverride", value.automationLevelOverride() == null
                ? null : value.automationLevelOverride().name());
        map.put("tagsOverride", value.tagsOverride());
        return map;
    }

    private static RulePatch decodePatch(Map<String, Object> map, String path) {
        Object name = map.get("nameOverride");
        Object description = map.get("descriptionOverride");
        Object enabled = map.get("enabledOverride");
        Object kind = map.get("kindOverride");
        Object automation = map.get("automationLevelOverride");
        Object tags = map.get("tagsOverride");
        try {
            return new RulePatch(
                    text(required(map, "id", path), path + ".id"),
                    text(required(map, "targetEntityId", path), path + ".targetEntityId"),
                    name == null ? null : decodeLocalized(object(name, path + ".nameOverride"), path + ".nameOverride"),
                    description == null ? null : decodeLocalized(object(description, path + ".descriptionOverride"), path + ".descriptionOverride"),
                    stringMap(required(map, "attributeOverrides", path), path + ".attributeOverrides"),
                    new LinkedHashSet<>(stringList(required(map, "removedAttributes", path), path + ".removedAttributes")),
                    enabled == null ? null : bool(enabled, path + ".enabledOverride"),
                    kind == null ? null : enumeration(RuleKind.class, kind, path + ".kindOverride"),
                    automation == null ? null : enumeration(
                            RuleAutomationLevel.class, automation, path + ".automationLevelOverride"),
                    tags == null ? null : stringList(tags, path + ".tagsOverride"));
        } catch (IllegalArgumentException exception) {
            throw format(path, exception.getMessage());
        }
    }

    private static Map<String, Object> encodeEntity(RuleEntity value) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("id", value.id());
        map.put("kind", value.kind().name());
        map.put("origin", value.origin().name());
        map.put("name", encodeLocalized(value.name()));
        map.put("description", encodeLocalized(value.description()));
        map.put("derivedFrom", value.derivedFrom());
        map.put("enabled", value.enabled());
        map.put("automationLevel", value.automationLevel().name());
        map.put("attributes", new LinkedHashMap<>(new TreeMap<>(value.attributes())));
        map.put("tags", value.tags());
        map.put("source", value.source());
        map.put("license", value.license());
        map.put("sourcePage", value.sourcePage());
        return map;
    }

    private static RuleEntity decodeEntity(Map<String, Object> map, String path) {
        try {
            return new RuleEntity(
                    text(required(map, "id", path), path + ".id"),
                    enumeration(RuleKind.class, required(map, "kind", path), path + ".kind"),
                    enumeration(RulesetOrigin.class, required(map, "origin", path), path + ".origin"),
                    decodeLocalized(object(required(map, "name", path), path + ".name"), path + ".name"),
                    decodeLocalized(object(required(map, "description", path), path + ".description"), path + ".description"),
                    text(required(map, "derivedFrom", path), path + ".derivedFrom"),
                    bool(required(map, "enabled", path), path + ".enabled"),
                    enumeration(RuleAutomationLevel.class, required(map, "automationLevel", path), path + ".automationLevel"),
                    stringMap(required(map, "attributes", path), path + ".attributes"),
                    stringList(required(map, "tags", path), path + ".tags"),
                    text(required(map, "source", path), path + ".source"),
                    text(required(map, "license", path), path + ".license"),
                    integer(required(map, "sourcePage", path), path + ".sourcePage"));
        } catch (IllegalArgumentException exception) {
            throw format(path, exception.getMessage());
        }
    }

    private static Map<String, Object> encodeLocalized(LocalizedRuleText value) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("primaryLanguage", value.primaryLanguage());
        map.put("values", new LinkedHashMap<>(new TreeMap<>(value.values())));
        return map;
    }

    private static LocalizedRuleText decodeLocalized(Map<String, Object> map, String path) {
        return new LocalizedRuleText(
                stringMap(required(map, "values", path), path + ".values"),
                text(required(map, "primaryLanguage", path), path + ".primaryLanguage"));
    }

    private static Map<String, Object> encodeRuntime(RulesetRuntimeConfig value) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("semanticsVersion", value.semanticsVersion());
        map.put("criticalHitMinimumNatural", value.criticalHitMinimumNatural());
        map.put("naturalOneAlwaysMisses", value.naturalOneAlwaysMisses());
        map.put("maximumExhaustion", value.maximumExhaustion());
        map.put("exhaustionD20PenaltyPerLevel", value.exhaustionD20PenaltyPerLevel());
        map.put("exhaustionSpeedPenaltyFeetPerLevel", value.exhaustionSpeedPenaltyFeetPerLevel());
        map.put("proficiencyBonusBase", value.proficiencyBonusBase());
        map.put("proficiencyLevelsPerIncrease", value.proficiencyLevelsPerIncrease());
        map.put("proficiencyBonusMaximum", value.proficiencyBonusMaximum());
        return map;
    }

    private static RulesetRuntimeConfig decodeRuntime(Map<String, Object> map, String path) {
        return new RulesetRuntimeConfig(
                text(required(map, "semanticsVersion", path), path + ".semanticsVersion"),
                integer(required(map, "criticalHitMinimumNatural", path), path + ".criticalHitMinimumNatural"),
                bool(required(map, "naturalOneAlwaysMisses", path), path + ".naturalOneAlwaysMisses"),
                integer(required(map, "maximumExhaustion", path), path + ".maximumExhaustion"),
                integer(required(map, "exhaustionD20PenaltyPerLevel", path), path + ".exhaustionD20PenaltyPerLevel"),
                integer(required(map, "exhaustionSpeedPenaltyFeetPerLevel", path), path + ".exhaustionSpeedPenaltyFeetPerLevel"),
                integer(required(map, "proficiencyBonusBase", path), path + ".proficiencyBonusBase"),
                integer(required(map, "proficiencyLevelsPerIncrease", path), path + ".proficiencyLevelsPerIncrease"),
                integer(required(map, "proficiencyBonusMaximum", path), path + ".proficiencyBonusMaximum"));
    }

    private interface Decoder<T> {
        T decode(Map<String, Object> value, String path);
    }

    private static <T> List<T> mapArray(Object raw, String path, Decoder<T> decoder) {
        List<?> values = array(raw, path);
        ArrayList<T> result = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            String itemPath = path + '[' + index + ']';
            result.add(decoder.decode(object(values.get(index), itemPath), itemPath));
        }
        return List.copyOf(result);
    }

    private static Object required(Map<String, Object> map, String key, String path) {
        if (!map.containsKey(key)) throw format(path + '.' + key, "missing required value");
        return map.get(key);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object raw, String path) {
        if (!(raw instanceof Map<?, ?> value)) throw format(path, "expected object");
        for (Object key : value.keySet()) if (!(key instanceof String)) throw format(path, "object key is not text");
        return (Map<String, Object>) value;
    }

    private static List<?> array(Object raw, String path) {
        if (!(raw instanceof List<?> value)) throw format(path, "expected array");
        return value;
    }

    private static String text(Object raw, String path) {
        if (!(raw instanceof String value)) throw format(path, "expected text");
        return value;
    }

    private static boolean bool(Object raw, String path) {
        if (!(raw instanceof Boolean value)) throw format(path, "expected boolean");
        return value;
    }

    private static int integer(Object raw, String path) {
        long value = longInteger(raw, path);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) throw format(path, "integer out of range");
        return (int) value;
    }

    private static long longInteger(Object raw, String path) {
        if (raw instanceof Integer value) return value.longValue();
        if (raw instanceof Long value) return value;
        if (raw instanceof BigInteger value && value.bitLength() < 64) return value.longValue();
        throw format(path, "expected integer");
    }

    private static List<String> stringList(Object raw, String path) {
        List<?> values = array(raw, path);
        ArrayList<String> result = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) result.add(text(values.get(index), path + '[' + index + ']'));
        return List.copyOf(result);
    }

    private static Map<String, String> stringMap(Object raw, String path) {
        Map<String, Object> values = object(raw, path);
        TreeMap<String, String> result = new TreeMap<>();
        values.forEach((key, value) -> result.put(key, text(value, path + '.' + key)));
        return Map.copyOf(result);
    }

    private static <T extends Enum<T>> T enumeration(Class<T> type, Object raw, String path) {
        String value = text(raw, path);
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw format(path, "unknown " + type.getSimpleName() + " '" + value + "'");
        }
    }

    private static IllegalArgumentException format(String path, String detail) {
        return new IllegalArgumentException("Invalid ruleset document at " + path + ": " + detail);
    }
}
