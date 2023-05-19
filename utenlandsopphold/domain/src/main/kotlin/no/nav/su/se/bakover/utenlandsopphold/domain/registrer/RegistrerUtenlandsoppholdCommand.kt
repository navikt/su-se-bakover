package no.nav.su.se.bakover.utenlandsopphold.domain.registrer

import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.tid.periode.DatoIntervall
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
    val begrunnelse: String?,
    val correlationId: CorrelationId,
    val brukerroller: List<Brukerrolle>,
    val klientensSisteSaksversjon: Hendelsesversjon,
) {
    fun toHendelse(
        clock: Clock,
        nesteVersjon: Hendelsesversjon,
    ): RegistrerUtenlandsoppholdHendelse {
        return RegistrerUtenlandsoppholdHendelse.registrer(
            sakId = sakId,
            periode = periode,
            dokumentasjon = dokumentasjon,
            journalposter = journalposter,
            begrunnelse = begrunnelse,
            opprettetAv = opprettetAv,
            clock = clock,
            hendelseMetadata = HendelseMetadata(
                correlationId = correlationId,
                ident = opprettetAv,
                brukerroller = brukerroller,
            ),
            nesteVersjon = nesteVersjon,
        )
    }
}
