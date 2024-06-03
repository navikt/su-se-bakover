package no.nav.su.se.bakover.kontrollsamtale.domain.oppdater.status

import arrow.core.Either
import arrow.core.left
import dokument.domain.journalføring.ErTilknyttetSak
import dokument.domain.journalføring.KunneIkkeSjekkeTilknytningTilSak
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtaler

suspend fun Sak.oppdaterStatusPåKontrollsamtale(
    command: OppdaterStatusPåKontrollsamtaleCommand,
    kontrollsamtaler: Kontrollsamtaler,
    erJournalpostTilknyttetSak: suspend (JournalpostId, Saksnummer) -> Either<KunneIkkeSjekkeTilknytningTilSak, ErTilknyttetSak>,
): Either<KunneIkkeOppdatereStatusPåKontrollsamtale, Pair<Kontrollsamtale, Kontrollsamtaler>> {
    return when (command.nyStatus) {
        is OppdaterStatusPåKontrollsamtaleCommand.OppdaterStatusTil.Gjennomført -> {
            val saksnummer = this.saksnummer
            val journalpostId = command.nyStatus.journalpostId
            erJournalpostTilknyttetSak(journalpostId, saksnummer).fold(
                ifLeft = {
                    KunneIkkeOppdatereStatusPåKontrollsamtale.JournalpostIkkeTilknyttetSak(
                        journalpostId,
                        saksnummer,
                    ).left()
                },
                ifRight = { kontrollsamtaler.oppdaterStatus(command, kontrollsamtaler) },
            )
        }

        else -> kontrollsamtaler.oppdaterStatus(command, kontrollsamtaler)
    }
}
