package no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning

import arrow.core.Either
import arrow.core.flatMap
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import no.nav.su.se.bakover.common.periode.PeriodeJson
import no.nav.su.se.bakover.common.periode.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.routes.periode.toPeriodeOrResultat

internal data class StønadsperiodeJson(
    val periode: PeriodeJson,
) {
    fun toStønadsperiode(): Either<Resultat, Stønadsperiode> {
        return periode.toPeriodeOrResultat().flatMap { periode ->
            Stønadsperiode.tryCreate(periode).mapLeft {
                when (it) {
                    Stønadsperiode.UgyldigStønadsperiode.FraOgMedDatoKanIkkeVæreFør2021 -> BadRequest.errorJson(
                        "En stønadsperiode kan ikke starte før 2021",
                        "stønadsperiode_før_2021",
                    )
                    Stønadsperiode.UgyldigStønadsperiode.PeriodeKanIkkeVæreLengreEnn12Måneder -> BadRequest.errorJson(
                        "En stønadsperiode kan være maks 12 måneder",
                        "stønadsperiode_max_12mnd",
                    )
                }
            }
        }
    }

    companion object {
        fun Stønadsperiode.toJson() = StønadsperiodeJson(periode.toJson())
    }
}
