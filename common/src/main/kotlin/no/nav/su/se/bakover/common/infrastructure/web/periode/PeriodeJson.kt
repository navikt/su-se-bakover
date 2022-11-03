package no.nav.su.se.bakover.common.infrastructure.web.periode

import arrow.core.Either
import no.nav.su.se.bakover.common.periode.DatoIntervall
import no.nav.su.se.bakover.common.periode.Måned
import no.nav.su.se.bakover.common.periode.Periode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class PeriodeJson(
    val fraOgMed: String,
    val tilOgMed: String,
) {

    /** @throws IllegalArgumentException dersom [fraOgMed] eller [tilOgMed] ikke kan parses til [LocalDate] */
    fun toPeriode(): Periode {
        return Periode.create(LocalDate.parse(fraOgMed), LocalDate.parse(tilOgMed))
    }

    /** @throws IllegalArgumentException dersom [fraOgMed] eller [tilOgMed] ikke kan parses til [Måned] */
    fun tilMåned(): Måned {
        return Måned.fra(LocalDate.parse(fraOgMed), LocalDate.parse(tilOgMed))
    }

    fun tryToPeriode(): Either<Periode.UgyldigPeriode, Periode> {
        return Periode.tryCreate(LocalDate.parse(fraOgMed), LocalDate.parse(tilOgMed))
    }

    fun toDatoIntervall(): DatoIntervall {
        return DatoIntervall(LocalDate.parse(fraOgMed), LocalDate.parse(tilOgMed))
    }

    companion object {
        fun Periode.toJson() =
            PeriodeJson(fraOgMed.format(DateTimeFormatter.ISO_DATE), tilOgMed.format(DateTimeFormatter.ISO_DATE))

        fun DatoIntervall.toJson() =
            PeriodeJson(fraOgMed.format(DateTimeFormatter.ISO_DATE), tilOgMed.format(DateTimeFormatter.ISO_DATE))
    }
}
