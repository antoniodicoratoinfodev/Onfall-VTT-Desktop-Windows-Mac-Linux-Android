package app.d6d.engine.ai;

import app.d6d.rules.model.LocalizedRuleText;
import app.d6d.rules.model.RuleAutomationLevel;
import app.d6d.rules.model.RuleEntity;
import app.d6d.rules.model.RuleKind;
import app.d6d.rules.model.RulesetOrigin;
import app.d6d.rules.model.RulesetRevision;
import app.d6d.rules.model.RulesetRuntimeConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnemyCpuRulesSupportTest {

    @Test
    void exactBaselineIsAutomatedWhileDifferentHexRulesAreExplicitlyManual() {
        RulesetRevision baseline = revision("base", List.of());
        RuleEntity movement = new RuleEntity("move", RuleKind.MOVEMENT, RulesetOrigin.HOMEBREW,
                LocalizedRuleText.bilingual("Movimento", "Movement"),
                LocalizedRuleText.bilingual("Test", "Test"), "", true, RuleAutomationLevel.FULL,
                Map.of("topology", "HEX_POINTY", "diagonalRule", "UNIFORM", "unitsPerCell", "1"),
                List.of(), "Test", "", 0);
        RulesetRevision changed = revision("changed", List.of(movement));

        assertTrue(EnemyCpuRulesSupport.assess(baseline, baseline).automated());
        EnemyCpuRulesSupport support = EnemyCpuRulesSupport.assess(changed, baseline);
        assertFalse(support.automated());
        assertEquals(EnemyCpuRulesSupport.Mode.MANUAL_ONLY, support.mode());
        assertTrue(support.blockers().contains("UNSUPPORTED_TOPOLOGY:HEX_POINTY"));
    }

    private static RulesetRevision revision(String id, List<RuleEntity> entities) {
        return RulesetRevision.create(id, id + ":revision:1", "1", id, "",
                RulesetOrigin.HOMEBREW, "", RulesetRuntimeConfig.genericManual(), entities, "");
    }
}
