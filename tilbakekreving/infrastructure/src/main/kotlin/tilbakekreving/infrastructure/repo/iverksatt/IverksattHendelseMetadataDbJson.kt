package tilbakekreving.infrastructure.repo.iverksatt

import no.nav.su.se.bakover.common.infrastructure.ident.BrukerrolleJson
import no.nav.su.se.bakover.common.infrastructure.ident.toBrukerrollerJson
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import tilbakekreving.domain.iverksettelse.IverksattHendelseMetadata

data class IverksattHendelseMetadataDbJson(
    val correlationId: String,
    val ident: String,
    val brukerroller: List<BrukerrolleJson>,
    val tilbakekrevingsvedtakForsendelse: TilbakekrevingsvedtakForsendelseDbJson,
) {
    data class TilbakekrevingsvedtakForsendelseDbJson(
        private val requestXml: String,
        private val tidspunkt: Tidspunkt,
        private val responseXml: String,
    )
}

fun IverksattHendelseMetadata.toDbJson(): String {
    return serialize(
        IverksattHendelseMetadataDbJson(
            correlationId = correlationId.toString(),
            ident = ident.toString(),
            brukerroller = brukerroller.toBrukerrollerJson(),
            tilbakekrevingsvedtakForsendelse = IverksattHendelseMetadataDbJson.TilbakekrevingsvedtakForsendelseDbJson(
                requestXml = tilbakekrevingsvedtakForsendelse.requestXml,
                tidspunkt = tilbakekrevingsvedtakForsendelse.tidspunkt,
                responseXml = tilbakekrevingsvedtakForsendelse.responseXml,
            ),
        ),
    )
}
