package no.nav.su.se.bakover.service.avstemming

import arrow.core.Either
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming

interface AvstemmingService {
    fun avstemming(): Either<AvstemmingFeilet, Avstemming>
}
