package app.d6d.ui.content

import app.d6d.i18n.AppLanguage
import app.d6d.sheet.MonsterStatBlock

/**
 * Riporta nella lingua richiesta una creatura del bestiario incluso.
 *
 * Le creature incluse non si *traducono*: si **rigenerano**. Sono contenuto
 * nostro, scritto una volta in due lingue e identificato da uno slug stabile,
 * quindi la versione nell'altra lingua esiste gia' — riscriverla parola per
 * parola sarebbe piu' fragile e darebbe un risultato peggiore, perche' una
 * scheda delle statistiche e' fatta anche di tratti e azioni in prosa.
 *
 * Vale pero' la stessa regola che governa le schede: **si sostituisce solo cio'
 * di cui si conosce la provenienza.** Una creatura che l'utente ha modificato
 * non coincide piu' con quella canonica della propria lingua, e allora resta
 * sua, marcatore compreso. Meglio una creatura che non segue la lingua di una
 * creatura che perde le modifiche di chi gioca.
 *
 * Una creatura inventata da zero non e' nel bestiario e non viene toccata.
 */
internal fun MonsterStatBlock.regeneratedIn(target: AppLanguage): MonsterStatBlock {
    if (target == contentLanguage) return this
    val canonicalSource = TemplateBestiary.of(contentLanguage).all.firstOrNull { it.id == id }
        ?: return this
    if (this != canonicalSource) return this
    return TemplateBestiary.of(target).all.firstOrNull { it.id == id } ?: this
}
