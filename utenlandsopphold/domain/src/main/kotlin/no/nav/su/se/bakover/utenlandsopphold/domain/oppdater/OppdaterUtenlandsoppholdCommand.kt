package no.nav.su.se.bakover.utenlandsopphold.domain.oppdater

import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.application.journal.JournalpostId
import no.nav.su.se.bakover.common.periode.DatoIntervall
import no.nav.su.se.bakover.hendelse.domain.HendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.utenlandsopphold.domain.UtenlandsoppholdDokumentasjon
import no.nav.su.se.bakover.utenlandsopphold.domain.UtenlandsoppholdHendelse
import java.time.Clock
import java.util.UUID

data class OppdaterUtenlandsoppholdCommand(
    val sakId: UUID,
    val periode: DatoIntervall,
    val dokumentasjon: UtenlandsoppholdDokumentasjon,
    val journalposter: List<JournalpostId>,
    val opprettetAv: NavIdentBruker.Saksbehandler,
    val correlationId: CorrelationId,
    val brukerroller: List<Brukerrolle>,
    val utenlandsoppholdId: UUID,
    val klientensSisteSaksversjon: Hendelsesversjon,
) {
    /**
     * @param forrigeVersjon Det kan ha skjedd andre endringer på saken. Må være nyere eller lik [forrigeHendelse].
     */
    fun toHendelse(
        forrigeHendelse: UtenlandsoppholdHendelse,
        forrigeVersjon: Hendelsesversjon,
        clock: Clock,
    ): OppdaterUtenlandsoppholdHendelse {
        return OppdaterUtenlandsoppholdHendelse.create(
            forrigeHendelse = forrigeHendelse,
            forrigeSakVersjon = forrigeVersjon,
            periode = periode,
            dokumentasjon = dokumentasjon,
            journalposter = journalposter,
            utførtAv = opprettetAv,
            clock = clock,
            hendelseMetadata = HendelseMetadata(
                correlationId = correlationId,
                ident = opprettetAv,
                brukerroller = brukerroller,
            ),
        )
    }
}
