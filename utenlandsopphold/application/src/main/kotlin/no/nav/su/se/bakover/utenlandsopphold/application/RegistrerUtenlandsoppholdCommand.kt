package no.nav.su.se.bakover.utenlandsopphold.application

import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.application.journal.JournalpostId
import no.nav.su.se.bakover.common.periode.DatoIntervall
import no.nav.su.se.bakover.utenlandsopphold.domain.RegistrertUtenlandsopphold
import no.nav.su.se.bakover.utenlandsopphold.domain.UtenlandsoppholdDokumentasjon
import java.time.Clock
import java.util.UUID

data class RegistrerUtenlandsoppholdCommand(
    val sakId: UUID,
    val periode: DatoIntervall,
    val dokumentasjon: UtenlandsoppholdDokumentasjon,
    val journalposter: List<JournalpostId>,
    val opprettetAv: NavIdentBruker.Saksbehandler,
) {
    fun toUtenlandsopphold(clock: Clock): RegistrertUtenlandsopphold {
        return RegistrertUtenlandsopphold.registrer(
            periode = periode,
            dokumentasjon = dokumentasjon,
            journalposter = journalposter,
            opprettetAv = opprettetAv,
            clock = clock,
        )
    }
}
