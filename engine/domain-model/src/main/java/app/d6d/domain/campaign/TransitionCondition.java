package app.d6d.domain.campaign;

import java.util.Objects;

/** Serializable predicate attached to an edge in a session graph. */
public record TransitionCondition(
        TransitionConditionType type,
        String subject,
        ComparisonOperator operator,
        String expectedValue) {

    public TransitionCondition {
        type = Objects.requireNonNull(type, "type");
        subject = CampaignValues.optionalText(subject, "subject");
        operator = Objects.requireNonNull(operator, "operator");
        expectedValue = CampaignValues.optionalText(expectedValue, "expectedValue");
        if (type == TransitionConditionType.ALWAYS) {
            if (!subject.isEmpty() || operator != ComparisonOperator.NONE || !expectedValue.isEmpty()) {
                throw new IllegalArgumentException("ALWAYS cannot have predicate values");
            }
        } else if (operator == ComparisonOperator.NONE || expectedValue.isEmpty()) {
            throw new IllegalArgumentException("a conditional transition needs an operator and value");
        }
    }

    public static TransitionCondition always() {
        return new TransitionCondition(
                TransitionConditionType.ALWAYS, "", ComparisonOperator.NONE, "");
    }

    public static TransitionCondition onOutcome(EncounterOutcome outcome) {
        Objects.requireNonNull(outcome, "outcome");
        return new TransitionCondition(
                TransitionConditionType.ENCOUNTER_OUTCOME,
                "",
                ComparisonOperator.EQUALS,
                outcome.name());
    }

    public static TransitionCondition actorState(String actorReferenceId, String expectedState) {
        return new TransitionCondition(
                TransitionConditionType.ACTOR_STATE,
                CampaignValues.requireText(actorReferenceId, "actorReferenceId"),
                ComparisonOperator.EQUALS,
                CampaignValues.requireText(expectedState, "expectedState"));
    }

    public static TransitionCondition hasItem(String actorReferenceId, String itemId) {
        return new TransitionCondition(
                TransitionConditionType.HAS_ITEM,
                CampaignValues.requireText(actorReferenceId, "actorReferenceId"),
                ComparisonOperator.EQUALS,
                CampaignValues.requireText(itemId, "itemId"));
    }

    public static TransitionCondition checkResult(
            String checkId, ComparisonOperator operator, int target) {
        return new TransitionCondition(
                TransitionConditionType.CHECK_RESULT,
                CampaignValues.requireText(checkId, "checkId"),
                operator,
                Integer.toString(target));
    }

    public static TransitionCondition variable(
            String variableName, ComparisonOperator operator, String expectedValue) {
        return new TransitionCondition(
                TransitionConditionType.VARIABLE,
                CampaignValues.requireText(variableName, "variableName"),
                operator,
                CampaignValues.requireText(expectedValue, "expectedValue"));
    }
}
