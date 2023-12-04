package tilbakekreving.domain.kravgrunnlag

import no.nav.su.se.bakover.common.tid.Tidspunkt

/**
 * Dette er det rå svaret vi får fra tilbakekrevingskomponenten etter vi har svart ut et tilbakekrevingsvedtak.
 */
data class RåTilbakekrevingsvedtakForsendelse(
    private val requestXml: String,
    private val tidspunkt: Tidspunkt,
    private val responseXml: String,
) {
    fun originalRequest(): String = requestXml
    fun tidspunkt(): Tidspunkt = tidspunkt
    fun originalRespons(): String = responseXml
}
