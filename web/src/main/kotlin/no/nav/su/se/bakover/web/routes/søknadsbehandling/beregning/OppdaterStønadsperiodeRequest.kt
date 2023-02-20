package no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.periode.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.web.periode.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.SaksbehandlersAvgjørelse
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.web.routes.periode.toPeriodeOrResultat
import java.time.Clock

internal data class OppdaterStønadsperiodeRequest(
    val periode: PeriodeJson,
    val harSaksbehandlerAvgjort: Boolean,
) {
    fun toDomain(clock: Clock): Either<Resultat, PartialOppdaterStønadsperiodeRequest> {
        return periode.toPeriodeOrResultat().flatMap { periode ->
            Stønadsperiode.tryCreate(periode).mapLeft {
                return when (it) {
                    Stønadsperiode.UgyldigStønadsperiode.FraOgMedDatoKanIkkeVæreFør2021 -> BadRequest.errorJson(
                        "En stønadsperiode kan ikke starte før 2021",
                        "stønadsperiode_før_2021",
                    ).left()

                    Stønadsperiode.UgyldigStønadsperiode.PeriodeKanIkkeVæreLengreEnn12Måneder -> BadRequest.errorJson(
                        "En stønadsperiode kan være maks 12 måneder",
                        "stønadsperiode_max_12mnd",
                    ).left()
                }
            }
        }.mapLeft {
            it
        }.map {
            PartialOppdaterStønadsperiodeRequest(
                stønadsperiode = it,
                saksbehandlersAvgjørelse = when (harSaksbehandlerAvgjort) {
                    true -> SaksbehandlersAvgjørelse.Avgjort(Tidspunkt.now(clock))
                    false -> null
                },
            )
        }
    }

    companion object {
        fun Stønadsperiode.toJson() = StønadsperiodeJson(periode.toJson())

        internal data class StønadsperiodeJson(val periode: PeriodeJson)
    }
}

internal data class PartialOppdaterStønadsperiodeRequest(
    val stønadsperiode: Stønadsperiode,
    val saksbehandlersAvgjørelse: SaksbehandlersAvgjørelse?,
)
