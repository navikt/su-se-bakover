package no.nav.su.se.bakover.utenlandsopphold.domain

import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.application.journal.JournalpostId
import no.nav.su.se.bakover.common.periode.DatoIntervall
import no.nav.su.se.bakover.hendelse.domain.HendelseMetadata
import java.time.Clock
import java.util.UUID

data class RegistrerUtenlandsoppholdCommand(
    val sakId: UUID,
    val periode: DatoIntervall,
    val dokumentasjon: UtenlandsoppholdDokumentasjon,
    val journalposter: List<JournalpostId>,
    val opprettetAv: NavIdentBruker.Saksbehandler,
    val correlationId: CorrelationId,
) {
    fun toUtenlandsopphold(clock: Clock): RegistrerUtenlandsoppholdHendelse {
        return RegistrerUtenlandsoppholdHendelse.registrer(
            sakId = sakId,
            periode = periode,
            dokumentasjon = dokumentasjon,
            journalposter = journalposter,
            opprettetAv = opprettetAv,
            clock = clock,
            hendelseMetadata = HendelseMetadata(
                correlationId = correlationId,
                ident = opprettetAv,
            ),
        )
    }
}
