package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.Either
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import java.util.UUID

interface FerdigstillSøknadsbehandingIverksettingService {
    fun hentBehandlingForUtbetaling(utbetalingId: UUID30): Søknadsbehandling.Iverksatt.Innvilget?

    fun ferdigstillInnvilgelse(søknadsbehandling: Søknadsbehandling.Iverksatt.Innvilget)

    fun opprettManglendeJournalpostOgBrevdistribusjon(): OpprettManglendeJournalpostOgBrevdistribusjonResultat

    sealed class KunneIkkeFerdigstilleInnvilgelse {
        object FikkIkkeHentetSaksbehandlerEllerAttestant : KunneIkkeFerdigstilleInnvilgelse()
        object KunneIkkeOppretteJournalpost : KunneIkkeFerdigstilleInnvilgelse()
        object KunneIkkeDistribuereBrev : KunneIkkeFerdigstilleInnvilgelse()
        object KunneIkkeOppretteOppgave : KunneIkkeFerdigstilleInnvilgelse()
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
