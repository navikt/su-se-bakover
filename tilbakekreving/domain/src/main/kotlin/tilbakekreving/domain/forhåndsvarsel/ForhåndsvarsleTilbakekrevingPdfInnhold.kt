package tilbakekreving.domain.forhåndsvarsel

import dokument.domain.pdf.PdfInnhold
import dokument.domain.pdf.PdfTemplateMedDokumentNavn
import dokument.domain.pdf.PersonaliaPdfInnhold

/**
 * TODO - vil helst at denne kanskhe skal bo nærmere tilbakekreving
 */
data class ForhåndsvarsleTilbakekrevingPdfInnhold(
    val personalia: PersonaliaPdfInnhold,
    val saksbehandlerNavn: String,
    val fritekst: String,
) : PdfInnhold() {
    override val pdfTemplate = PdfTemplateMedDokumentNavn.ForhåndsvarselTilbakekreving

    companion object {
        fun fromBrevCommand(
            command: ForhåndsvarselTilbakekrevingsbehandlingCommand,
            personalia: PersonaliaPdfInnhold,
            saksbehandlerNavn: String,
        ): ForhåndsvarsleTilbakekrevingPdfInnhold {
            return ForhåndsvarsleTilbakekrevingPdfInnhold(
                personalia = personalia,
                saksbehandlerNavn = saksbehandlerNavn,
                fritekst = command.fritekst,
            )
        }
    }
}
