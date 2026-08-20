package app.d6d.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.d6d.i18n.AppLanguage
import app.d6d.board.StampKind
import app.d6d.board.TemplateShape
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Preferenze vive, condivise da tutta la shell.
 *
 * Gemello di [app.d6d.ui.layout.UiLayoutState] per le scelte che non riguardano la
 * disposizione: si parte dal file, si scrive qui, e il file si riallinea poco dopo.
 * Chi legge una singola proprieta' viene ridisegnato solo quando quella cambia.
 */
class AppPreferencesState(
    initial: AppPreferences = AppPreferences(),
    private val store: PreferencesStore? = null,
) {
    var enemyCpuSpeed by mutableStateOf(initial.speedOrDefault())
    var animatedBackdrop by mutableStateOf(initial.animatedBackdrop)
    var boardColorArgb by mutableStateOf(initial.boardColorArgb)
    var boardStrokeWidth by mutableStateOf(initial.boardStrokeWidth)
    var boardTemplateShape by mutableStateOf(initial.templateShapeOrDefault())
    var boardStampKind by mutableStateOf(initial.stampKindOrDefault())

    /**
     * La lingua dell'interfaccia.
     *
     * Chi la cambia qui non deve preoccuparsi di avvisare nessuno: la radice
     * dell'applicazione osserva questo valore e allinea [app.d6d.ui.i18n.AppLocale],
     * che e' cio' che il testo fuori da Compose legge.
     */
    var language by mutableStateOf(initial.languageOrSystemDefault())

    /**
     * Vero finche' la lingua e' quella dedotta dal sistema e non una scelta.
     *
     * Serve a non scrivere nel file una preferenza che nessuno ha espresso: chi
     * porta i propri dati su una macchina configurata in un'altra lingua deve
     * ritrovare quella macchina, non quella di prima. Al primo cambio esplicito
     * la distinzione decade per sempre.
     */
    private var languageIsSystemDefault by mutableStateOf(initial.language.isBlank())

    private var lastSaved = initial.sanitized()

    /**
     * Fotografia serializzabile delle preferenze attuali.
     *
     * La lingua si legge **sempre**, anche quando non finisce nel file. La radice
     * dell'applicazione salva reagendo a `snapshotFlow { snapshot() }`, e un flusso
     * del genere si iscrive soltanto agli stati che il blocco legge davvero: con la
     * lettura dentro il ramo `else` di un `if`, finche' valeva il predefinito di
     * sistema nessuno osservava la lingua, e sceglierne una cambiava lo schermo
     * senza mai arrivare al disco.
     */
    fun snapshot(): AppPreferences {
        val chosen = language.name
        return AppPreferences(
            enemyCpuSpeed = enemyCpuSpeed.name,
            animatedBackdrop = animatedBackdrop,
            language = if (languageIsSystemDefault) "" else chosen,
            boardColorArgb = boardColorArgb,
            boardStrokeWidth = boardStrokeWidth,
            boardTemplateShape = boardTemplateShape.name,
            boardStampKind = boardStampKind.name,
        ).sanitized()
    }

    /** Registra una scelta esplicita: da qui in poi la lingua si salva. */
    fun chooseLanguage(value: AppLanguage) {
        language = value
        languageIsSystemDefault = false
    }

    /** Scrive su disco solo se qualcosa e' davvero cambiato dall'ultimo salvataggio. */
    fun persist() {
        val current = snapshot()
        if (current == lastSaved) return
        store?.save(current)
        lastSaved = current
    }

    /** Applica preferenze caricate in background senza risalvarle. */
    fun restore(loaded: AppPreferences) {
        val value = loaded.sanitized()
        enemyCpuSpeed = value.speedOrDefault()
        animatedBackdrop = value.animatedBackdrop
        boardColorArgb = value.boardColorArgb
        boardStrokeWidth = value.boardStrokeWidth
        boardTemplateShape = value.templateShapeOrDefault()
        boardStampKind = value.stampKindOrDefault()
        language = value.languageOrSystemDefault()
        languageIsSystemDefault = value.language.isBlank()
        lastSaved = value
    }

    /** Riporta ogni preferenza al valore di fabbrica. */
    fun resetToDefaults() {
        val defaults = AppPreferences()
        enemyCpuSpeed = defaults.speedOrDefault()
        animatedBackdrop = defaults.animatedBackdrop
        boardColorArgb = defaults.boardColorArgb
        boardStrokeWidth = defaults.boardStrokeWidth
        boardTemplateShape = defaults.templateShapeOrDefault()
        boardStampKind = defaults.stampKindOrDefault()
        // La lingua torna a quella del sistema, cioe' allo stato di chi non ha
        // ancora scelto: e' il valore di fabbrica anche per lei.
        language = defaults.languageOrSystemDefault()
        languageIsSystemDefault = true
    }
}

val LocalAppPreferences = staticCompositionLocalOf { AppPreferencesState() }
