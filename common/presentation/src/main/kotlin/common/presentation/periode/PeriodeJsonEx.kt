package common.presentation.periode

import arrow.core.Either
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.tid.periode.Periode

fun PeriodeJson.toPeriodeOrResultat(): Either<Resultat, Periode> {
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
