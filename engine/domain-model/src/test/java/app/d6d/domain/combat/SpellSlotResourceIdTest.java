package app.d6d.domain.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpellSlotResourceIdTest {
    @Test
    void roundTripsStandardAndPactResourceIds() {
        SpellSlotResourceId standard = SpellSlotResourceId.standard(3);
        SpellSlotResourceId pact = SpellSlotResourceId.pact(5);

        assertEquals("app.d6d:spell-slot:3", standard.id());
        assertEquals("app.d6d:pact-slot:5", pact.id());
        assertEquals(standard, SpellSlotResourceId.parse(standard.id()).orElseThrow());
        assertEquals(pact, SpellSlotResourceId.parse(pact.id()).orElseThrow());
    }

    @Test
    void rejectsUnrelatedMalformedAndOutOfRangeIds() {
        assertTrue(SpellSlotResourceId.parse(null).isEmpty());
        assertTrue(SpellSlotResourceId.parse("other:slot:1").isEmpty());
        assertTrue(SpellSlotResourceId.parse(SpellSlotResourceId.STANDARD_PREFIX).isEmpty());
        assertTrue(SpellSlotResourceId.parse(SpellSlotResourceId.STANDARD_PREFIX + "0").isEmpty());
        assertTrue(SpellSlotResourceId.parse(SpellSlotResourceId.STANDARD_PREFIX + "+1").isEmpty());
        assertTrue(SpellSlotResourceId.parse(SpellSlotResourceId.STANDARD_PREFIX + "01").isEmpty());
        assertTrue(SpellSlotResourceId.parse(SpellSlotResourceId.PACT_PREFIX + "10").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> SpellSlotResourceId.standard(0));
        assertThrows(IllegalArgumentException.class, () -> SpellSlotResourceId.pact(10));
    }
}
