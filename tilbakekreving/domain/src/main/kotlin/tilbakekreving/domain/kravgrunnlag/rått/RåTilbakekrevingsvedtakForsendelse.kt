package tilbakekreving.domain.kravgrunnlag.rått

import no.nav.su.se.bakover.common.tid.Tidspunkt

/**
 * Dette er det rå svaret vi får fra tilbakekrevingskomponenten etter vi har svart ut et tilbakekrevingsvedtak.
 */
data class RåTilbakekrevingsvedtakForsendelse(
    val requestXml: String,
    val responseXml: String,
    val tidspunkt: Tidspunkt,
)
