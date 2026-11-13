package dokument.domain.generer.bygger.maler

import dokument.domain.generer.DokumentMal
import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.dokument
import dokument.domain.generer.bygger.fellesKlageinfo
import dokument.domain.generer.bygger.fritekst
import dokument.domain.generer.bygger.fødselsnummerAvsnitt
import dokument.domain.generer.bygger.hilsen
import no.nav.su.se.bakover.common.domain.sak.Sakstype

data class AvvistSøknadVedtakPdfInnhold(
    override val sakstype: Sakstype,
    val personalia: PersonaliaPdfInnhold,
    val saksbehandlerNavn: String,
    val fritekst: String?,
) : PdfInnhold {
    override val dokumentMal = DokumentMal.AvvistSøknadVedtak
}

val avvistSøknadVedtak = { _: String, pdfInnhold: AvvistSøknadVedtakPdfInnhold ->
    val tittel = "Vi har avslått søknaden din om supplerende stønad"
    dokument(tittel, pdfInnhold.personalia) {
        fødselsnummerAvsnitt(pdfInnhold.personalia)
        pdfInnhold.fritekst?.let { fritekst(it) }
        fellesKlageinfo(pdfInnhold.sakstype)
        hilsen(saksbehandler = pdfInnhold.saksbehandlerNavn)
    }
}
