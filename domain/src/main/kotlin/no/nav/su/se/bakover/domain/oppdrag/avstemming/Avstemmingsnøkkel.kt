package no.nav.su.se.bakover.domain.oppdrag.avstemming

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.endOfDay
import no.nav.su.se.bakover.common.startOfDay
import java.time.LocalDate
import kotlin.math.pow

object Avstemmingsnøkkel {
    // Avoid Utils.now() because of truncation to millis.
    fun generer(tidspunkt: Tidspunkt = Tidspunkt.now()) =
        tidspunkt.instant.epochSecond * 10.0.pow(9).toLong() + tidspunkt.nano

    fun periode(fraOgMed: LocalDate, tilOgMed: LocalDate) = generer(fraOgMed.startOfDay())..generer(tilOgMed.endOfDay())
}
