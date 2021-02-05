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
    fun opprett(request: OpprettRequest): Either<KunneIkkeOpprette, Søknadsbehandling>
    fun vilkårsvurder(request: VilkårsvurderRequest): Either<KunneIkkeVilkårsvurdere, Søknadsbehandling>
    fun beregn(request: BeregnRequest): Either<KunneIkkeBeregne, Søknadsbehandling>
    fun simuler(request: SimulerRequest): Either<KunneIkkeSimulereBehandling, Søknadsbehandling>
    fun sendTilAttestering(request: SendTilAttesteringRequest): Either<KunneIkkeSendeTilAttestering, Søknadsbehandling>
    fun underkjenn(request: UnderkjennRequest): Either<KunneIkkeUnderkjenne, Søknadsbehandling>
    fun iverksett(request: IverksettRequest): Either<KunneIkkeIverksette, Søknadsbehandling>
    fun brev(request: BrevRequest): Either<KunneIkkeLageBrev, ByteArray>
    fun hent(request: HentRequest): Either<FantIkkeBehandling, Søknadsbehandling>

    data class OpprettRequest(
        val søknadId: UUID
    )

    sealed class KunneIkkeOpprette {
        object FantIkkeSøknad : KunneIkkeOpprette()
        object SøknadManglerOppgave : KunneIkkeOpprette()
        object SøknadErLukket : KunneIkkeOpprette()
        object SøknadHarAlleredeBehandling : KunneIkkeOpprette()
    }

    data class VilkårsvurderRequest(
        val behandlingId: UUID,
        val saksbehandler: NavIdentBruker.Saksbehandler,
        val behandlingsinformasjon: Behandlingsinformasjon
    )

    sealed class KunneIkkeVilkårsvurdere {
        object FantIkkeBehandling : KunneIkkeVilkårsvurdere()
    }

    data class BeregnRequest(
        val behandlingId: UUID,
        val periode: Periode,
        val fradrag: List<Fradrag>
    )

    sealed class KunneIkkeBeregne {
        object FantIkkeBehandling : KunneIkkeBeregne()
    }

    data class SimulerRequest(
        val behandlingId: UUID,
        val saksbehandler: NavIdentBruker.Saksbehandler
    )

    sealed class KunneIkkeSimulereBehandling {
        object KunneIkkeSimulere : KunneIkkeSimulereBehandling()
        object FantIkkeBehandling : KunneIkkeSimulereBehandling()
    }

    data class SendTilAttesteringRequest(
        val behandlingId: UUID,
        val saksbehandler: NavIdentBruker.Saksbehandler
    )

    sealed class KunneIkkeSendeTilAttestering {
        object FantIkkeBehandling : KunneIkkeSendeTilAttestering()
        object KunneIkkeFinneAktørId : KunneIkkeSendeTilAttestering()
        object KunneIkkeOppretteOppgave : KunneIkkeSendeTilAttestering()
    }

    data class UnderkjennRequest(
        val behandlingId: UUID,
        val attestering: Attestering.Underkjent
    )

    sealed class KunneIkkeUnderkjenne {
        object FantIkkeBehandling : KunneIkkeUnderkjenne()
        object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeUnderkjenne()
        object KunneIkkeOppretteOppgave : KunneIkkeUnderkjenne()
        object FantIkkeAktørId : KunneIkkeUnderkjenne()
    }

    data class IverksettRequest(
        val behandlingId: UUID,
        val attestering: Attestering
    )

    sealed class KunneIkkeIverksette {
        object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeIverksette()
        object KunneIkkeUtbetale : KunneIkkeIverksette()
        object KunneIkkeKontrollsimulere : KunneIkkeIverksette()
        object SimuleringHarBlittEndretSidenSaksbehandlerSimulerte : KunneIkkeIverksette()
        object KunneIkkeJournalføreBrev : KunneIkkeIverksette()
        object FantIkkeBehandling : KunneIkkeIverksette()
        object FantIkkePerson : KunneIkkeIverksette()
        object FikkIkkeHentetSaksbehandlerEllerAttestant : KunneIkkeIverksette()
    }

    data class BrevRequest(
        val behandlingId: UUID
    )

    sealed class KunneIkkeLageBrev {
        data class KanIkkeLageBrevutkastForStatus(val status: BehandlingsStatus) : KunneIkkeLageBrev()
        object FantIkkeBehandling : KunneIkkeLageBrev()
        object KunneIkkeLagePDF : KunneIkkeLageBrev()
        object FantIkkePerson : KunneIkkeLageBrev()
        object FikkIkkeHentetSaksbehandlerEllerAttestant : KunneIkkeLageBrev()
    }

    data class HentRequest(
        val behandlingId: UUID
    )

    object FantIkkeBehandling
}
