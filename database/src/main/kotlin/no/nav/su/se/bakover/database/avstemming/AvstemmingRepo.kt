package no.nav.su.se.bakover.database.avstemming

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming

interface AvstemmingRepo {
    fun opprettAvstemming(avstemming: Avstemming): Avstemming
    fun oppdaterAvstemteUtbetalinger(avstemming: Avstemming)
    fun hentSisteAvstemming(): Avstemming?
    fun hentUtbetalingerForAvstemming(fraOgMed: Tidspunkt, tilOgMed: Tidspunkt): List<Utbetaling.OversendtUtbetaling>
}
