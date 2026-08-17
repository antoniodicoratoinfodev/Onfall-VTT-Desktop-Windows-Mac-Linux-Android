package app.d6d.ui.i18n

/**
 * Il pochissimo vocabolario di gioco che non appartiene al motore.
 *
 * Quasi tutto il lessico — condizioni, tipi di danno, caratteristiche, taglie —
 * vive accanto agli enum che descrive, in `app.d6d.i18n`, perche' serve anche
 * agli strati sotto Compose. Qui restano solo le forme che esistono unicamente
 * per lo schermo: le iniziali dei gettoni del turno, che devono stare in un
 * cerchio di dodici pixel, e i nomi brevi che le accompagnano.
 *
 * Le iniziali cambiano con la lingua e non sono decorative: in italiano l'Azione
 * e' «A» e la Reazione «R», ma in un'altra lingua le due parole potrebbero
 * cominciare per la stessa lettera, e allora il gettone andrebbe ripensato. E'
 * una scelta di traduzione, quindi sta con le traduzioni.
 */
interface GlossaryStrings {
    val action: String
    val bonusAction: String
    val reaction: String
    val actionInitial: String
    val bonusActionInitial: String
    val reactionInitial: String
}

internal object GlossaryStringsIt : GlossaryStrings {
    override val action = "Azione"
    override val bonusAction = "Bonus"
    override val reaction = "Reazione"
    override val actionInitial = "A"
    override val bonusActionInitial = "B"
    override val reactionInitial = "R"
}

internal object GlossaryStringsEn : GlossaryStrings {
    override val action = "Action"
    override val bonusAction = "Bonus"
    override val reaction = "Reaction"
    override val actionInitial = "A"
    override val bonusActionInitial = "B"
    override val reactionInitial = "R"
}
