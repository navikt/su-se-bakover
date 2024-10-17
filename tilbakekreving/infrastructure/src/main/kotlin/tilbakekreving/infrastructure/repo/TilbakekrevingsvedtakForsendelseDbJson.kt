package tilbakekreving.infrastructure.repo

import no.nav.su.se.bakover.common.tid.Tidspunkt

data class TilbakekrevingsvedtakForsendelseDbJson(
    val requestXml: String,
    val tidspunkt: Tidspunkt,
    val responseXml: String,
)
