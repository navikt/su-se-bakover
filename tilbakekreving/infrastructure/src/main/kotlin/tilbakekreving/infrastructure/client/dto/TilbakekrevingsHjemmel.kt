package tilbakekreving.infrastructure.client.dto

/**
 * Dersom man bruker hjemmel 'ANNET' sender tilbakekrevingskomponenten posisjon 118 som blank til NAVI/Predator og den vil bli behandlet som foreldet.
 * Dersom man bruker hjemmel 'SUL_13' vil tilbakekrevingskomponenten sende T på posisjon 118 istedet og vi vil få forventet oppførsel.
 * Vi har også bestilt SUL_13-1, SUL_13-2, SUL_13-3 og SUL_13-4 som vi ikke har tatt i bruk enda.
 */
internal enum class TilbakekrevingsHjemmel(val value: String) {
    T("SUL_13"),
    ;

    override fun toString() = value
}
