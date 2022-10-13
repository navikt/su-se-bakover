package no.nav.su.se.bakover.utenlandsopphold.presentation.web.oppdater

import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.application.journal.JournalpostId
import no.nav.su.se.bakover.common.infrastructure.web.periode.PeriodeJson
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.utenlandsopphold.domain.oppdater.OppdaterUtenlandsoppholdCommand
import no.nav.su.se.bakover.utenlandsopphold.presentation.web.UtenlandsoppholdDokumentasjonJson
import java.util.UUID

/**
 * @param journalposter kan være tom dersom det ikke er knyttet noen journalposter til utenlandsoppholdet.
 */
data class OppdaterUtenlandsoppholdJson(
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
        utenlandsoppholdId: UUID,
    ) = OppdaterUtenlandsoppholdCommand(
        sakId = sakId,
        periode = periode.toDatoIntervall(),
        dokumentasjon = dokumentasjon.toDomain(),
        journalposter = journalposter.map { JournalpostId(it) },
        opprettetAv = opprettetAv,
        correlationId = correlationId,
        brukerroller = brukerroller,
        utenlandsoppholdId = utenlandsoppholdId,
        klientensSisteSaksversjon = Hendelsesversjon(saksversjon),
    )
}
