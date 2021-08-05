package no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning

import arrow.core.Either
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal data class PeriodeJson(
    val fraOgMed: String,
    val tilOgMed: String
) {
    fun toPeriode(): Either<Resultat, Periode> {
        return Periode.tryCreate(LocalDate.parse(fraOgMed), LocalDate.parse(tilOgMed)).mapLeft {
            when (it) {
                Periode.UgyldigPeriode.FraOgMedDatoMåVæreFørsteDagIMåneden -> HttpStatusCode.BadRequest.errorJson(
                    "Perioder kan kun starte på første dag i måneden",
                    "ugyldig_periode_fom"
                )
                Periode.UgyldigPeriode.TilOgMedDatoMåVæreSisteDagIMåneden -> HttpStatusCode.BadRequest.errorJson(
                    "Perioder kan kun avsluttes siste dag i måneden",
                    "ugyldig_periode_tom"
                )
                Periode.UgyldigPeriode.FraOgMedDatoMåVæreFørTilOgMedDato -> HttpStatusCode.BadRequest.errorJson(
                    "Startmåned må være tidligere eller lik sluttmåned",
                    "ugyldig_periode_start_slutt"
                )
            }
        }
    }

    companion object {
        fun Periode.toJson() = PeriodeJson(fraOgMed.format(DateTimeFormatter.ISO_DATE), tilOgMed.format(DateTimeFormatter.ISO_DATE))
    }
}
