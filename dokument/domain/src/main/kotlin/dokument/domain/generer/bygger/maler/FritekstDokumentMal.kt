package dokument.domain.generer.bygger.maler

import com.fasterxml.jackson.annotation.JsonPropertyOrder
import dokument.domain.generer.DokumentMal
import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.dokument
import dokument.domain.generer.bygger.fritekst
import dokument.domain.generer.bygger.harDuSpørsmål
import dokument.domain.generer.bygger.hilsen
import no.nav.brev.brevbaker.markup.dsl.paragraph
import no.nav.su.se.bakover.common.domain.sak.Sakstype

@JsonPropertyOrder(
    "personalia",
    "saksbehandlerNavn",
    "tittel",
    "fritekst",
    "sakstype",
)
data class FritekstPdfInnhold(
    override val sakstype: Sakstype,
    val personalia: PersonaliaPdfInnhold,
    val saksbehandlerNavn: String,
    val tittel: String,
    val fritekst: String,
) : PdfInnhold {
    override val dokumentMal = DokumentMal.FritekstDokument(tittel)
}

val fritekstDokument = { _: String, pdfInnhold: FritekstPdfInnhold ->
    dokument(pdfInnhold.tittel, pdfInnhold.personalia) {
        paragraph("Fødselsnummer: ${pdfInnhold.personalia.fødselsnummer}")
        fritekst(pdfInnhold.fritekst)
        harDuSpørsmål(pdfInnhold.sakstype)
        hilsen(saksbehandler = pdfInnhold.saksbehandlerNavn)
    }
}
