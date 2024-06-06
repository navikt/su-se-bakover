package no.nav.su.se.bakover.common.domain.backoff

import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.time.Clock

/**
 * Oversikt over antall feil og tidspunkt for siste feil.
 * Typisk brukt for å vurdere om en operasjon skal prøves på nytt.
 */
data class Failures(
    val count: Long,
    val last: Tidspunkt?,
) {
    fun inc(clock: Clock): Failures {
        return Failures(
            count = count + 1,
            last = Tidspunkt.now(clock),
        )
    }

    companion object {
        val EMPTY = Failures(0, null)
    }
}
