package no.nav.su.se.bakover.utenlandsopphold.domain.registrer

import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.application.journal.JournalpostId
import no.nav.su.se.bakover.common.periode.DatoIntervall
import no.nav.su.se.bakover.hendelse.domain.HendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.utenlandsopphold.domain.UtenlandsoppholdDokumentasjon
import java.time.Clock
import java.util.UUID

data class RegistrerUtenlandsoppholdCommand(
    val sakId: UUID,
    val periode: DatoIntervall,
    val dokumentasjon: UtenlandsoppholdDokumentasjon,
    val journalposter: List<JournalpostId>,
    val opprettetAv: NavIdentBruker.Saksbehandler,
    val correlationId: CorrelationId,
    val brukerroller: List<Brukerrolle>,
    val klientensSisteSaksversjon: Hendelsesversjon,
) {
    fun toHendelse(
        clock: Clock,
        forrigeVersjon: Hendelsesversjon,
    ): RegistrerUtenlandsoppholdHendelse {
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
                brukerroller = brukerroller,
            ),
            forrigeVersjon = forrigeVersjon,
        )
    }
}
