package no.nav.su.se.bakover.service.søknad

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.SøknadInnhold
import java.util.UUID

interface SøknadService {
    fun nySøknad(
        søknadInnhold: SøknadInnhold,
        identBruker: NavIdentBruker,
    ): Either<KunneIkkeOppretteSøknad, Pair<Saksnummer, Søknad>>

    fun persisterSøknad(søknad: Søknad.Journalført.MedOppgave.Lukket, sessionContext: SessionContext)
    fun hentSøknad(søknadId: UUID): Either<FantIkkeSøknad, Søknad>
    fun hentSøknadPdf(søknadId: UUID): Either<KunneIkkeLageSøknadPdf, PdfA>
    fun opprettManglendeJournalpostOgOppgave(): OpprettManglendeJournalpostOgOppgaveResultat
}

data object FantIkkeSøknad

sealed interface KunneIkkeOppretteSøknad {
    data object FantIkkePerson : KunneIkkeOppretteSøknad
    data object SøknadsinnsendingIkkeTillatt : KunneIkkeOppretteSøknad
    data object FeilSakstype : KunneIkkeOppretteSøknad
}

sealed interface KunneIkkeLageSøknadPdf {
    data object FantIkkeSøknad : KunneIkkeLageSøknadPdf
    data object KunneIkkeLagePdf : KunneIkkeLageSøknadPdf
    data object FantIkkePerson : KunneIkkeLageSøknadPdf
    data object FantIkkeSak : KunneIkkeLageSøknadPdf
}

data class OpprettManglendeJournalpostOgOppgaveResultat(
    val journalpostResultat: List<Either<KunneIkkeOppretteJournalpost, Søknad.Journalført.UtenOppgave>>,
    val oppgaveResultat: List<Either<KunneIkkeOppretteOppgave, Søknad.Journalført.MedOppgave>>,
)

data class KunneIkkeOppretteJournalpost(val sakId: UUID, val søknadId: UUID, val grunn: String)
data class KunneIkkeOppretteOppgave(
    val sakId: UUID,
    val søknadId: UUID,
    val journalpostId: JournalpostId,
    val grunn: String,
)
