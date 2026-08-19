package app.d6d.domain.catalog;

import app.d6d.domain.campaign.ActorKind;
import app.d6d.domain.campaign.ActorTemplate;
import app.d6d.domain.combat.ActorDefinition;

import java.math.BigDecimal;
import java.util.Objects;

/** Joins campaign metadata to the immutable combat definition without polluting either model. */
public record ActorCatalogEntry(
        ActorTemplate template,
        ActorDefinition combatDefinition,
        boolean activePartyMember,
        BigDecimal challengeRating,
        long xp) {

    public ActorCatalogEntry {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(combatDefinition, "combatDefinition");
        challengeRating = Objects.requireNonNull(challengeRating, "challengeRating").stripTrailingZeros();
        if (!template.id().equals(combatDefinition.id())) {
            throw new IllegalArgumentException("Campaign and combat definitions must share their stable id");
        }
        if (!template.name().equals(combatDefinition.name())) {
            throw new IllegalArgumentException("Campaign and combat definitions must share their display name");
        }
        if (challengeRating.signum() < 0 || xp < 0) {
            throw new IllegalArgumentException("Challenge Rating and XP cannot be negative");
        }
        if (template.kind() == ActorKind.PLAYER_CHARACTER && challengeRating.signum() != 0) {
            throw new IllegalArgumentException("Player characters do not use a Challenge Rating");
        }
        if (activePartyMember && template.kind() != ActorKind.PLAYER_CHARACTER) {
            throw new IllegalArgumentException("Only player characters can be active party members");
        }
    }

    public static ActorCatalogEntry character(ActorTemplate template, ActorDefinition definition, boolean active) {
        return new ActorCatalogEntry(template, definition, active, BigDecimal.ZERO, 0);
    }

    public static ActorCatalogEntry creature(
            ActorTemplate template, ActorDefinition definition, BigDecimal challengeRating, long xp) {
        return new ActorCatalogEntry(template, definition, false, challengeRating, xp);
    }

    public boolean isCharacter() {
        return template.kind() == ActorKind.PLAYER_CHARACTER;
    }
}
