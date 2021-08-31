package no.nav.su.se.bakover.domain.oppdrag.avstemming

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling

sealed class Avstemming {
    abstract val id: UUID30
    abstract val opprettet: Tidspunkt
    abstract val fraOgMed: Tidspunkt
    abstract val tilOgMed: Tidspunkt
    abstract val utbetalinger: List<Utbetaling.OversendtUtbetaling>
    abstract val avstemmingXmlRequest: String?

    data class Grensesnittavstemming(
        override val id: UUID30 = UUID30.randomUUID(),
        override val opprettet: Tidspunkt = Tidspunkt.now(),
        override val fraOgMed: Tidspunkt,
        override val tilOgMed: Tidspunkt,
        override val utbetalinger: List<Utbetaling.OversendtUtbetaling>,
        override val avstemmingXmlRequest: String? = null
    ) : Avstemming()

    data class Konsistensavstemming(
        override val id: UUID30 = UUID30.randomUUID(),
        override val opprettet: Tidspunkt = Tidspunkt.now(),
        override val fraOgMed: Tidspunkt,
        override val tilOgMed: Tidspunkt,
        override val utbetalinger: List<Utbetaling.OversendtUtbetaling>,
        override val avstemmingXmlRequest: String? = null
    ) : Avstemming()
}
