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

        sealed class SøknadsbehandlingStatistikk : Statistikk() {
            abstract val søknadsbehandling: Søknadsbehandling

            data class SøknadsbehandlingOpprettet(override val søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Uavklart) :
                SøknadsbehandlingStatistikk()

            data class SøknadsbehandlingUnderkjent(override val søknadsbehandling: Søknadsbehandling.Underkjent) :
                SøknadsbehandlingStatistikk()

            data class SøknadsbehandlingTilAttestering(override val søknadsbehandling: Søknadsbehandling.TilAttestering) :
                SøknadsbehandlingStatistikk()

            data class SøknadsbehandlingIverksatt(override val søknadsbehandling: Søknadsbehandling.Iverksatt) :
                SøknadsbehandlingStatistikk()
        }
    }
}
