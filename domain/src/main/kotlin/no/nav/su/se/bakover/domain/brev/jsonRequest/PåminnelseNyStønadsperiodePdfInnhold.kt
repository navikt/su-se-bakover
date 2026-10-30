package no.nav.su.se.bakover.domain.brev.jsonRequest

import dokument.domain.generer.DokumentMal
import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.PersonaliaPdfInnhold
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.ddMMyyyy
import no.nav.su.se.bakover.domain.brev.command.PåminnelseNyStønadsperiodeDokumentCommand

data class PåminnelseNyStønadsperiodePdfInnhold(
    override val sakstype: Sakstype,
    val personalia: PersonaliaPdfInnhold,
    val utløpsdato: String,
    // Er en bruker med uføre som fyller 67 år i stønadsperiode sin siste måned
    val uføreSomFyller67: Boolean,
) : PdfInnhold {
    override val dokumentMal = DokumentMal.PåminnelseNyStønadsperiode

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
