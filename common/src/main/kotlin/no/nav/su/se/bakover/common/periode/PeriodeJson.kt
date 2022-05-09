package no.nav.su.se.bakover.common.periode

import arrow.core.Either
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class PeriodeJson(
    val fraOgMed: String,
    val tilOgMed: String
) {

    /** @throws IllegalArgumentException dersom [fraOgMed] eller [tilOgMed] ikke kan parses til [LocalDate] */
    fun toPeriode(): Periode {
        return Periode.create(LocalDate.parse(fraOgMed), LocalDate.parse(tilOgMed))
    }

    /** @throws IllegalArgumentException dersom [fraOgMed] eller [tilOgMed] ikke kan parses til [Måned] */
    fun tilMåned(): Måned {
        return Måned(LocalDate.parse(fraOgMed), LocalDate.parse(tilOgMed))
    }

    fun tryToPeriode(): Either<Periode.UgyldigPeriode, Periode> {
        return Periode.tryCreate(LocalDate.parse(fraOgMed), LocalDate.parse(tilOgMed))
    }

    companion object {
        fun Periode.toJson() = PeriodeJson(fraOgMed.format(DateTimeFormatter.ISO_DATE), tilOgMed.format(DateTimeFormatter.ISO_DATE))
    }
}
