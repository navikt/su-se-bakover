package no.nav.su.se.bakover.domain.oppdrag.avstemming

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.endOfDay
import no.nav.su.se.bakover.common.startOfDay
import java.time.LocalDate
import kotlin.math.pow

data class Avstemmingsnøkkel(
    val opprettet: Tidspunkt = Tidspunkt.now()
) : Comparable<Avstemmingsnøkkel>{
    private val nøkkel: Long = generer(opprettet)

    companion object {

        private fun generer(tidspunkt: Tidspunkt = Tidspunkt.now()) : Long =
            tidspunkt.instant.epochSecond * 10.0.pow(9).toLong() + tidspunkt.nano
    }

    override fun toString() = nøkkel.toString()
    override fun compareTo(other: Avstemmingsnøkkel) = opprettet.compareTo(other.opprettet.instant)
}
