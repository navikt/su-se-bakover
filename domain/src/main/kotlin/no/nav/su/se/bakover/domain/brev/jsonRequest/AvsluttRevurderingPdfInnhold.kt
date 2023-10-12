package no.nav.su.se.bakover.domain.brev.jsonRequest

import dokument.domain.pdf.PdfInnhold
import dokument.domain.pdf.PdfTemplateMedDokumentNavn
import no.nav.su.se.bakover.domain.brev.command.AvsluttRevurderingDokumentCommand

/**
 * Brev for når en revurdering er forhåndsvarslet
 * hvis revurderingen ikke er forhåndsvarslet, er det ikke noe brev.
 */
data class AvsluttRevurderingPdfInnhold(
    val personalia: PersonaliaPdfInnhold,
    val saksbehandlerNavn: String,
    val fritekst: String?,
) : PdfInnhold() {
    override val pdfTemplate = PdfTemplateMedDokumentNavn.Revurdering.AvsluttRevurdering

    companion object {
        fun fromBrevCommand(
            command: AvsluttRevurderingDokumentCommand,
            personalia: PersonaliaPdfInnhold,
            saksbehandlerNavn: String,
        ): AvsluttRevurderingPdfInnhold {
            return AvsluttRevurderingPdfInnhold(
                personalia = personalia,
                saksbehandlerNavn = saksbehandlerNavn,
                fritekst = command.fritekst,
            )
        }
    }
}
