package app.d6d.ui.board

import app.d6d.board.Perception
import app.d6d.board.WallMask

/**
 * Chi, fra le creature dell'incontro, si è accorto del gruppo.
 *
 * Una creatura si accorge del gruppo in due modi, e bastano l'uno o l'altro:
 * qualcuno della squadra la vede, oppure è lei a vedere qualcuno della squadra.
 * I due versi non coincidono — un mostro con scurovisione in un corridoio buio
 * vede prima di essere visto, e una guardia cieca non vede mai nessuno — e
 * tenerli separati è ciò che rende l'agguato possibile in entrambe le direzioni.
 *
 * Chi è a terra non conta, né come occhio né come corpo notato: è la stessa
 * regola della nebbia dinamica, dove un personaggio privo di sensi smette di
 * illuminare. Con la squadra intera a terra nessuno si sveglia più da solo:
 * restano i colpi, che il motore gestisce per conto suo.
 */
fun awareOfParty(
    party: List<VisionViewer>,
    creatures: List<VisionViewer>,
    walls: WallMask,
): Set<String> {
    if (party.isEmpty() || creatures.isEmpty()) return emptySet()
    return creatures.asSequence()
        .filter { creature ->
            party.any { member ->
                Perception.sees(walls, member.placement, member.radiusSquares, creature.placement) ||
                    Perception.sees(walls, creature.placement, creature.radiusSquares, member.placement)
            }
        }
        .map { it.combatantId }
        .toSet()
}
