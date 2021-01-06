package no.nav.su.se.bakover.service.statistikk

import no.nav.su.se.bakover.domain.Sak

interface StatistikkService {
    fun publiser(statistikk: Statistikk)
}

interface EventObserver {
    fun handle(event: Event)
}

sealed class Event {
    sealed class Statistikk : Event() {
        data class SakOpprettet(val sak: Sak) : Statistikk()
    }
}
