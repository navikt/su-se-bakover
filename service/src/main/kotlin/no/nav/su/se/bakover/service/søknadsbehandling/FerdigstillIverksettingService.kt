package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.Either
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.visitor.LagBrevRequestVisitor
import no.nav.su.se.bakover.domain.visitor.Visitable
import java.util.UUID

interface FerdigstillIverksettingService {
    fun ferdigstillIverksetting(utbetalingId: UUID30)
    fun opprettManglendeJournalpostOgBrevdistribusjon(): OpprettManglendeJournalpostOgBrevdistribusjonResultat
    fun lagBrevRequest(visitable: Visitable<LagBrevRequestVisitor>): Either<KunneIkkeFerdigstilleInnvilgelse, LagBrevRequest>
    fun lukkOppgave(oppgaveId: OppgaveId): Either<KunneIkkeFerdigstilleInnvilgelse.KunneIkkeLukkeOppgave, Unit>

    sealed class KunneIkkeFerdigstilleInnvilgelse {
        object FikkIkkeHentetSaksbehandlerEllerAttestant : KunneIkkeFerdigstilleInnvilgelse()
        object KunneIkkeOppretteJournalpost : KunneIkkeFerdigstilleInnvilgelse()
        object KunneIkkeDistribuereBrev : KunneIkkeFerdigstilleInnvilgelse()
        object KunneIkkeLukkeOppgave : KunneIkkeFerdigstilleInnvilgelse()
        object FantIkkePerson : KunneIkkeFerdigstilleInnvilgelse()
    }

    data class KunneIkkeOppretteJournalpostForIverksetting(
        val sakId: UUID,
        val behandlingId: UUID,
        val grunn: String,
    )

    data class OpprettetJournalpostForIverksetting(
        val sakId: UUID,
        val behandlingId: UUID,
        val journalpostId: JournalpostId,
    )

    data class BestiltBrev(
        val sakId: UUID,
        val behandlingId: UUID,
        val journalpostId: JournalpostId,
        val brevbestillingId: BrevbestillingId,
    )

    data class KunneIkkeBestilleBrev(
        val sakId: UUID,
        val behandlingId: UUID,
        val journalpostId: JournalpostId?,
        val grunn: String,
    )

    data class OpprettManglendeJournalpostOgBrevdistribusjonResultat(
        val journalpostresultat: List<Either<KunneIkkeOppretteJournalpostForIverksetting, OpprettetJournalpostForIverksetting>>,
        val brevbestillingsresultat: List<Either<KunneIkkeBestilleBrev, BestiltBrev>>
    ) {
        fun harFeil(): Boolean = journalpostresultat.mapNotNull { it.swap().orNull() }.isNotEmpty() ||
            brevbestillingsresultat.mapNotNull { it.swap().orNull() }.isNotEmpty()
    }
}
