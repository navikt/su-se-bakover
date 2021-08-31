package no.nav.su.se.bakover.database.avstemming

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming

interface AvstemmingRepo {
    fun opprettAvstemming(avstemming: Avstemming): Avstemming
    fun oppdaterUtbetalingerEtterGrensesnittsavstemming(avstemming: Avstemming.Grensesnittavstemming)
    fun hentSisteGrensesnittsavstemming(): Avstemming.Grensesnittavstemming?
    fun hentUtbetalingerForGrensesnittsavstemming(
        fraOgMed: Tidspunkt,
        tilOgMed: Tidspunkt,
    ): List<Utbetaling.OversendtUtbetaling>
}
