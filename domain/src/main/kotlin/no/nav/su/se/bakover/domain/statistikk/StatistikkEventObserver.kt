package no.nav.su.se.bakover.domain.statistikk

import arrow.core.Nel
import no.nav.su.se.bakover.common.persistence.SessionContext

interface StatistikkEventObserver {
    fun handle(event: StatistikkEvent, sessionContext: SessionContext? = null)
}

fun List<StatistikkEventObserver>.notify(event: StatistikkEvent, sessionContext: SessionContext) {
    this.forEach { observer ->
        observer.handle(event, sessionContext)
    }
}

fun List<StatistikkEventObserver>.notify(events: Nel<StatistikkEvent>, sessionContext: SessionContext) {
    events.forEach { event ->
        this.notify(event, sessionContext)
    }
}
