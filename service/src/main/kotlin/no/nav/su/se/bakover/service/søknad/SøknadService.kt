package no.nav.su.se.bakover.service.søknad

import arrow.core.Either
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnhold
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import java.util.UUID

interface SøknadService {
    fun nySøknad(søknadInnhold: SøknadInnhold): Either<KunneIkkeOppretteSøknad, Pair<Saksnummer, Søknad>>
    fun hentSøknad(søknadId: UUID): Either<FantIkkeSøknad, Søknad>
    fun hentSøknadPdf(søknadId: UUID): Either<KunneIkkeLageSøknadPdf, ByteArray>
    fun opprettManglendeJournalpostOgOppgave(): OpprettManglendeJournalpostOgOppgaveResultat
}

object FantIkkeSøknad

sealed class KunneIkkeOppretteSøknad {
    object FantIkkePerson : KunneIkkeOppretteSøknad()
}

sealed class KunneIkkeLageSøknadPdf {
    object FantIkkeSøknad : KunneIkkeLageSøknadPdf()
    object KunneIkkeLagePdf : KunneIkkeLageSøknadPdf()
    object FantIkkePerson : KunneIkkeLageSøknadPdf()
    object FantIkkeSak : KunneIkkeLageSøknadPdf()
}

data class OpprettManglendeJournalpostOgOppgaveResultat(
    val journalpostResultat: List<Either<KunneIkkeOppretteJournalpost, OpprettetJournalpost>>,
    val oppgaveResultat: List<Either<KunneIkkeOppretteOppgave, OpprettetOppgave>>
) {
    fun harFeil(): Boolean = journalpostResultat.mapNotNull { it.swap().orNull() }.isNotEmpty() ||
        oppgaveResultat.mapNotNull { it.swap().orNull() }.isNotEmpty()
}
data class KunneIkkeOppretteJournalpost(val sakId: UUID)
data class OpprettetJournalpost(val sakId: UUID, val journalpostId: JournalpostId)
data class KunneIkkeOppretteOppgave(val sakId: UUID)
data class OpprettetOppgave(val sakId: UUID, val oppgaveId: OppgaveId)
