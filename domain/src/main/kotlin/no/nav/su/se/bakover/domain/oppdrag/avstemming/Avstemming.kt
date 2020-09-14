package no.nav.su.se.bakover.domain.oppdrag.avstemming

import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import java.time.Instant
import java.util.UUID

data class Avstemming(
    val id: UUID = UUID.randomUUID(),
    val opprettet: Instant = now(),
    val fom: Instant,
    val tom: Instant,
    val utbetalinger: List<Utbetaling>,
    val avstemmingXmlRequest: String? = null
) {
    fun updateUtbetalinger() = utbetalinger.forEach { it.addAvstemmingId(id) }
}
