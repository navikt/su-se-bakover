package no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning

import arrow.core.Either
import arrow.core.flatMap
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.PeriodeJson.Companion.toJson

internal data class StønadsperiodeJson(
    val periode: PeriodeJson,
    val begrunnelse: String,
) {
    fun toStønadsperiode(): Either<Resultat, Stønadsperiode> {
        return periode.toPeriode().flatMap { periode ->
            Stønadsperiode.tryCreate(periode, begrunnelse).mapLeft {
                when (it) {
                    Stønadsperiode.UgyldigStønadsperiode.FraOgMedDatoKanIkkeVæreFør2021 -> BadRequest.message("En stønadsperiode kan ikke starte før 2021")
                    Stønadsperiode.UgyldigStønadsperiode.PeriodeKanIkkeVæreLengreEnn12Måneder -> BadRequest.message("En stønadsperiode kan være maks 12 måneder")
                }
            }
        }
    }

    companion object {
        fun Stønadsperiode.toJson() = StønadsperiodeJson(periode.toJson(), begrunnelse)
    }
}
