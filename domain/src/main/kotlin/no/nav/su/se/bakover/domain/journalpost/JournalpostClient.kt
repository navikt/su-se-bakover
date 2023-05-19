package no.nav.su.se.bakover.domain.journalpost

import arrow.core.Either
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.tid.periode.DatoIntervall
import no.nav.su.se.bakover.domain.sak.Saksnummer

interface JournalpostClient {
    /**
     * Sjekker om aktuell [journalpostId] er knyttet til [saksnummer]
     */
    suspend fun erTilknyttetSak(
        journalpostId: JournalpostId,
        saksnummer: Saksnummer,
    ): Either<KunneIkkeSjekkeTilknytningTilSak, ErTilknyttetSak>

    /**
     * Skreddersydd for å svare på om det er mottatt et kontrollnotat for [saksnummer] i løpet av gitt [periode].
     */
    fun kontrollnotatMotatt(
        saksnummer: Saksnummer,
        periode: DatoIntervall,
    ): Either<KunneIkkeSjekkKontrollnotatMottatt, ErKontrollNotatMottatt>
}

data class KunneIkkeSjekkKontrollnotatMottatt(val feil: Any)

sealed interface ErKontrollNotatMottatt {
    object Nei : ErKontrollNotatMottatt
    data class Ja(val kontrollnotat: KontrollnotatMottattJournalpost) : ErKontrollNotatMottatt
}
sealed interface ErTilknyttetSak {
    object Ja : ErTilknyttetSak
    object Nei : ErTilknyttetSak
}
sealed interface KunneIkkeSjekkeTilknytningTilSak {
    object Ukjent : KunneIkkeSjekkeTilknytningTilSak
    object FantIkkeJournalpost : KunneIkkeSjekkeTilknytningTilSak
    object IkkeTilgang : KunneIkkeSjekkeTilknytningTilSak
    object TekniskFeil : KunneIkkeSjekkeTilknytningTilSak
    object UgyldigInput : KunneIkkeSjekkeTilknytningTilSak
    object JournalpostIkkeKnyttetTilSak : KunneIkkeSjekkeTilknytningTilSak
}
