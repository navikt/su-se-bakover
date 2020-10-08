package no.nav.su.se.bakover.web.routes.me

import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import no.nav.su.se.bakover.common.filterMap
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.getGroupsFromJWT

data class UserData(
    val navn: String,
    val navIdent: String,
    val roller: List<Brukerrolle>
)

@OptIn(io.ktor.locations.KtorExperimentalLocationsAPI::class)
internal fun Route.meRoutes() {
    get("/me") {
        val prince = call.authentication.principal
        val roller = getGroupsFromJWT(prince).filterMap {
            Brukerrolle.fromAzureGroup(it)
        }

        call.respond(
            HttpStatusCode.OK,
            serialize(
                UserData(
                    navn = call.suUserContext.user.displayName,
                    navIdent = call.suUserContext.getNAVIdent(),
                    roller = roller
                )
            )
        )
    }
}
