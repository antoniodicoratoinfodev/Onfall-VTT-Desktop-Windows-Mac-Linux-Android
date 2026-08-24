package app.d6d.domain.combat;

import app.d6d.domain.space.BattleMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;

/** Complete persistence/UI snapshot of an encounter, including deterministic RNG state. */
public record CombatState(
        String encounterId,
        String rulesetVersion,
        String contentVersion,
        CombatStatus status,
        long revision,
        long randomSeed,
        long randomState,
        List<String> rosterOrder,
        Map<String, CombatantState> combatants,
        Map<String, Integer> initiativeScores,
        List<String> initiativeOrder,
        int round,
        int turnIndex,
        Map<String, TurnBudget> turnBudgets,
        Set<String> partyCombatantIds,
        /**
         * Quando e' attivo, i combattenti che hanno pareggiato l'iniziativa agiscono
         * nello stesso turno anziche' uno dopo l'altro. Resta una scelta del tavolo:
         * il regolamento fa risolvere le parita' al DM, non le rende simultanee da se'.
         */
        boolean simultaneousTies,

        /**
         * Mappa tattica, facoltativa. Quando non e' configurata l'incontro resta
         * astratto e nulla cambia rispetto al gioco senza mappa.
         */
        BattleMap battleMap,

        /** Raggio in piedi entro cui una creatura appena sveglia allerta il proprio schieramento. */
        int alarmRadiusFeet,

        /**
         * Chi non si e' ancora accorto del gruppo.
         *
         * <p>Una creatura inattiva sta nell'ordine d'iniziativa ma non agisce: la
         * CPU la salta, e il suo turno passa senza che si muova o attacchi. Non e'
         * una condizione del regolamento e non concede vantaggi a chi la attacca:
         * e' la distinzione fra il mostro che sta ancora facendo la guardia in
         * fondo al corridoio e quello che ha visto arrivare qualcuno.</p>
         *
         * <p>Vuoto e' il valore normale, ed e' anche quello dei salvataggi nati
         * prima che l'attivazione esistesse: senza informazioni sulla vista, tutti
         * agiscono. Il risveglio e' definitivo, perche' una creatura che ha visto
         * il gruppo non torna a non saperlo.</p>
         */
        Set<String> dormantCombatantIds) {

    /** Valore dei salvataggi creati prima che il raggio d'allarme fosse persistito. */
    public static final int DEFAULT_ALARM_RADIUS_FEET = 60;

    public CombatState {
        encounterId = requireText(encounterId, "encounterId");
        rulesetVersion = requireText(rulesetVersion, "rulesetVersion");
        contentVersion = requireText(contentVersion, "contentVersion");
        Objects.requireNonNull(status, "status");
        if (revision < 0 || round < 0 || turnIndex < -1) {
            throw new IllegalArgumentException("Invalid encounter counters");
        }
        if (alarmRadiusFeet < 0) throw new IllegalArgumentException("Alarm radius cannot be negative");
        rosterOrder = List.copyOf(Objects.requireNonNull(rosterOrder, "rosterOrder"));
        combatants = immutableLinkedMap(combatants);
        initiativeScores = immutableLinkedMap(initiativeScores);
        initiativeOrder = List.copyOf(Objects.requireNonNull(initiativeOrder, "initiativeOrder"));
        turnBudgets = immutableLinkedMap(turnBudgets);
        partyCombatantIds = Set.copyOf(Objects.requireNonNull(partyCombatantIds, "partyCombatantIds"));
        battleMap = battleMap == null ? BattleMap.none() : battleMap;
        dormantCombatantIds = dormantCombatantIds == null ? Set.of() : Set.copyOf(dormantCombatantIds);
        if (!combatants.keySet().containsAll(rosterOrder) || !combatants.keySet().containsAll(initiativeOrder)
                || !combatants.keySet().containsAll(initiativeScores.keySet())
                || !combatants.keySet().containsAll(turnBudgets.keySet())
                || !combatants.keySet().containsAll(partyCombatantIds)
                || !combatants.keySet().containsAll(dormantCombatantIds)) {
            throw new IllegalArgumentException("State references an unknown combatant");
        }
        if (new HashSet<>(rosterOrder).size() != rosterOrder.size()) {
            throw new IllegalArgumentException("Roster order contains duplicate combatants");
        }
        if (rosterOrder.size() != combatants.size() || !rosterOrder.containsAll(combatants.keySet())) {
            throw new IllegalArgumentException("Roster order must contain every combatant exactly once");
        }
        if (!turnBudgets.keySet().equals(combatants.keySet())) {
            throw new IllegalArgumentException("Every combatant needs a turn budget");
        }
        Set<String> conditionIds = new HashSet<>();
        for (Map.Entry<String, CombatantState> entry : combatants.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().snapshot().instanceId())) {
                throw new IllegalArgumentException("Combatant map key and snapshot instance id differ");
            }
            for (ConditionInstance condition : entry.getValue().conditions()) {
                if (!combatants.containsKey(condition.sourceCombatantId())) {
                    throw new IllegalArgumentException("Condition references an unknown source combatant");
                }
                if (!conditionIds.add(condition.id())) {
                    throw new IllegalArgumentException("Duplicate condition instance id: " + condition.id());
                }
                if (condition.duration().expiry() == ConditionExpiry.CONCENTRATION
                        && !combatants.containsKey(condition.concentrationOwnerId())) {
                    throw new IllegalArgumentException("Condition references an unknown concentration owner");
                }
                if (condition.duration().expiry() == ConditionExpiry.CONCENTRATION
                        && combatants.get(condition.concentrationOwnerId()).concentration() == null) {
                    throw new IllegalArgumentException("Condition owner is not concentrating");
                }
            }
        }
        if (!initiativeScores.keySet().containsAll(initiativeOrder)) {
            throw new IllegalArgumentException("Initiative order contains a combatant without a score");
        }
        if (initiativeOrder.isEmpty() && turnIndex != -1) {
            throw new IllegalArgumentException("turnIndex requires an initiative order");
        }
        if (!initiativeOrder.isEmpty() && turnIndex >= initiativeOrder.size()) {
            throw new IllegalArgumentException("turnIndex is outside initiative order");
        }
        boolean liveOrResolved = status == CombatStatus.ACTIVE || status == CombatStatus.PAUSED
                || status == CombatStatus.RESOLVED;
        if (liveOrResolved && (round < 1 || turnIndex < 0
                || initiativeOrder.size() != combatants.size()
                || !initiativeOrder.containsAll(combatants.keySet())
                || !initiativeScores.keySet().containsAll(combatants.keySet()))) {
            throw new IllegalArgumentException("A started encounter needs a complete initiative state");
        }
        if (!liveOrResolved && (round != 0 || turnIndex != -1)) {
            throw new IllegalArgumentException("An encounter that has not started cannot have a current turn");
        }
    }

    /**
     * Costruttore di compatibilita' per chi non usa i turni simultanei ne' la mappa.
     *
     * <p>Senza la bandiera ogni combattente forma un gruppo da solo, quindi
     * l'indice di turno mantiene esattamente il significato precedente; senza mappa
     * l'incontro resta astratto.</p>
     */
    public CombatState(
            String encounterId,
            String rulesetVersion,
            String contentVersion,
            CombatStatus status,
            long revision,
            long randomSeed,
            long randomState,
            List<String> rosterOrder,
            Map<String, CombatantState> combatants,
            Map<String, Integer> initiativeScores,
            List<String> initiativeOrder,
            int round,
            int turnIndex,
            Map<String, TurnBudget> turnBudgets,
            Set<String> partyCombatantIds) {
        this(encounterId, rulesetVersion, contentVersion, status, revision, randomSeed, randomState,
                rosterOrder, combatants, initiativeScores, initiativeOrder, round, turnIndex, turnBudgets,
                partyCombatantIds, false, BattleMap.none());
    }

    /** Costruttore di compatibilita' per chi usa i turni simultanei ma non la mappa. */
    public CombatState(
            String encounterId,
            String rulesetVersion,
            String contentVersion,
            CombatStatus status,
            long revision,
            long randomSeed,
            long randomState,
            List<String> rosterOrder,
            Map<String, CombatantState> combatants,
            Map<String, Integer> initiativeScores,
            List<String> initiativeOrder,
            int round,
            int turnIndex,
            Map<String, TurnBudget> turnBudgets,
            Set<String> partyCombatantIds,
            boolean simultaneousTies) {
        this(encounterId, rulesetVersion, contentVersion, status, revision, randomSeed, randomState,
                rosterOrder, combatants, initiativeScores, initiativeOrder, round, turnIndex, turnBudgets,
                partyCombatantIds, simultaneousTies, BattleMap.none(), DEFAULT_ALARM_RADIUS_FEET, Set.of());
    }

    /**
     * Costruttore di compatibilita' per chi non conosce l'attivazione.
     *
     * <p>Senza l'insieme delle creature inattive nessuno lo e', che e' il solo
     * comportamento possibile prima che la vista entrasse nel modello.</p>
     */
    public CombatState(
            String encounterId,
            String rulesetVersion,
            String contentVersion,
            CombatStatus status,
            long revision,
            long randomSeed,
            long randomState,
            List<String> rosterOrder,
            Map<String, CombatantState> combatants,
            Map<String, Integer> initiativeScores,
            List<String> initiativeOrder,
            int round,
            int turnIndex,
            Map<String, TurnBudget> turnBudgets,
            Set<String> partyCombatantIds,
            boolean simultaneousTies,
            BattleMap battleMap) {
        this(encounterId, rulesetVersion, contentVersion, status, revision, randomSeed, randomState,
                rosterOrder, combatants, initiativeScores, initiativeOrder, round, turnIndex, turnBudgets,
                partyCombatantIds, simultaneousTies, battleMap, DEFAULT_ALARM_RADIUS_FEET, Set.of());
    }

    /** Costruttore di compatibilita' per lo stato con dormienza ma senza raggio persistito. */
    public CombatState(
            String encounterId,
            String rulesetVersion,
            String contentVersion,
            CombatStatus status,
            long revision,
            long randomSeed,
            long randomState,
            List<String> rosterOrder,
            Map<String, CombatantState> combatants,
            Map<String, Integer> initiativeScores,
            List<String> initiativeOrder,
            int round,
            int turnIndex,
            Map<String, TurnBudget> turnBudgets,
            Set<String> partyCombatantIds,
            boolean simultaneousTies,
            BattleMap battleMap,
            Set<String> dormantCombatantIds) {
        this(encounterId, rulesetVersion, contentVersion, status, revision, randomSeed, randomState,
                rosterOrder, combatants, initiativeScores, initiativeOrder, round, turnIndex, turnBudgets,
                partyCombatantIds, simultaneousTies, battleMap, DEFAULT_ALARM_RADIUS_FEET, dormantCombatantIds);
    }

    /** Vero se la creatura e' nell'incontro ma non si e' ancora accorta del gruppo. */
    public boolean dormant(String combatantId) {
        return dormantCombatantIds.contains(combatantId);
    }

    /**
     * Distanza in piedi fra due combattenti.
     *
     * <p>Vuota senza mappa o senza entrambe le posizioni: e' la distinzione che il
     * documento impone fra simulazione astratta e simulazione tattica esatta.</p>
     */
    public Optional<Integer> distanceFeet(String first, String second) {
        return battleMap.distanceFeet(first, second);
    }

    /**
     * L'ordine dei turni raggruppato.
     *
     * <p>Con i turni simultanei disattivati ogni combattente e' un gruppo da solo.
     * Con i turni simultanei attivi, combattenti consecutivi nell'ordine d'iniziativa
     * che condividono lo stesso punteggio formano un unico gruppo e giocano insieme.</p>
     */
    public List<List<String>> turnGroups() {
        if (!simultaneousTies) {
            return initiativeOrder.stream().map(List::of).toList();
        }
        List<List<String>> groups = new ArrayList<>();
        List<String> current = new ArrayList<>();
        Integer currentScore = null;
        for (String id : initiativeOrder) {
            Integer score = initiativeScores.get(id);
            if (!current.isEmpty() && Objects.equals(score, currentScore)) {
                current.add(id);
            } else {
                if (!current.isEmpty()) groups.add(List.copyOf(current));
                current = new ArrayList<>();
                current.add(id);
                currentScore = score;
            }
        }
        if (!current.isEmpty()) groups.add(List.copyOf(current));
        return List.copyOf(groups);
    }

    /**
     * I combattenti che possono giocare il turno corrente: uno solo, o piu' se in
     * parita'. I membri a 0 PF restano nel gruppo strutturale di iniziativa, ma non
     * sono attori del turno: in questo modo {@code turnIndex} resta stabile e
     * persistibile mentre l'interfaccia puo' comunque mostrarli come saltati.
     */
    public List<String> currentCombatantIds() {
        List<List<String>> groups = turnGroups();
        if (turnIndex < 0 || turnIndex >= groups.size()) return List.of();
        return groups.get(turnIndex).stream()
                .filter(id -> !combatants.get(id).defeated() && !combatants.get(id).dead())
                .toList();
    }

    /** Il primo combattente del turno corrente, per i chiamanti che ne attendono uno solo. */
    public Optional<String> currentCombatantId() {
        List<String> active = currentCombatantIds();
        return active.isEmpty() ? Optional.empty() : Optional.of(active.get(0));
    }

    /** Vero quando il turno corrente e' condiviso da piu' combattenti in parita'. */
    public boolean currentTurnIsSimultaneous() {
        return currentCombatantIds().size() > 1;
    }

    public CombatantState combatant(String combatantId) {
        CombatantState result = combatants.get(combatantId);
        if (result == null) throw new IllegalArgumentException("Unknown combatant: " + combatantId);
        return result;
    }

    public List<InitiativeEntry> initiativeEntries() {
        List<InitiativeEntry> entries = new ArrayList<>();
        for (String id : initiativeOrder) {
            entries.add(new InitiativeEntry(id, initiativeScores.get(id), rosterOrder.indexOf(id)));
        }
        return List.copyOf(entries);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return value;
    }

    private static <K, V> Map<K, V> immutableLinkedMap(Map<K, V> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(source, "source")));
    }
}
