package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.Either
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.søknadsbehandling.BehandlingsStatus
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import java.util.UUID

interface SøknadsbehandlingService {
    fun opprett(request: OpprettSøknadsbehandlingRequest): Either<KunneIkkeOppretteSøknadsbehandling, Søknadsbehandling>
    fun vilkårsvurder(request: OppdaterSøknadsbehandlingsinformasjonRequest): Either<KunneIkkeOppdatereBehandlingsinformasjon, Søknadsbehandling>
    fun beregn(request: OpprettBeregningRequest): Either<KunneIkkeBeregne, Søknadsbehandling>
    fun simuler(request: OpprettSimuleringRequest): Either<KunneIkkeSimulereBehandling, Søknadsbehandling>
    fun sendTilAttestering(request: SendTilAttesteringRequest): Either<KunneIkkeSendeTilAttestering, Søknadsbehandling>
    fun underkjenn(request: UnderkjennSøknadsbehandlingRequest): Either<KunneIkkeUnderkjenneBehandling, Søknadsbehandling>
    fun iverksett(request: IverksettSøknadsbehandlingRequest): Either<KunneIkkeIverksetteBehandling, Søknadsbehandling>
    fun brev(request: OpprettBrevRequest): Either<KunneIkkeLageBrevutkast, ByteArray>
    fun hent(request: HentBehandlingRequest): Either<FantIkkeBehandling, Søknadsbehandling>
}

sealed class KunneIkkeLageBrevutkast {
    data class KanIkkeLageBrevutkastForStatus(val status: BehandlingsStatus) : KunneIkkeLageBrevutkast()
    object FantIkkeBehandling : KunneIkkeLageBrevutkast()
    object KunneIkkeLageBrev : KunneIkkeLageBrevutkast()
    object FantIkkePerson : KunneIkkeLageBrevutkast()
    object FikkIkkeHentetSaksbehandlerEllerAttestant : KunneIkkeLageBrevutkast()
}

object FantIkkeBehandling

sealed class KunneIkkeOppretteSøknadsbehandling {
    object FantIkkeSøknad : KunneIkkeOppretteSøknadsbehandling()
    object SøknadManglerOppgave : KunneIkkeOppretteSøknadsbehandling()
    object SøknadErLukket : KunneIkkeOppretteSøknadsbehandling()
    object SøknadHarAlleredeBehandling : KunneIkkeOppretteSøknadsbehandling()
}

sealed class KunneIkkeBeregne {
    object FantIkkeBehandling : KunneIkkeBeregne()
}

sealed class KunneIkkeSimulereBehandling {
    object KunneIkkeSimulere : KunneIkkeSimulereBehandling()
    object FantIkkeBehandling : KunneIkkeSimulereBehandling()
}

sealed class KunneIkkeSendeTilAttestering {
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
    object FantIkkeBehandling : KunneIkkeIverksetteBehandling()
    object FantIkkePerson : KunneIkkeIverksetteBehandling()
    object FikkIkkeHentetSaksbehandlerEllerAttestant : KunneIkkeIverksetteBehandling()
}

sealed class KunneIkkeOppdatereBehandlingsinformasjon {
    object FantIkkeBehandling : KunneIkkeOppdatereBehandlingsinformasjon()
}

data class OpprettSøknadsbehandlingRequest(
    val søknadId: UUID
)

data class OppdaterSøknadsbehandlingsinformasjonRequest(
    val behandlingId: UUID,
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val behandlingsinformasjon: Behandlingsinformasjon
)

data class OpprettBeregningRequest(
    val behandlingId: UUID,
    val periode: Periode,
    val fradrag: List<Fradrag>
)

data class OpprettSimuleringRequest(
    val behandlingId: UUID,
    val saksbehandler: NavIdentBruker.Saksbehandler
)

data class SendTilAttesteringRequest(
    val behandlingId: UUID,
    val saksbehandler: NavIdentBruker.Saksbehandler
)

data class UnderkjennSøknadsbehandlingRequest(
    val behandlingId: UUID,
    val attestering: Attestering.Underkjent
)

data class IverksettSøknadsbehandlingRequest(
    val behandlingId: UUID,
    val attestering: Attestering
)

data class OpprettBrevRequest(
    val behandlingId: UUID
)

data class HentBehandlingRequest(
    val behandlingId: UUID
)
