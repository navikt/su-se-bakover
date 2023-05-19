package no.nav.su.se.bakover.database.tilbakekreving

import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.RåTilbakekrevingsvedtakForsendelse

internal data class RåTilbakekrevingsvedtakForsendelseDb(
    val requestXml: String,
    val requestSendt: Tidspunkt,
    val responseXml: String,
) {
    companion object {
        fun fra(tilbakekrevingsvedtakForsendelse: RåTilbakekrevingsvedtakForsendelse): RåTilbakekrevingsvedtakForsendelseDb {
            return RåTilbakekrevingsvedtakForsendelseDb(
                requestXml = tilbakekrevingsvedtakForsendelse.originalRequest(),
                requestSendt = tilbakekrevingsvedtakForsendelse.tidspunkt(),
                responseXml = tilbakekrevingsvedtakForsendelse.originalRespons(),
            )
        }
    }
}
