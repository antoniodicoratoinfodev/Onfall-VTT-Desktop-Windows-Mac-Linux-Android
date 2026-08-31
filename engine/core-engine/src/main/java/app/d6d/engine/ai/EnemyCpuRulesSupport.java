package app.d6d.engine.ai;

import app.d6d.rules.model.CompiledRuleset;
import app.d6d.rules.model.RulesetRevision;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Valutazione esplicita e conservativa dell'automazione tattica per una revisione. */
public record EnemyCpuRulesSupport(Mode mode, List<String> blockers) {

    public enum Mode { FULL_AUTOMATION, MANUAL_ONLY }

    public EnemyCpuRulesSupport {
        mode = Objects.requireNonNull(mode, "mode");
        blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers"));
        if (mode == Mode.FULL_AUTOMATION && !blockers.isEmpty()) {
            throw new IllegalArgumentException("Full CPU automation cannot have blockers");
        }
    }

    public boolean automated() {
        return mode == Mode.FULL_AUTOMATION;
    }

    public static EnemyCpuRulesSupport assess(RulesetRevision candidate, RulesetRevision supportedBaseline) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(supportedBaseline, "supportedBaseline");
        if (candidate.legacyCombatAutomationCompatibleWith(supportedBaseline)) {
            return new EnemyCpuRulesSupport(Mode.FULL_AUTOMATION, List.of());
        }
        ArrayList<String> blockers = new ArrayList<>();
        CompiledRuleset compiled = candidate.compile();
        compiled.movementModels().values().forEach(movement -> {
            if (movement.topology() != CompiledRuleset.BoardTopology.SQUARE) {
                blockers.add("UNSUPPORTED_TOPOLOGY:" + movement.topology());
            }
            if (movement.diagonalRule() != CompiledRuleset.DiagonalRule.UNIFORM) {
                blockers.add("UNSUPPORTED_DIAGONAL_RULE:" + movement.diagonalRule());
            }
        });
        blockers.add("RULESET_DIFFERS_FROM_CPU_BASELINE");
        return new EnemyCpuRulesSupport(Mode.MANUAL_ONLY, blockers.stream().distinct().toList());
    }
}
