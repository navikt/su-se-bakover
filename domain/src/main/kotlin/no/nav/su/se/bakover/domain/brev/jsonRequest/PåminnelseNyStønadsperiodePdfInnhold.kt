package no.nav.su.se.bakover.domain.brev.jsonRequest

import dokument.domain.pdf.PdfInnhold
import dokument.domain.pdf.PdfTemplateMedDokumentNavn
import dokument.domain.pdf.PersonaliaPdfInnhold
import no.nav.su.se.bakover.common.extensions.ddMMyyyy
import no.nav.su.se.bakover.domain.brev.command.PåminnelseNyStønadsperiodeDokumentCommand

data class PåminnelseNyStønadsperiodePdfInnhold(
    val personalia: PersonaliaPdfInnhold,
    val utløpsdato: String,
    val halvtGrunnbeløp: Int,
) : PdfInnhold {
    override val pdfTemplate = PdfTemplateMedDokumentNavn.PåminnelseNyStønadsperiode

    companion object {
        fun fromBrevCommand(
            command: PåminnelseNyStønadsperiodeDokumentCommand,
            personalia: PersonaliaPdfInnhold,
        ): PåminnelseNyStønadsperiodePdfInnhold {
            return PåminnelseNyStønadsperiodePdfInnhold(
                personalia = personalia,
                utløpsdato = command.utløpsdato.ddMMyyyy(),
                halvtGrunnbeløp = command.halvtGrunnbeløp,
            )
        }
    }
}
