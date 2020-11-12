package no.nav.su.se.bakover.service.behandling

import arrow.core.Either
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.NavIdentBruker.Attestant
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import java.time.LocalDate
import java.util.UUID

interface BehandlingService {
    fun hentBehandling(behandlingId: UUID): Either<FantIkkeBehandling, Behandling>
    fun underkjenn(
        behandlingId: UUID,
        attestant: Attestant,
        begrunnelse: String
    ): Either<KunneIkkeUnderkjenneBehandling, Behandling>

    fun oppdaterBehandlingsinformasjon(behandlingId: UUID, behandlingsinformasjon: Behandlingsinformasjon): Behandling
    fun opprettBeregning(
        behandlingId: UUID,
        fraOgMed: LocalDate,
        tilOgMed: LocalDate,
        fradrag: List<Fradrag>
    ): Behandling

    fun simuler(behandlingId: UUID, saksbehandler: NavIdentBruker): Either<SimuleringFeilet, Behandling>
    fun sendTilAttestering(
        behandlingId: UUID,
        saksbehandler: Saksbehandler
    ): Either<KunneIkkeSendeTilAttestering, Behandling>

    fun iverksett(
        behandlingId: UUID,
        attestant: Attestant
    ): Either<KunneIkkeIverksetteBehandling, Behandling>

    fun opprettSøknadsbehandling(søknadId: UUID): Either<KunneIkkeOppretteSøknadsbehandling, Behandling>
    fun lagBrevutkast(behandlingId: UUID): Either<KunneIkkeLageBrevutkast, ByteArray>
}

sealed class KunneIkkeLageBrevutkast {
    object FantIkkeBehandling : KunneIkkeLageBrevutkast()
    object KunneIkkeLageBrev : KunneIkkeLageBrevutkast()
}

object FantIkkeBehandling
sealed class KunneIkkeOppretteSøknadsbehandling {
    object FantIkkeSøknad : KunneIkkeOppretteSøknadsbehandling()
    object SøknadManglerOppgave : KunneIkkeOppretteSøknadsbehandling()
    object SøknadErLukket : KunneIkkeOppretteSøknadsbehandling()
    object SøknadHarAlleredeBehandling : KunneIkkeOppretteSøknadsbehandling()
}
sealed class KunneIkkeSendeTilAttestering() {
    object FantIkkeBehandling : KunneIkkeSendeTilAttestering()
    object KunneIkkeFinneAktørId : KunneIkkeSendeTilAttestering()
    object KunneIkkeOppretteOppgave : KunneIkkeSendeTilAttestering()
}

sealed class KunneIkkeUnderkjenneBehandling {
    object FantIkkeBehandling : KunneIkkeUnderkjenneBehandling()
    object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeUnderkjenneBehandling()
    object KunneIkkeOppretteOppgave : KunneIkkeUnderkjenneBehandling()
    object FantIkkeAktørId : KunneIkkeUnderkjenneBehandling()
}

sealed class KunneIkkeIverksetteBehandling {
    object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeIverksetteBehandling()
    object KunneIkkeUtbetale : KunneIkkeIverksetteBehandling()
    object KunneIkkeKontrollsimulere : KunneIkkeIverksetteBehandling()
    object SimuleringHarBlittEndretSidenSaksbehandlerSimulerte : KunneIkkeIverksetteBehandling()
    object KunneIkkeJournalføreBrev : KunneIkkeIverksetteBehandling()
    object KunneIkkeDistribuereBrev : KunneIkkeIverksetteBehandling()
    object FantIkkeBehandling : KunneIkkeIverksetteBehandling()
    object KunneIkkeLukkeOppgave : KunneIkkeIverksetteBehandling()
}
