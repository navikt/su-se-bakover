package no.nav.su.se.bakover.domain.brev.jsonRequest

import dokument.domain.pdf.PdfInnhold
import dokument.domain.pdf.PdfTemplateMedDokumentNavn
import dokument.domain.pdf.PersonaliaPdfInnhold
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.ddMMyyyy
import no.nav.su.se.bakover.domain.brev.command.PåminnelseNyStønadsperiodeDokumentCommand

data class PåminnelseNyStønadsperiodePdfInnhold(
    override val sakstype: Sakstype,
    val personalia: PersonaliaPdfInnhold,
    val utløpsdato: String,
    val uføreSomFyller67: Boolean,
) : PdfInnhold {
    override val pdfTemplate = PdfTemplateMedDokumentNavn.PåminnelseNyStønadsperiode

    companion object {
        fun fromBrevCommand(
            command: PåminnelseNyStønadsperiodeDokumentCommand,
            personalia: PersonaliaPdfInnhold,
        ): PåminnelseNyStønadsperiodePdfInnhold {
            return PåminnelseNyStønadsperiodePdfInnhold(
                sakstype = command.sakstype,
                personalia = personalia,
                utløpsdato = command.utløpsdato.ddMMyyyy(),
                uføreSomFyller67 = command.uføreSomFyller67,
            )
        }
    }
}
