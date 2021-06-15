package no.nav.su.se.bakover.domain

import com.fasterxml.jackson.annotation.JsonValue
import java.time.LocalDate
import java.time.Month
import kotlin.math.roundToInt

class Grunnbeløp private constructor(private val multiplier: Double) {
    private val datoToBeløp: Map<LocalDate, Int> = listOfNotNull(
        LocalDate.of(2017, Month.MAY, 1) to 93634,
        LocalDate.of(2018, Month.MAY, 1) to 96883,
        LocalDate.of(2019, Month.MAY, 1) to 99858,
        LocalDate.of(2020, Month.MAY, 1) to 101351,
        LocalDate.of(2021, Month.MAY, 1) to 106399,
    ).toMap()

    fun fraDato(dato: LocalDate): Double = datoToBeløp.entries
        .sortedByDescending { it.key }
        .first { dato.isAfter(it.key) || dato.isEqual(it.key) }.value * multiplier

    fun datoForSisteEndringAvGrunnbeløp(forDato: LocalDate): LocalDate = datoToBeløp.entries
        .sortedByDescending { it.key }
        .first { forDato.isAfter(it.key) || forDato.isEqual(it.key) }.key

    fun alleFraDato(dato: LocalDate): List<Pair<LocalDate, Int>> = datoToBeløp.entries
        .sortedByDescending { it.key }
        // TODO jah: Påkall ingar for å finne en sweetere funksjon enn fold.
        .fold(emptyList<Map.Entry<LocalDate, Int>>()) { acc, entry ->
            if (entry.key.isAfter(dato) || entry.key.isEqual(dato) || acc.none {
                it.key.isBefore(dato) || it.key.isEqual(dato)
            }
            ) acc + entry else acc
        }
        .mapNotNull { it }
        .map { it.key to (it.value * multiplier).roundToInt() }

    companion object {
        val `2,28G` = Grunnbeløp(2.28)
        val `2,48G` = Grunnbeløp(2.48)
        val `1G` = Grunnbeløp(1.0)
        val `0,5G` = Grunnbeløp(0.5)
    }

    @JsonValue
    override fun toString() = multiplier.toString()
}
