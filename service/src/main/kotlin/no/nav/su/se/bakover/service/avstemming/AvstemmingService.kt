package no.nav.su.se.bakover.service.avstemming

import arrow.core.Either
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming

interface AvstemmingService {
    fun grensesnittsavstemming(): Either<AvstemmingFeilet, Avstemming.Grensesnittavstemming>
    fun grensesnittsavstemming(fraOgMed: Tidspunkt, tilOgMed: Tidspunkt): Either<AvstemmingFeilet, Avstemming.Grensesnittavstemming>
    fun konsistensavstemming(fraOgMed: Tidspunkt, tilOgMed: Tidspunkt): Either<AvstemmingFeilet, Avstemming.Konsistensavstemming>
}
