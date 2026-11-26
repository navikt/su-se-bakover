package no.nav.su.se.bakover.domain.brev.jsonRequest

import dokument.domain.Brevtype
import dokument.domain.generer.Brevmal
import dokument.domain.generer.Dokumenttype
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

val påminnelseNyStønadsperiodeBrev = Brevmal<PåminnelseNyStønadsperiodeDokumentCommand>(
    dokumenttype = Dokumenttype.INFORMASJON_VIKTIG,
    brevtype = Brevtype.PÅMINNELSE_NY_STØNADSPERIODE,
) { felles, kommando ->
    kommando.fromBrevCommand(personalia = felles.personalia)
}
