package no.nav.su.se.bakover.domain.statistikk

import arrow.core.Nel

interface StatistikkEventObserver {
    /**
     * Kaster ikke exceptions.
     */
    fun handle(event: StatistikkEvent)
}

fun List<StatistikkEventObserver>.notify(event: StatistikkEvent) {
    this.forEach { observer ->
        observer.handle(event)
    }
}

fun List<StatistikkEventObserver>.notify(events: Nel<StatistikkEvent>) {
    events.forEach { event ->
        this.notify(event)
    }
}
