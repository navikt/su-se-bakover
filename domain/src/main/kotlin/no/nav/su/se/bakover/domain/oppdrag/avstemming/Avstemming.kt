package no.nav.su.se.bakover.domain.oppdrag.avstemming

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling

data class Avstemming(
    val id: UUID30 = UUID30.randomUUID(),
    val opprettet: Tidspunkt = now(),
    val fraOgMed: Tidspunkt,
    val tilOgMed: Tidspunkt,
    val utbetalinger: List<Utbetaling.OversendtUtbetaling>,
    val avstemmingXmlRequest: String? = null
)
