package tilbakekreving.infrastructure.repo.kravgrunnlag

import no.nav.su.se.bakover.common.infrastructure.ident.BrukerrolleJson
import no.nav.su.se.bakover.common.infrastructure.ident.toBrukerrollerJson
import no.nav.su.se.bakover.common.serialize
import tilbakekreving.domain.kravgrunnlag.repo.AnnullerKravgrunnlagStatusEndringMeta
import tilbakekreving.infrastructure.repo.TilbakekrevingsvedtakForsendelseDbJson

data class AnnullerKravgrunnlagStatusEndringMetaJson(
    val correlationId: String,
    val ident: String,
    val brukerroller: List<BrukerrolleJson>,
    val tilbakekrevingsvedtakForsendelse: TilbakekrevingsvedtakForsendelseDbJson,
)

fun AnnullerKravgrunnlagStatusEndringMeta.toDbJson(): String {
    return serialize(
        AnnullerKravgrunnlagStatusEndringMetaJson(
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
