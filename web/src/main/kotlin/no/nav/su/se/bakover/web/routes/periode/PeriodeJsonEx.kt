package no.nav.su.se.bakover.web.routes.periode

import arrow.core.Either
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.PeriodeJson
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson

internal fun PeriodeJson.toPeriodeOrResultat(): Either<Resultat, Periode> {
    return this.tryToPeriode().mapLeft {
        when (it) {
            Periode.UgyldigPeriode.FraOgMedDatoMåVæreFørsteDagIMåneden -> HttpStatusCode.BadRequest.errorJson(
                "Perioder kan kun starte på første dag i måneden",
                "ugyldig_periode_fom",
            )
            Periode.UgyldigPeriode.TilOgMedDatoMåVæreSisteDagIMåneden -> HttpStatusCode.BadRequest.errorJson(
                "Perioder kan kun avsluttes siste dag i måneden",
                "ugyldig_periode_tom",
            )
            Periode.UgyldigPeriode.FraOgMedDatoMåVæreFørTilOgMedDato -> HttpStatusCode.BadRequest.errorJson(
                "Startmåned må være tidligere eller lik sluttmåned",
                "ugyldig_periode_start_slutt",
            )
        }
    }
}
