package no.nav.su.se.bakover.domain.statistikk

interface StatistikkEventObserver {
    /**
     * Kaster ikke exceptions.
     */
    fun handle(event: StatistikkEvent)
}

fun List<StatistikkEventObserver>.notify(event: StatistikkEvent) {
    this.forEach {
            observer ->
        observer.handle(event)
    }
}
