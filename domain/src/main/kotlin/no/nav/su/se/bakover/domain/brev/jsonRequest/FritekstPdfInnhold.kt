package no.nav.su.se.bakover.domain.brev.jsonRequest

import com.fasterxml.jackson.annotation.JsonPropertyOrder
import dokument.domain.pdf.PdfInnhold
import dokument.domain.pdf.PdfTemplateMedDokumentNavn
import dokument.domain.pdf.PersonaliaPdfInnhold
import no.nav.su.se.bakover.domain.brev.command.FritekstDokumentCommand

@JsonPropertyOrder(
    "personalia",
    "saksbehandlerNavn",
    "tittel",
    "fritekst",
    "sakstype",
    "erAldersbrev",
)
data class FritekstPdfInnhold(
    val personalia: PersonaliaPdfInnhold,
    val saksbehandlerNavn: String,
    val tittel: String,
    val fritekst: String,
) : PdfInnhold {
    override val pdfTemplate: PdfTemplateMedDokumentNavn = PdfTemplateMedDokumentNavn.Fritekst(tittel)

    companion object {
        fun fromBrevCommand(
            command: FritekstDokumentCommand,
            personalia: PersonaliaPdfInnhold,
            saksbehandlerNavn: String,
        ): FritekstPdfInnhold {
            return FritekstPdfInnhold(
                personalia = personalia,
                saksbehandlerNavn = saksbehandlerNavn,
                tittel = command.brevTittel,
                fritekst = command.fritekst,
            )
        }
    }
}
