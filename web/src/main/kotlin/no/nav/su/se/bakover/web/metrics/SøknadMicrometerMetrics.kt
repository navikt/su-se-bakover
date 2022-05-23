package no.nav.su.se.bakover.web.metrics

import no.nav.su.se.bakover.common.metrics.SuMetrics
import no.nav.su.se.bakover.domain.søknad.SøknadMetrics

class SøknadMicrometerMetrics : SøknadMetrics {
    override fun incrementNyCounter(handling: SøknadMetrics.NyHandlinger) {
        SuMetrics.incrementCounter("ny_soknad_counter", handling.name)
    }
}
