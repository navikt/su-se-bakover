package no.nav.su.se.bakover.domain.oppdrag.avstemming

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.endOfDay
import no.nav.su.se.bakover.common.startOfDay
import java.time.LocalDate
import kotlin.math.pow

data class Avstemmingsnøkkel(
    val tidspunkt: Tidspunkt = Tidspunkt.now()
) {
    val nøkkel = generer(tidspunkt)

    companion object {

        private fun generer(tidspunkt: Tidspunkt = Tidspunkt.now()) =
            tidspunkt.instant.epochSecond * 10.0.pow(9).toLong() + tidspunkt.nano

        fun periode(fraOgMed: LocalDate, tilOgMed: LocalDate) =
            generer(fraOgMed.startOfDay())..generer(tilOgMed.endOfDay())
    }

    override fun toString() = nøkkel.toString()
}
