package app.d6d.domain.space;

/**
 * Collocazione dello sfondo sulla griglia, in unita' di casella.
 *
 * <p>{@code offsetX}/{@code offsetY} sono l'angolo in alto a sinistra dell'immagine
 * rispetto all'origine della griglia (casella 0,0); {@code width}/{@code height}
 * sono la misura con cui l'immagine viene disegnata. Tutto in caselle, non in pixel:
 * cosi' la collocazione resta valida a qualunque zoom e con qualunque scala in
 * piedi, perche' la griglia e' l'unico sistema di riferimento stabile.</p>
 *
 * <p>Lo sfondo puo' debordare dalla griglia — valori negativi o oltre il bordo:
 * incorniciare solo una parte dell'immagine e' una scelta legittima.</p>
 *
 * <p>{@link #UNSET} (larghezza o altezza non positive) significa "non ancora
 * collocato": l'interfaccia calcola allora una collocazione predefinita che
 * conserva le proporzioni dell'immagine, invece di deformarla per riempire la
 * griglia.</p>
 */
public record MapBackground(double offsetX, double offsetY, double width, double height) {

    /** Sfondo non ancora collocato: l'interfaccia decide una posizione iniziale. */
    public static final MapBackground UNSET = new MapBackground(0, 0, 0, 0);

    public MapBackground {
        if (!Double.isFinite(offsetX) || !Double.isFinite(offsetY)
                || !Double.isFinite(width) || !Double.isFinite(height)) {
            throw new IllegalArgumentException("Le coordinate dello sfondo devono essere numeri finiti");
        }
    }

    /** Vero quando la collocazione e' stata decisa e ha una misura disegnabile. */
    public boolean isSet() {
        return width > 0 && height > 0;
    }
}
