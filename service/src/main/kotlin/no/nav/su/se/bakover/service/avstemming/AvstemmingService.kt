package no.nav.su.se.bakover.service.avstemming

import arrow.core.Either
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Fagområde
import java.time.LocalDate

interface AvstemmingService {
    fun grensesnittsavstemming(fagområde: Fagområde): Either<AvstemmingFeilet, Avstemming.Grensesnittavstemming>

    fun grensesnittsavstemming(
        fraOgMed: Tidspunkt,
        tilOgMed: Tidspunkt,
        fagområde: Fagområde,
    ): Either<AvstemmingFeilet, Avstemming.Grensesnittavstemming>

    fun konsistensavstemming(
        løpendeFraOgMed: LocalDate,
        fagområde: Fagområde,
    ): Either<AvstemmingFeilet, Avstemming.Konsistensavstemming.Ny>

    fun konsistensavstemmingUtførtForOgPåDato(
        dato: LocalDate,
        fagområde: Fagområde,
    ): Boolean
}
