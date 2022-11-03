package no.nav.su.se.bakover.service.søknad

import arrow.core.Either
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.application.journal.JournalpostId
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadinnhold.SøknadInnhold
import java.util.UUID

interface SøknadService {
    fun nySøknad(søknadInnhold: SøknadInnhold, identBruker: NavIdentBruker): Either<KunneIkkeOppretteSøknad, Pair<Saksnummer, Søknad>>
    fun persisterSøknad(søknad: Søknad.Journalført.MedOppgave.Lukket, sessionContext: SessionContext)
    fun hentSøknad(søknadId: UUID): Either<FantIkkeSøknad, Søknad>
    fun hentSøknadPdf(søknadId: UUID): Either<KunneIkkeLageSøknadPdf, ByteArray>
    fun opprettManglendeJournalpostOgOppgave(): OpprettManglendeJournalpostOgOppgaveResultat
}

object FantIkkeSøknad {
    override fun toString() = this::class.simpleName!!
}

sealed class KunneIkkeOppretteSøknad {
    object FantIkkePerson : KunneIkkeOppretteSøknad()
    object SøknadsinnsendingIkkeTillatt : KunneIkkeOppretteSøknad()
}

sealed class KunneIkkeLageSøknadPdf {
    object FantIkkeSøknad : KunneIkkeLageSøknadPdf()
    object KunneIkkeLagePdf : KunneIkkeLageSøknadPdf()
    object FantIkkePerson : KunneIkkeLageSøknadPdf()
    object FantIkkeSak : KunneIkkeLageSøknadPdf()
}

data class OpprettManglendeJournalpostOgOppgaveResultat(
    val journalpostResultat: List<Either<KunneIkkeOppretteJournalpost, Søknad.Journalført.UtenOppgave>>,
    val oppgaveResultat: List<Either<KunneIkkeOppretteOppgave, Søknad.Journalført.MedOppgave>>,
) {
    fun harFeil(): Boolean = journalpostResultat.mapNotNull { it.swap().orNull() }.isNotEmpty() ||
        oppgaveResultat.mapNotNull { it.swap().orNull() }.isNotEmpty()
}
data class KunneIkkeOppretteJournalpost(val sakId: UUID, val søknadId: UUID, val grunn: String)
data class KunneIkkeOppretteOppgave(val sakId: UUID, val søknadId: UUID, val journalpostId: JournalpostId, val grunn: String)
