package no.nav.su.se.bakover.domain.oppdrag.avstemming

import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.toTidspunkt
import java.time.Instant
import java.util.concurrent.TimeUnit

data class Avstemmingsnøkkel(
    val opprettet: Tidspunkt,
) : Comparable<Avstemmingsnøkkel> {

    @JsonProperty
    private val nøkkel: String = generer(opprettet)

    init {
        // Begrenser oss til oppdragssystemets makslengde
        require(nøkkel.length <= 30)
    }

    companion object {
        private val nanoPrecision = 1_000_000_000.toBigInteger()

        private fun generer(tidspunkt: Tidspunkt): String =
            (
                TimeUnit.NANOSECONDS.convert(tidspunkt.instant.epochSecond, TimeUnit.SECONDS)
                    .toBigInteger() + tidspunkt.instant.nano.toBigInteger()
                ).toString()

        fun fromString(value: String): Avstemmingsnøkkel {
            val bigIntegerValue = value.toBigInteger()
            val seconds = bigIntegerValue.divide(nanoPrecision).toLong()
            val nanos = bigIntegerValue.mod(nanoPrecision).toLong()
            return Avstemmingsnøkkel(Instant.ofEpochSecond(seconds, nanos).toTidspunkt())
        }
    }

    override fun toString() = nøkkel
    override fun compareTo(other: Avstemmingsnøkkel) = opprettet.compareTo(other.opprettet.instant)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Avstemmingsnøkkel

        if (opprettet != other.opprettet) return false
        if (nøkkel != other.nøkkel) return false

        return true
    }

    override fun hashCode(): Int {
        var result = opprettet.hashCode()
        result = 31 * result + nøkkel.hashCode()
        return result
    }
}
