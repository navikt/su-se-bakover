package no.nav.su.se.bakover.domain.brev.jsonRequest

import dokument.domain.pdf.PdfInnhold
import dokument.domain.pdf.PdfTemplateMedDokumentNavn
import dokument.domain.pdf.PersonaliaPdfInnhold
import no.nav.su.se.bakover.domain.brev.command.ForhåndsvarselDokumentCommand

data class ForhåndsvarselPdfInnhold(
    val personalia: PersonaliaPdfInnhold,
    val saksbehandlerNavn: String,
    val fritekst: String,
) : PdfInnhold {
    override val pdfTemplate = PdfTemplateMedDokumentNavn.Forhåndsvarsel

    companion object {
        fun fromBrevCommand(
            command: ForhåndsvarselDokumentCommand,
            personalia: PersonaliaPdfInnhold,
            saksbehandlerNavn: String,
        ): ForhåndsvarselPdfInnhold {
            return ForhåndsvarselPdfInnhold(
                personalia = personalia,
                saksbehandlerNavn = saksbehandlerNavn,
                fritekst = command.fritekst,
            )
        }
    }
}
