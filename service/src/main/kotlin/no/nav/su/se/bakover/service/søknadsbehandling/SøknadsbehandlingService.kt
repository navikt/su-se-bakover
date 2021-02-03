package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.Either
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.service.behandling.KunneIkkeBeregne
import no.nav.su.se.bakover.service.behandling.KunneIkkeIverksetteBehandling
import no.nav.su.se.bakover.service.behandling.KunneIkkeOppdatereBehandlingsinformasjon
import no.nav.su.se.bakover.service.behandling.KunneIkkeOppretteSøknadsbehandling
import no.nav.su.se.bakover.service.behandling.KunneIkkeSendeTilAttestering
import no.nav.su.se.bakover.service.behandling.KunneIkkeSimulereBehandling
import no.nav.su.se.bakover.service.behandling.KunneIkkeUnderkjenneBehandling
import java.util.UUID

interface SøknadsbehandlingService {
    fun opprett(request: OpprettSøknadsbehandlingRequest): Either<KunneIkkeOppretteSøknadsbehandling, Søknadsbehandling>
    fun vilkårsvurder(request: OppdaterSøknadsbehandlingsinformasjonRequest): Either<KunneIkkeOppdatereBehandlingsinformasjon, Søknadsbehandling>
    fun beregn(request: OpprettBeregningRequest): Either<KunneIkkeBeregne, Søknadsbehandling>
    fun simuler(request: OpprettSimuleringRequest): Either<KunneIkkeSimulereBehandling, Søknadsbehandling>
    fun sendTilAttestering(request: SendTilAttesteringRequest): Either<KunneIkkeSendeTilAttestering, Søknadsbehandling>
    fun underkjenn(request: UnderkjennSøknadsbehandlingRequest): Either<KunneIkkeUnderkjenneBehandling, Søknadsbehandling>
    fun iverksett(request: IverksettSøknadsbehandlingRequest): Either<KunneIkkeIverksetteBehandling, Søknadsbehandling>
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
