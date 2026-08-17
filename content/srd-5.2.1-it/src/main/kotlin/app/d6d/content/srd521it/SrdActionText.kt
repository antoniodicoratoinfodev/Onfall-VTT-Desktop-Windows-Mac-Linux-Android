package app.d6d.content.srd521it

/**
 * Le dodici azioni standard in inglese.
 *
 * Sono l'unica prosa dell'SRD che il pacchetto scrive a mano invece di leggerla
 * dai JSON: non appartengono a un capitolo estratto, ma alla tabella delle
 * azioni del capitolo di gioco. La chiave e' l'identificativo, che resta
 * italiano in entrambe le edizioni.
 */
internal class ActionText(val name: String, val description: String)

internal val ENGLISH_ACTIONS = mapOf(
    "attacco" to ActionText(
        "Attack",
        """
        When you take the Attack action, you can make one attack roll with a weapon or an
        Unarmed Strike.

        Certain features let you make more than one attack with this action: the Extra Attack
        feature is the most common of them. Each of those attacks is a separate attack roll.
        """.trimIndent(),
    ),
    "scatto" to ActionText(
        "Dash",
        """
        When you take the Dash action, you gain extra movement for the current turn equal to
        your Speed after applying any modifiers to it.

        With a Speed of 30 feet, for example, you can move up to 60 feet on your turn if you
        Dash.
        """.trimIndent(),
    ),
    "disimpegno" to ActionText(
        "Disengage",
        """
        If you take the Disengage action, your movement doesn't provoke Opportunity Attacks
        for the rest of the current turn.
        """.trimIndent(),
    ),
    "schivata" to ActionText(
        "Dodge",
        """
        If you take the Dodge action, you gain the following benefits until the start of your
        next turn: any attack roll made against you has Disadvantage if you can see the
        attacker, and you make Dexterity saving throws with Advantage.

        You lose these benefits if you have the Incapacitated condition or if your Speed is 0.
        """.trimIndent(),
    ),
    "aiutare" to ActionText(
        "Help",
        """
        When you take the Help action, you do one of the following.

        Assist an Ability Check. Choose one of your skill or tool proficiencies and one ally
        who is near enough for you to help. That ally has Advantage on the next ability check
        they make with the chosen proficiency.

        Assist an Attack Roll. Choose one ally within 5 feet of you and one enemy within 5
        feet of that ally. The next attack roll that the ally makes against that enemy before
        the start of your next turn has Advantage.
        """.trimIndent(),
    ),
    "nascondersi" to ActionText(
        "Hide",
        """
        With the Hide action, you try to conceal yourself. To do so, you must succeed on a DC
        15 Dexterity (Stealth) check while you're Heavily Obscured or behind Three-Quarters
        Cover or Total Cover, and you must be away from any enemy that can see you.

        On a success, you have the Invisible condition. That condition ends immediately after
        you make a sound louder than a whisper, an enemy finds you, you make an attack roll,
        or you cast a spell with a Verbal component.
        """.trimIndent(),
    ),
    "influenzare" to ActionText(
        "Influence",
        """
        With the Influence action, you urge a Monster to do something. Describe or roleplay
        how you're communicating with it, then choose Deception, Intimidation, Performance, or
        Persuasion — or another ability check if the situation warrants it.

        The DM decides whether the Monster does what you want. If it is unwilling but could be
        swayed, you make the ability check against a DC set by the DM. On a failed check, the
        Monster won't reconsider that request for the next 24 hours.
        """.trimIndent(),
    ),
    "magia" to ActionText(
        "Magic",
        """
        When you take the Magic action, you cast a spell that has a casting time of an action,
        or you use a feature or magic item that requires a Magic action to be activated.

        If you cast a spell with a casting time of 1 minute or longer, you must take the Magic
        action on each turn of that casting, and you must maintain Concentration while you do.
        """.trimIndent(),
    ),
    "prepararsi" to ActionText(
        "Ready",
        """
        You take the Ready action to wait for a particular circumstance before you act. To do
        so, take this action on your turn and decide both the trigger and what you will do in
        response to it: either a Reaction or moving up to your Speed.

        When the trigger occurs, you can either take your Reaction right after the trigger
        finishes or ignore it. Readying a spell requires it to have a casting time of an
        action, and holding on to its magic requires Concentration.
        """.trimIndent(),
    ),
    "cercare" to ActionText(
        "Search",
        """
        When you take the Search action, you make a Wisdom check to discern something that
        isn't obvious. The DM chooses the skill: Insight to detect a creature's state of mind,
        Medicine to determine a creature's ailment, Perception to sense something hidden, or
        Survival to follow tracks.
        """.trimIndent(),
    ),
    "studiare" to ActionText(
        "Study",
        """
        When you take the Study action, you make an Intelligence check to study your memories
        or a subject before you. The DM chooses the skill: Arcana, History, Investigation,
        Nature, or Religion, depending on what you are trying to recall or work out.
        """.trimIndent(),
    ),
    "utilizzare" to ActionText(
        "Utilize",
        """
        You take the Utilize action to use a nonmagical object in a way that requires an
        action, such as pulling a lever or drinking a potion.
        """.trimIndent(),
    ),
)
