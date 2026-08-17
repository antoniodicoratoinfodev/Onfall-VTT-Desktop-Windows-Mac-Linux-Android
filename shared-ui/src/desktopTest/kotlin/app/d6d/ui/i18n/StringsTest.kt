package app.d6d.ui.i18n

import app.d6d.i18n.AppLanguage
import app.d6d.sheet.CreatureSize
import app.d6d.sheet.MonsterSpeeds
import app.d6d.sheet.MonsterStatBlock
import app.d6d.sheet.i18n.distanceLabel
import app.d6d.sheet.i18n.feetFromDistance
import app.d6d.sheet.i18n.label
import app.d6d.sheet.i18n.subtitle
import app.d6d.sheet.i18n.withLocalizedDistances
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

/**
 * Il vocabolario, provato dove il compilatore non arriva.
 *
 * La completezza non ha bisogno di test: [Strings] e' un'interfaccia, e una voce
 * mancante in una delle due lingue non compila. Restano tre cose che invece un
 * compilatore non puo' vedere — una traduzione vuota, una lasciata identica
 * all'altra lingua per distrazione, e la lingua che non arriva davvero fino al
 * testo — ed e' di quelle che si occupa questo file.
 */
class StringsTest {

    /**
     * Ogni voce di ogni fascicolo, nelle due lingue, sotto forma di coppie
     * `nome → valore`. Passa per la riflessione perche' l'alternativa sarebbe
     * elencare a mano centinaia di proprieta', cioe' riscrivere il difetto che il
     * test dovrebbe trovare.
     */
    private fun entries(strings: Strings): Map<String, String> = buildMap {
        val bundles = Strings::class.declaredMemberProperties
            .filter { it.name != "language" }
            .map { it.name to it.get(strings) }

        bundles.forEach { (bundleName, bundle) ->
            if (bundle == null) return@forEach
            bundle::class.declaredMemberProperties
                .filter { it.returnType.classifier == String::class }
                .forEach { property ->
                    property.isAccessible = true
                    @Suppress("UNCHECKED_CAST")
                    val value = (property as kotlin.reflect.KProperty1<Any, String>).get(bundle)
                    put("$bundleName.${property.name}", value)
                }
        }
    }

    @Test
    fun `nessuna voce e' vuota, in nessuna delle due lingue`() {
        AppLanguage.entries.forEach { language ->
            entries(stringsFor(language)).forEach { (key, value) ->
                assertTrue(
                    value.isNotBlank(),
                    "«$key» è vuota in ${language.endonym}",
                )
            }
        }
    }

    /**
     * Molte voci coincidono per forza — «Bonus», «AC», «Metamagic» si scrivono
     * uguali — quindi non si puo' pretendere che siano tutte diverse. Si pretende
     * pero' che la maggior parte lo sia: se una traduzione venisse copiata di
     * peso, questa soglia crollerebbe subito.
     */
    @Test
    fun `le due lingue dicono cose diverse`() {
        val italian = entries(ItalianStrings)
        val english = entries(EnglishStrings)
        assertEquals(italian.keys, english.keys, "i due vocabolari non hanno le stesse voci")

        val different = italian.count { (key, value) -> english[key] != value }
        val ratio = different.toDouble() / italian.size
        assertTrue(
            ratio > 0.75,
            "solo il ${(ratio * 100).toInt()}% delle voci differisce fra italiano e inglese: " +
                "sembra una traduzione copiata",
        )
    }

    @Test
    fun `la lingua scelta arriva fino al testo`() {
        AppLocale.use(AppLanguage.ENGLISH)
        assertSame(EnglishStrings, AppLocale.current)
        assertEquals("Settings", AppLocale.current.nav.settings)

        AppLocale.use(AppLanguage.ITALIAN)
        assertSame(ItalianStrings, AppLocale.current)
        assertEquals("Impostazioni", AppLocale.current.nav.settings)
    }

    @Test
    fun `un testo localizzato segue la lingua invece di fissarsi`() {
        val text = LocalizedText { it.battle.noValidTarget }

        assertEquals("Nessun bersaglio valido.", text.resolve(ItalianStrings))
        assertEquals("No valid target.", text.resolve(EnglishStrings))
    }

    @Test
    fun `un testo letterale resta com'e' scritto`() {
        val text = literalText("Grix il Verde")

        assertEquals("Grix il Verde", text.resolve(ItalianStrings))
        assertEquals("Grix il Verde", text.resolve(EnglishStrings))
    }

    @Test
    fun `i dettagli degli errori sessione seguono la lingua`() {
        assertEquals(
            "Unsupported archive version: 7",
            localizedSessionError("Versione dell'archivio non supportata: 7", AppLanguage.ENGLISH),
        )
        assertEquals(
            "The saved session contains no combat.",
            localizedSessionError(
                "La sessione salvata non contiene un combattimento",
                AppLanguage.ENGLISH,
            ),
        )
        assertEquals(
            "JSON della sessione di combattimento non valido in $.round: era atteso un numero intero",
            localizedSessionError(
                "Invalid combat session JSON at $.round: expected an integer",
                AppLanguage.ITALIAN,
            ),
        )
        assertEquals(
            "JSON non valido alla riga 3, colonna 8 (offset 19).",
            localizedSessionError(
                "Expected a quoted object key at line 3, column 8 (offset 19)",
                AppLanguage.ITALIAN,
            ),
        )
    }

    @Test
    fun `la lingua di sistema decide solo finche' nessuno ha scelto`() {
        assertEquals(AppLanguage.ITALIAN, AppLanguage.fromLocale(Locale.ITALY))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLocale(Locale.US))
        // Una lingua che non parliamo ricade sull'inglese, non sull'italiano.
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLocale(Locale.JAPAN))

        assertEquals(AppLanguage.ENGLISH, AppLanguage.parseOrSystemDefault("ENGLISH"))
        assertEquals(AppLanguage.ITALIAN, AppLanguage.parseOrSystemDefault("it"))
        // Un valore vuoto o incomprensibile non e' una scelta: torna al sistema.
        assertEquals(AppLanguage.systemDefault(), AppLanguage.parseOrSystemDefault(""))
        assertEquals(AppLanguage.systemDefault(), AppLanguage.parseOrSystemDefault("klingon"))
    }

    /**
     * Le distanze non si traducono: si convertono.
     *
     * E' la differenza fra un'app tradotta e una localizzata. Un tavolo inglese
     * legge piedi su ogni manuale che ha in mano, e trovarsi metri a schermo lo
     * costringerebbe a fare i conti a mente a ogni gittata.
     */
    @Test
    fun `le distanze cambiano unita' con la lingua`() {
        assertEquals("1,5 m", distanceLabel(5, AppLanguage.ITALIAN))
        assertEquals("5 ft", distanceLabel(5, AppLanguage.ENGLISH))
        assertEquals("9 m", distanceLabel(30, AppLanguage.ITALIAN))
        assertEquals("30 ft", distanceLabel(30, AppLanguage.ENGLISH))

        // Andata e ritorno esatti su tutte le misure che il regolamento nomina.
        listOf(5, 10, 15, 30, 60, 120).forEach { feet ->
            val italian = distanceLabel(feet, AppLanguage.ITALIAN).removeSuffix(" m")
            assertEquals(
                feet,
                feetFromDistance(italian.replace(',', '.').toDouble(), AppLanguage.ITALIAN),
                "$feet piedi non sopravvivono al giro in metri",
            )
        }
    }

    @Test
    fun `velocita e sottotitolo dello stat block seguono la lingua`() {
        val speeds = MonsterSpeeds(walk = 30, fly = 60, hover = true, swim = 20)
        assertEquals("9 m, Volo 18 m (fluttua), Nuoto 6 m", speeds.label(AppLanguage.ITALIAN))
        assertEquals("30 ft, Fly 60 ft (hover), Swim 20 ft", speeds.label(AppLanguage.ENGLISH))

        val monster = MonsterStatBlock(
            size = CreatureSize.MEDIUM,
            type = "Beast",
            tags = "shapechanger",
            alignment = "Unaligned",
        )
        assertEquals(
            "Media Beast (shapechanger), Unaligned",
            monster.subtitle(AppLanguage.ITALIAN),
        )
        assertEquals(
            "Medium Beast (shapechanger), Unaligned",
            monster.subtitle(AppLanguage.ENGLISH),
        )
    }

    @Test
    fun `le distanze dentro un testo di regole seguono la lingua di chi legge`() {
        val italianSource = "Gittata 30/120 piedi, area di 20 piedi."
        val englishSource = "Range 30/120 ft, 20 feet across."

        assertEquals(
            "Gittata 9/36 m, area di 6 m.",
            italianSource.withLocalizedDistances(AppLanguage.ITALIAN),
        )
        // Un testo scritto in inglese, letto da chi gioca in italiano.
        assertEquals(
            "Range 9/36 m, 6 m across.",
            englishSource.withLocalizedDistances(AppLanguage.ITALIAN),
        )
        assertEquals(
            "Gittata 30/120 ft, area di 20 ft.",
            italianSource.withLocalizedDistances(AppLanguage.ENGLISH),
        )
    }

    @Test
    fun `i messaggi del motore hanno una forma per ciascuna lingua`() {
        val refusal = "A dead combatant cannot be healed"

        val italian = app.d6d.ui.state.translateRuleMessage(refusal, AppLanguage.ITALIAN)
        val english = app.d6d.ui.state.translateRuleMessage(refusal, AppLanguage.ENGLISH)

        assertNotNull(italian)
        assertFalse(italian == refusal, "il rifiuto è rimasto in inglese tecnico")
        assertEquals("Un combattente morto non può essere curato.", italian)
        // Anche l'inglese passa dalla tabella: il motore scrive righe da log, non
        // frasi da mostrare al tavolo.
        assertEquals("A dead combatant cannot be healed.", english)
    }

    @Test
    fun `un numero fuori scala nel testo libero non fa fallire il disegno`() {
        // La descrizione di una capacita' personalizzata e' testo libero: puo'
        // contenere un numero piu' grande di quanto un Int contenga. Convertirlo
        // non ha senso, ma farlo esplodere ne ha ancora meno — la scheda smette
        // di disegnarsi.
        val enorme = "raggio di 99999999999999999999 feet"
        assertEquals(enorme, enorme.withLocalizedDistances(AppLanguage.ITALIAN))

        val misto = "gittata 30/99999999999999999999 feet"
        assertEquals(misto, misto.withLocalizedDistances(AppLanguage.ITALIAN))

        // E cio' che e' rappresentabile continua a convertirsi.
        assertEquals("gittata 9/36 m", "gittata 30/120 feet".withLocalizedDistances(AppLanguage.ITALIAN))
    }
}
