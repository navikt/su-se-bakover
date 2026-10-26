package common.presentation.periode

import arrow.core.Either
import arrow.core.left
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.tid.periode.Periode

fun PeriodeJson.toPeriodeOrResultat(): Either<Resultat, Periode> {
    val periode = runCatching { this.tryToPeriode() }.getOrElse {
        return HttpStatusCode.BadRequest.errorJson(
            "Ugyldig datoformat i perioden. Forventet format er yyyy-MM-dd",
            "ugyldig_datoformat",
        ).left()
    }
    return periode.mapLeft {
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
