package no.nav.su.se.bakover.web.features

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.RouteScopedPlugin
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.application.isHandled
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.auth.UnauthorizedResponse
import io.ktor.server.auth.authentication
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext
import io.ktor.util.AttributeKey
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.web.AzureGroupMapper
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.getGroupsFromJWT
import no.nav.su.se.bakover.web.getNAVidentFromJwt
import no.nav.su.se.bakover.web.getNavnFromJwt
import no.nav.su.se.bakover.web.svar

class SuUserContext(val call: ApplicationCall, applicationConfig: ApplicationConfig) {
    val navIdent: String = getNAVidentFromJwt(applicationConfig, call.authentication.principal)
    val saksbehandler: NavIdentBruker.Saksbehandler = NavIdentBruker.Saksbehandler(navIdent)
    val attestant: NavIdentBruker.Attestant = NavIdentBruker.Attestant(navIdent)
    val navn: String = getNavnFromJwt(applicationConfig, call.authentication.principal)
    val grupper = getGroupsFromJWT(applicationConfig, call.authentication.principal)
    val roller = grupper.mapNotNull {
        AzureGroupMapper(applicationConfig.azure.groups).fromAzureGroup(it)
    }

    companion object {
        private val AttributeKey = AttributeKey<SuUserContext>("SuUserContext")

        internal fun init(call: ApplicationCall, applicationConfig: ApplicationConfig) =
            call.attributes.put(AttributeKey, SuUserContext(call, applicationConfig))

        internal fun from(call: ApplicationCall) = call.attributes[AttributeKey]
    }
}

val ApplicationCall.suUserContext: SuUserContext
    get() = SuUserContext.from(this)

class SuUserRouteSelector :
    RouteSelector() {
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int) = RouteSelectorEvaluation.Constant

    override fun toString(): String = "(with user)"
}

fun Route.withUser(applicationConfig: ApplicationConfig, build: Route.() -> Unit): Route {
    val routeWithUser = createChild(SuUserRouteSelector())
    routeWithUser.install(brukerinfoPlugin { BrukerinfoPluginConfig(applicationConfig) })
    routeWithUser.build()
    return routeWithUser
}

data class BrukerinfoPluginConfig(val applicationConfig: ApplicationConfig)

private fun brukerinfoPlugin(
    config: () -> BrukerinfoPluginConfig,
): RouteScopedPlugin<BrukerinfoPluginConfig> {
    return createRouteScopedPlugin("SuBrukerPlugin", config) {
        on(AuthenticationChecked) { call ->
            when {
                call.isHandled -> {
                    /** En annen plugin i pipelinen har allerede gitt en respons på kallet, ikke gjør noe. */
                }
                call.authentication.principal == null -> {
                    /**
                     * Krev at autentiseringen er gjennomført før vi henter ut brukerinfo fra token.
                     * Rekkefølgen her er viktig, og styres pt av hvor i route-hierarkiet man kaller på [withUser]
                     */
                    call.respond(UnauthorizedResponse())
                }
                else -> {
                    try {
                        SuUserContext.init(call, pluginConfig.applicationConfig)
                    } catch (ex: Throwable) {
                        log.error("Ukjent feil ved uthenting av brukerinformasjon", ex)
                        call.svar(
                            HttpStatusCode.InternalServerError.errorJson(
                                message = "Ukjent feil ved uthenting av brukerinformasjon",
                                code = "ukjent_feil_ved_uthenting_av_brukerinformasjon",
                            ),
                        )
                    }
                }
            }
        }
    }
}
