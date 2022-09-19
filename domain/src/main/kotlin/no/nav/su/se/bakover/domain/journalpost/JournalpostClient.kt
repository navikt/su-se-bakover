package no.nav.su.se.bakover.domain.journalpost

import arrow.core.Either
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.journal.JournalpostId

interface JournalpostClient {
    /**
     * Denne flyten er skreddersydd for å hente innkommende, journalførte dokumenter som tilhører SUP.
     */
    fun hentFerdigstiltJournalpost(
        saksnummer: Saksnummer,
        journalpostId: JournalpostId,
    ): Either<KunneIkkeHenteJournalpost, FerdigstiltJournalpost>

    /**
     * Skreddersydd for å svare på om det er mottatt et kontrollnotat for [saksnummer] i løpet av gitt [periode].
     */
    fun kontrollnotatMotatt(
        saksnummer: Saksnummer,
        periode: Periode
    ): Either<KunneIkkeSjekkKontrollnotatMottatt, ErKontrollNotatMottatt>
}

data class KunneIkkeSjekkKontrollnotatMottatt(val feil: Any)

sealed interface ErKontrollNotatMottatt {
    object Nei : ErKontrollNotatMottatt
    data class Ja(val kontrollnotat: KontrollnotatMottattJournalpost) : ErKontrollNotatMottatt
}

sealed interface KunneIkkeHenteJournalpost {
    object Ukjent : KunneIkkeHenteJournalpost
    object FantIkkeJournalpost : KunneIkkeHenteJournalpost
    object IkkeTilgang : KunneIkkeHenteJournalpost
    object TekniskFeil : KunneIkkeHenteJournalpost
    object UgyldigInput : KunneIkkeHenteJournalpost
    object JournalpostIkkeKnyttetTilSak : KunneIkkeHenteJournalpost
    object JournalpostenErIkkeEtInnkommendeDokument : KunneIkkeHenteJournalpost
    object JournalpostTemaErIkkeSUP : KunneIkkeHenteJournalpost
    object JournalpostenErIkkeFerdigstilt : KunneIkkeHenteJournalpost
}
