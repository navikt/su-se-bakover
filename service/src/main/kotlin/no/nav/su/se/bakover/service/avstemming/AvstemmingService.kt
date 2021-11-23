package no.nav.su.se.bakover.service.avstemming

import arrow.core.Either
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import java.time.LocalDate

interface AvstemmingService {
    fun grensesnittsavstemming(): Either<AvstemmingFeilet, Avstemming.Grensesnittavstemming>
    fun grensesnittsavstemming(
        fraOgMed: Tidspunkt,
        tilOgMed: Tidspunkt,
    ): Either<AvstemmingFeilet, Avstemming.Grensesnittavstemming>

    fun konsistensavstemming(løpendeFraOgMed: LocalDate): Either<AvstemmingFeilet, Avstemming.Konsistensavstemming.Ny>
    fun konsistensavstemmingUtførtForOgPåDato(dato: LocalDate): Boolean
}
