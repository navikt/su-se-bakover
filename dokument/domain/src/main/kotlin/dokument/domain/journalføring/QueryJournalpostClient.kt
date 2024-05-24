package dokument.domain.journalføring

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.tid.periode.DatoIntervall

interface QueryJournalpostClient {
    /**
     * Sjekker om aktuell [journalpostId] er knyttet til [saksnummer]
     */
    suspend fun erTilknyttetSak(
        journalpostId: JournalpostId,
        saksnummer: Saksnummer,
    ): Either<KunneIkkeSjekkeTilknytningTilSak, ErTilknyttetSak>

    fun hentJournalposterFor(saksnummer: Saksnummer, limit: Int = 50): Either<KunneIkkeHenteJournalposter, List<Journalpost>>
    fun finnesFagsak(fagsystemId: String, limit: Int = 50): Either<KunneIkkeHenteJournalposter, Boolean>

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
    data object Nei : ErKontrollNotatMottatt
    data class Ja(val kontrollnotat: KontrollnotatMottattJournalpost) : ErKontrollNotatMottatt
}
sealed interface ErTilknyttetSak {
    data object Ja : ErTilknyttetSak
    data object Nei : ErTilknyttetSak
}
sealed interface KunneIkkeSjekkeTilknytningTilSak {
    data object Ukjent : KunneIkkeSjekkeTilknytningTilSak
    data object FantIkkeJournalpost : KunneIkkeSjekkeTilknytningTilSak
    data object IkkeTilgang : KunneIkkeSjekkeTilknytningTilSak
    data object TekniskFeil : KunneIkkeSjekkeTilknytningTilSak
    data object UgyldigInput : KunneIkkeSjekkeTilknytningTilSak
    data object JournalpostIkkeKnyttetTilSak : KunneIkkeSjekkeTilknytningTilSak
}

sealed interface KunneIkkeHenteJournalposter {
    data object ClientError : KunneIkkeHenteJournalposter
}
