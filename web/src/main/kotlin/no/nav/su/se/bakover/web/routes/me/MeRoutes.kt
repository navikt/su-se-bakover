package no.nav.su.se.bakover.web.routes.me

import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.bruker.Brukerrolle
import no.nav.su.se.bakover.web.AzureGroupMapper
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.getGroupsFromJWT

data class UserData(
    val navn: String,
    val navIdent: String,
    val roller: List<Brukerrolle>
)

@OptIn(io.ktor.locations.KtorExperimentalLocationsAPI::class)
internal fun Route.meRoutes(applicationConfig: ApplicationConfig, azureGroupMapper: AzureGroupMapper) {
    get("/me") {
        val roller =
            getGroupsFromJWT(applicationConfig, call.authentication.principal)
                .mapNotNull { azureGroupMapper.fromAzureGroup(it) }

        call.respond(
            HttpStatusCode.OK,
            serialize(
                UserData(
                    navn = call.suUserContext.navn,
                    navIdent = call.suUserContext.navIdent,
                    roller = roller
                )
            )
        )
    }
}
