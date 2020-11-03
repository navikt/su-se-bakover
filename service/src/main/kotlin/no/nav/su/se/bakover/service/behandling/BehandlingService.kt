package no.nav.su.se.bakover.service.behandling

import arrow.core.Either
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.NavIdentBruker.Attestant
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.beregning.fradrag.IFradrag
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import java.time.LocalDate
import java.util.UUID

interface BehandlingService {
    fun hentBehandling(behandlingId: UUID): Either<FantIkkeBehandling, Behandling>
    fun underkjenn(
        begrunnelse: String,
        attestant: Attestant,
        behandling: Behandling
    ): Either<Behandling.KunneIkkeUnderkjenne, Behandling>

    fun oppdaterBehandlingsinformasjon(behandlingId: UUID, behandlingsinformasjon: Behandlingsinformasjon): Behandling
    fun opprettBeregning(
        behandlingId: UUID,
        fraOgMed: LocalDate,
        tilOgMed: LocalDate,
        fradrag: List<IFradrag>
    ): Behandling

    fun simuler(behandlingId: UUID, saksbehandler: NavIdentBruker): Either<SimuleringFeilet, Behandling>
    fun sendTilAttestering(
        sakId: UUID,
        behandlingId: UUID,
        saksbehandler: Saksbehandler
    ): Either<KunneIkkeSendeTilAttestering, Behandling>

    fun iverksett(
        behandlingId: UUID,
        attestant: Attestant
    ): Either<Behandling.KunneIkkeIverksetteBehandling, Behandling>

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
}
sealed class KunneIkkeSendeTilAttestering() {
    object UgyldigKombinasjonSakOgBehandling : KunneIkkeSendeTilAttestering()
    object KunneIkkeFinneAktørId : KunneIkkeSendeTilAttestering()
    object InternFeil : KunneIkkeSendeTilAttestering()
}
