package no.nav.su.se.bakover.domain.oppdrag.avstemming

import no.nav.su.se.bakover.common.MicroInstant
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling

data class Avstemming(
    val id: UUID30 = UUID30.randomUUID(),
    val opprettet: MicroInstant = now(),
    val fom: MicroInstant,
    val tom: MicroInstant,
    val utbetalinger: List<Utbetaling>,
    val avstemmingXmlRequest: String? = null
) {
    fun updateUtbetalinger() = utbetalinger.forEach { it.addAvstemmingId(id) }
}
