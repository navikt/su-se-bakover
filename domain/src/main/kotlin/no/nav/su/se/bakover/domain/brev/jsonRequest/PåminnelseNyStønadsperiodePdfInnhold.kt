package no.nav.su.se.bakover.domain.brev.jsonRequest

import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.maler.PåminnelseNyStønadsperiodePdfInnhold
import no.nav.su.se.bakover.common.domain.tid.ddMMyyyy
import no.nav.su.se.bakover.domain.brev.command.PåminnelseNyStønadsperiodeDokumentCommand

fun PåminnelseNyStønadsperiodeDokumentCommand.fromBrevCommand(
    personalia: PersonaliaPdfInnhold,
): PåminnelseNyStønadsperiodePdfInnhold {
    return PåminnelseNyStønadsperiodePdfInnhold(
        sakstype = sakstype,
        personalia = personalia,
        utløpsdato = utløpsdato.ddMMyyyy(),
        uføreSomFyller67 = uføreSomFyller67,
    )
}
