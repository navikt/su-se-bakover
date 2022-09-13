package no.nav.su.se.bakover.service.statistikk

import no.nav.su.se.bakover.domain.statistikk.Statistikkhendelse

interface StatistikkService {
    fun publiser(statistikk: Statistikk)
}

interface EventObserver {
    fun handle(event: Statistikkhendelse)
}

fun List<EventObserver>.notify(event: Statistikkhendelse) {
    this.forEach { observer ->
        observer.handle(event)
    }
}
