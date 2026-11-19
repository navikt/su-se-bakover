package dokument.domain.generer.bygger.maler

import dokument.domain.generer.DokumentMal
import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.BeregningsperiodeBrev
import dokument.domain.generer.bygger.SatsoversiktBrev
import dokument.domain.generer.bygger.dokument
import no.nav.brev.brevbaker.markup.dsl.paragraph
import no.nav.su.se.bakover.common.domain.sak.Sakstype

data class VedtakVedReguleringPdfInnhold(
    override val sakstype: Sakstype,
    val fraOgMed: String,
    val alderEndretFraOgMed: String,
    val personalia: PersonaliaPdfInnhold,
    val saksbehandlerNavn: String,
    val beregningsperioder: List<BeregningsperiodeBrev>,
    val satsoversikt: SatsoversiktBrev,
    val attestantNavn: String,
) : PdfInnhold {
    override val dokumentMal = DokumentMal.VedtaksbrevReguleringAldersfradrag
}

val vedtaksbrevReguleringAldersfradrag = { _: String, pdfInnhold: VedtakVedReguleringPdfInnhold ->
    dokument("Vi har vurdert din supplerende stønad på nytt", pdfInnhold.personalia) {
        paragraph("test")
    }
}
