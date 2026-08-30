package app.d6d.rules.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleScopeTest {
    @Test
    void scopeIdentityIsTypedStableAndSortable() {
        RuleScope actor = RuleScope.actor(" hero ");
        RuleScope object = RuleScope.objectScope("relic:moon");

        assertEquals("hero", actor.id());
        assertEquals("actor:hero", actor.canonicalKey());
        assertEquals(List.of(RuleScope.session(), actor, object),
                java.util.stream.Stream.of(object, actor, RuleScope.session()).sorted().toList());
        assertTrue(RuleScope.session().isSession());
    }

    @Test
    void invalidOrAmbiguousScopeIdsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new RuleScope(RuleScope.Kind.SESSION, "another"));
        assertThrows(IllegalArgumentException.class, () -> RuleScope.actor("   "));
        assertThrows(IllegalArgumentException.class, () -> RuleScope.scene("scene\nother"));
    }
}
