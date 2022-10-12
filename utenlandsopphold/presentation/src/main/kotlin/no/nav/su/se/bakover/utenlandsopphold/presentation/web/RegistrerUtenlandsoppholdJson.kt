package no.nav.su.se.bakover.utenlandsopphold.presentation.web

import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.application.journal.JournalpostId
import no.nav.su.se.bakover.common.infrastructure.web.periode.PeriodeJson
import no.nav.su.se.bakover.utenlandsopphold.domain.RegistrerUtenlandsoppholdCommand
import java.util.UUID

/**
 * @param journalposter kan være tom dersom det ikke er knyttet noen journalposter til utenlandsoppholdet.
 */
data class RegistrerUtenlandsoppholdJson(
    val periode: PeriodeJson,
    val journalposter: List<String>,
    val dokumentasjon: UtenlandsoppholdDokumentasjonJson,
) {
    fun toCommand(
        sakId: UUID,
        opprettetAv: NavIdentBruker.Saksbehandler,
        correlationId: CorrelationId,
    ) = RegistrerUtenlandsoppholdCommand(
        sakId = sakId,
        periode = periode.toDatoIntervall(),
        dokumentasjon = dokumentasjon.toDomain(),
        journalposter = journalposter.map { JournalpostId(it) },
        opprettetAv = opprettetAv,
        correlationId = correlationId,
    )
}
