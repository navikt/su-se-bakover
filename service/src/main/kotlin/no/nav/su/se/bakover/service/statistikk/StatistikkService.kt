package no.nav.su.se.bakover.service.statistikk

import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling

interface StatistikkService {
    fun publiser(statistikk: Statistikk)
}

interface EventObserver {
    fun handle(event: Event)
}

sealed class Event {
    sealed class Statistikk : Event() {
        data class SakOpprettet(val sak: Sak) : Statistikk()
        data class SøknadsbehandlingOpprettet(val behandling: Søknadsbehandling) : Statistikk()
        data class SøknadsbehandlingUnderkjent(val behandling: Søknadsbehandling.Underkjent) : Statistikk()
        data class SøknadsbehandlingTilAttestering(val behandling: Søknadsbehandling.TilAttestering) : Statistikk()
        data class SøknadsbehandlingIverksatt(val behandling: Søknadsbehandling.Iverksatt) : Statistikk()
    }
}
