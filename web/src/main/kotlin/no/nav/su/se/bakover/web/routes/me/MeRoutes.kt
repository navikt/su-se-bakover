package no.nav.su.se.bakover.web.routes.me

import arrow.core.Either
import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import no.nav.su.se.bakover.common.Config
import no.nav.su.se.bakover.common.filterMap
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.getGroupsFromJWT

enum class Rolle(val type: String) {
    Attestant("ATTESTANT"),
    Saksbehandler("SAKSBEHANDLER"),
    Veileder("VEILEDER")
}

data class UserData(
    val navIdent: String,
    val roller: List<Rolle>
)

@OptIn(io.ktor.locations.KtorExperimentalLocationsAPI::class)
internal fun Route.meRoutes() {
    get("/me") {
        val user = call.suUserContext.user!!
        val prince = call.authentication.principal
        val roller = getGroupsFromJWT(prince).filterMap {
            when (it) {
                Config.azureGroupAttestant -> Either.Right(Rolle.Attestant)
                Config.azureGroupSaksbehandler -> Either.Right(Rolle.Saksbehandler)
                Config.azureGroupVeileder -> Either.Right(Rolle.Veileder)
                else -> Either.left(Unit)
            }
        }

        user.fold(
            ifLeft = {
                call.respond(HttpStatusCode.InternalServerError, "FEIL I HENTING AV BRUKER ELLERNO")
            },
            ifRight = {
                call.respond(
                    HttpStatusCode.OK,
                    serialize(
                        UserData(
                            navIdent = it.onPremisesSamAccountName!!,
                            roller = roller
                        )
                    )
                )
            }
        )
    }
}
