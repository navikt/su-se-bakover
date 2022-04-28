package no.nav.su.se.bakover.domain

import com.fasterxml.jackson.annotation.JsonValue
import no.nav.su.se.bakover.common.ApplicationConfig
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.Month
import kotlin.math.roundToInt

class Grunnbeløp private constructor(
    private val multiplier: Double,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    private val datoToBeløp: Map<LocalDate, Int> = listOfNotNull(
        LocalDate.of(2017, Month.MAY, 1) to 93634,
        LocalDate.of(2018, Month.MAY, 1) to 96883,
        LocalDate.of(2019, Month.MAY, 1) to 99858,
        LocalDate.of(2020, Month.MAY, 1) to 101351,
        LocalDate.of(2021, Month.MAY, 1) to 106399,
        if (ApplicationConfig.isNotProd()) {
            log.warn("Inkluderer fiktiv G-verdi for 2022. Skal ikke dukke opp i prod!")
            LocalDate.of(2022, Month.MAY, 1) to 106899
        } else null,
    ).toMap()

    fun påDato(dato: LocalDate): Double = datoToBeløp.entries
        .sortedByDescending { it.key }
        .first { dato.isAfter(it.key) || dato.isEqual(it.key) }.value * multiplier

    fun datoForSisteEndringAvGrunnbeløp(forDato: LocalDate): LocalDate = datoToBeløp.entries
        .sortedByDescending { it.key }
        .first { forDato.isAfter(it.key) || forDato.isEqual(it.key) }.key

    fun månedsbeløp(dato: LocalDate): Double = påDato(dato) / 12

    fun heltallPåDato(dato: LocalDate): Int = påDato(dato).roundToInt()

    /**
     * Hent grunnbeløpet * multiplier som er gyldig på gitt dato og alle senere.
     */
    fun gyldigPåDatoOgSenere(dato: LocalDate): List<Pair<LocalDate, Int>> = datoToBeløp.entries
        .sortedByDescending { it.key }
        .fold(emptyList<Map.Entry<LocalDate, Int>>()) { acc, entry ->
            if (entry.key.isAfter(dato) || entry.key.isEqual(dato) || acc.none {
                it.key.isBefore(dato) || it.key.isEqual(dato)
            }
            ) acc + entry else acc
        }
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
