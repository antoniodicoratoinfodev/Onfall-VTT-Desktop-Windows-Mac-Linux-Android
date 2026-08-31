package app.d6d.rules.model;

import java.util.List;

/**
 * Fondazione read-only realmente vuota per regolamenti indipendenti dallo SRD.
 *
 * <p>È una base tecnica, non un gioco: non introduce dadi, caratteristiche,
 * classi, tipi di danno, condizioni o action economy. Un fork aggiunge soltanto
 * ciò che il suo autore dichiara esplicitamente.</p>
 */
public final class GenericRulesetFoundation {
    public static final String PROJECT_ID = "onfall:generic:blank";
    public static final String REVISION_ID = "onfall:generic:blank:1";

    private static final RulesetRevision REVISION = RulesetRevision.create(
            PROJECT_ID,
            REVISION_ID,
            "1",
            "Fondazione generica vuota / Blank generic foundation",
            "Base senza regole predefinite per creare un regolamento indipendente dallo SRD. "
                    + "/ A rules-free base for building a ruleset independently from the SRD.",
            RulesetOrigin.BUNDLED_STANDARD,
            "",
            RulesetRuntimeConfig.genericManual(),
            List.of(),
            "2026-08-31T00:00:00Z");

    private GenericRulesetFoundation() {
    }

    public static RulesetRevision revision() {
        return REVISION;
    }
}
