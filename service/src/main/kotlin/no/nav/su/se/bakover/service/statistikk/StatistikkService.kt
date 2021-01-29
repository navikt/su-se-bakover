package no.nav.su.se.bakover.service.statistikk

import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.Søknadsbehandling
import no.nav.su.se.bakover.service.behandling.IverksattBehandling

interface StatistikkService {
    fun publiser(statistikk: Statistikk)
}

interface EventObserver {
    fun handle(event: Event)
}

sealed class Event {
    sealed class Statistikk : Event() {
        data class SakOpprettet(val sak: Sak) : Statistikk()
        data class BehandlingOpprettet(val behandling: Behandling) : Statistikk()
        data class SøknadsbehandlingOpprettet(val behandling: Søknadsbehandling) : Statistikk()
        data class BehandlingTilAttestering(val behandling: Behandling) : Statistikk()
        data class BehandlingAttesteringUnderkjent(val behandling: Behandling) : Statistikk()
        data class SøknadsbehandlingUnderkjent(val behandling: Søknadsbehandling.Underkjent) : Statistikk()
        data class BehandlingIverksatt(val behandling: IverksattBehandling) : Statistikk()
    }
}
