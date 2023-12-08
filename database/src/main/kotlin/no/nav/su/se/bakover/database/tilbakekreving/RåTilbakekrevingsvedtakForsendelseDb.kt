package no.nav.su.se.bakover.database.tilbakekreving

import no.nav.su.se.bakover.common.tid.Tidspunkt
import tilbakekreving.domain.kravgrunnlag.RåTilbakekrevingsvedtakForsendelse

internal data class RåTilbakekrevingsvedtakForsendelseDb(
    val requestXml: String,
    val responseXml: String,
    val tidspunkt: Tidspunkt,
) {
    companion object {
        fun fra(tilbakekrevingsvedtakForsendelse: RåTilbakekrevingsvedtakForsendelse): RåTilbakekrevingsvedtakForsendelseDb {
            return RåTilbakekrevingsvedtakForsendelseDb(
                requestXml = tilbakekrevingsvedtakForsendelse.requestXml,
                responseXml = tilbakekrevingsvedtakForsendelse.responseXml,
                tidspunkt = tilbakekrevingsvedtakForsendelse.tidspunkt,

            )
        }
    }
}
