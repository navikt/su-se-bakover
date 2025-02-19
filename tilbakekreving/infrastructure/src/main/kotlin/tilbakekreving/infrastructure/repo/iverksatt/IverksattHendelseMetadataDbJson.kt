package tilbakekreving.infrastructure.repo.iverksatt

import no.nav.su.se.bakover.common.infrastructure.ident.BrukerrolleJson
import no.nav.su.se.bakover.common.infrastructure.ident.toBrukerrollerJson
import no.nav.su.se.bakover.common.serialize
import tilbakekreving.domain.iverksettelse.IverksattHendelseMetadata
import tilbakekreving.infrastructure.repo.TilbakekrevingsvedtakForsendelseDbJson

data class IverksattHendelseMetadataDbJson(
    val correlationId: String,
    val ident: String,
    val brukerroller: List<BrukerrolleJson>,
    val tilbakekrevingsvedtakForsendelse: TilbakekrevingsvedtakForsendelseDbJson,
)

fun IverksattHendelseMetadata.toDbJson(): String {
    return serialize(
        IverksattHendelseMetadataDbJson(
            correlationId = correlationId.toString(),
            ident = ident.toString(),
            brukerroller = brukerroller.toBrukerrollerJson(),
            tilbakekrevingsvedtakForsendelse = TilbakekrevingsvedtakForsendelseDbJson(
                requestXml = tilbakekrevingsvedtakForsendelse.requestXml,
                tidspunkt = tilbakekrevingsvedtakForsendelse.tidspunkt,
                responseXml = tilbakekrevingsvedtakForsendelse.responseXml,
            ),
        ),
    )
}
