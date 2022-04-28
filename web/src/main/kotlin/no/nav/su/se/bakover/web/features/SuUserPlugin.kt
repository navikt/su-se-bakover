package no.nav.su.se.bakover.web.features

import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.BaseApplicationPlugin
import io.ktor.server.application.call
import io.ktor.server.application.plugin
import io.ktor.server.auth.authentication
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext
import io.ktor.server.routing.application
import io.ktor.util.AttributeKey
import io.ktor.util.pipeline.PipelinePhase
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.person.KunneIkkeHenteNavnForNavIdent
import no.nav.su.se.bakover.web.getGroupsFromJWT
import no.nav.su.se.bakover.web.getNAVidentFromJwt
import no.nav.su.se.bakover.web.getNavnFromJwt

/**
 * Dette er basert løst på denne bloggposten: https://www.ximedes.com/2020-09-17/role-based-authorization-in-ktor/
 */

class SuUserPlugin(private val configuration: Configuration) {

    class Configuration {
        lateinit var applicationConfig: ApplicationConfig
    }

    fun interceptPipeline(pipeline: ApplicationCallPipeline) {
        // TODO jah: De har fjernet Authentication.ChallengePhase
        val ChallengePhase = PipelinePhase("Challenge")
        pipeline.insertPhaseAfter(ApplicationCallPipeline.Plugins, ChallengePhase)
        pipeline.insertPhaseAfter(ChallengePhase, SuUserContextPhase)

        pipeline.intercept(SuUserContextPhase) {
            SuUserContext.init(call, configuration.applicationConfig)
        }
    }

    companion object Feature : BaseApplicationPlugin<ApplicationCallPipeline, Configuration, SuUserPlugin> {
        override val key = AttributeKey<SuUserPlugin>("SuUserFeature")
        val SuUserContextPhase = PipelinePhase("Foo")

        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): SuUserPlugin {
            val config = Configuration().apply(configure)

            return SuUserPlugin(config)
        }
    }
}

internal sealed class SuUserFeaturefeil(override val message: String) : RuntimeException(message)

internal object ManglerAuthHeader : SuUserFeaturefeil("Mangler auth header")
internal object IkkeInitialisert : SuUserFeaturefeil("Ikke initialisert")
internal data class KallMotMicrosoftGraphApiFeilet(val feil: KunneIkkeHenteNavnForNavIdent) :
    SuUserFeaturefeil("Kall mot Microsoft Graph Api feilet")

internal object FantBrukerMenManglerNAVIdent : SuUserFeaturefeil("Bruker mangler NAVIdent")

class SuUserContext(val call: ApplicationCall, applicationConfig: ApplicationConfig) {
    val navIdent: String = getNAVidentFromJwt(applicationConfig, call.authentication.principal)
    val saksbehandler: NavIdentBruker.Saksbehandler = NavIdentBruker.Saksbehandler(navIdent)
    val attestant: NavIdentBruker.Attestant = NavIdentBruker.Attestant(navIdent)
    val navn: String = getNavnFromJwt(applicationConfig, call.authentication.principal)
    val grupper = getGroupsFromJWT(applicationConfig, call.authentication.principal)

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

fun Route.withUser(build: Route.() -> Unit): Route {
    val routeWithUser = createChild(SuUserRouteSelector())
    application.plugin(SuUserPlugin).interceptPipeline(routeWithUser)
    routeWithUser.build()
    return routeWithUser
}
