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
import org.slf4j.LoggerFactory

enum class Rolle(val type: String) {
    Attestant("ATTESTANT"),
    Saksbehandler("SAKSBEHANDLER"),
    Veileder("VEILEDER")
}

data class UserData(
    val navn: String,
    val navIdent: String,
    val roller: List<Rolle>
)

@OptIn(io.ktor.locations.KtorExperimentalLocationsAPI::class)
internal fun Route.meRoutes() {
    val logger = LoggerFactory.getLogger(this::class.java)

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
                logger.info("Feil ved henting av bruker: $it")
                call.respond(HttpStatusCode.InternalServerError, "Feil under henting av bruker")
            },
            ifRight = {
                call.respond(
                    HttpStatusCode.OK,
                    serialize(
                        UserData(
                            navn = it.displayName,
                            navIdent = it.onPremisesSamAccountName!!,
                            roller = roller
                        )
                    )
                )
            }
        )
    }
}
