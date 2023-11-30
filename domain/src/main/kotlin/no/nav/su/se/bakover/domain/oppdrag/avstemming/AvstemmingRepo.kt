package no.nav.su.se.bakover.domain.oppdrag.avstemming

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.tid.Tidspunkt
import økonomi.domain.Fagområde
import økonomi.domain.utbetaling.Utbetaling
import java.time.LocalDate

interface AvstemmingRepo {
    fun opprettGrensesnittsavstemming(avstemming: Avstemming.Grensesnittavstemming)
    fun opprettKonsistensavstemming(avstemming: Avstemming.Konsistensavstemming.Ny)
    fun hentGrensesnittsavstemming(avstemmingId: UUID30): Avstemming.Grensesnittavstemming?
    fun hentKonsistensavstemming(avstemmingId: UUID30): Avstemming.Konsistensavstemming.Fullført?
    fun oppdaterUtbetalingerEtterGrensesnittsavstemming(avstemming: Avstemming.Grensesnittavstemming)
    fun hentSisteGrensesnittsavstemming(fagområde: Fagområde): Avstemming.Grensesnittavstemming?

    fun hentUtbetalingerForGrensesnittsavstemming(
        fraOgMed: Tidspunkt,
        tilOgMed: Tidspunkt,
        fagområde: Fagområde,
    ): List<Utbetaling.OversendtUtbetaling>

    fun hentUtbetalingerForKonsistensavstemming(
        løpendeFraOgMed: Tidspunkt,
        opprettetTilOgMed: Tidspunkt,
        fagområde: Fagområde,
    ): List<Utbetaling.OversendtUtbetaling>

    fun konsistensavstemmingUtførtForOgPåDato(
        dato: LocalDate,
        fagområde: Fagområde,
    ): Boolean
}
