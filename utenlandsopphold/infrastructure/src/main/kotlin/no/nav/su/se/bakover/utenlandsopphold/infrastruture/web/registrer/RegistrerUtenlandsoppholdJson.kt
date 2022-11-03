package no.nav.su.se.bakover.utenlandsopphold.infrastruture.web.registrer

import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.application.journal.JournalpostId
import no.nav.su.se.bakover.common.infrastructure.web.periode.PeriodeJson
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.utenlandsopphold.domain.registrer.RegistrerUtenlandsoppholdCommand
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.web.UtenlandsoppholdDokumentasjonJson
import java.util.UUID

/**
 * @param journalposter kan være tom dersom det ikke er knyttet noen journalposter til utenlandsoppholdet.
 */
data class RegistrerUtenlandsoppholdJson(
    val periode: PeriodeJson,
    val journalposter: List<String>,
    val dokumentasjon: UtenlandsoppholdDokumentasjonJson,
    val saksversjon: Long,
) {
    fun toCommand(
        sakId: UUID,
        opprettetAv: NavIdentBruker.Saksbehandler,
        correlationId: CorrelationId,
        brukerroller: List<Brukerrolle>,
    ) = RegistrerUtenlandsoppholdCommand(
        sakId = sakId,
        periode = periode.toDatoIntervall(),
        dokumentasjon = dokumentasjon.toDomain(),
        journalposter = journalposter.map { JournalpostId(it) },
        opprettetAv = opprettetAv,
        correlationId = correlationId,
        brukerroller = brukerroller,
        klientensSisteSaksversjon = Hendelsesversjon(saksversjon),
    )
}
