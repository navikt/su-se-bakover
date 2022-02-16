package no.nav.su.se.bakover.web.features

import io.ktor.application.ApplicationCall
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.ApplicationFeature
import io.ktor.application.call
import io.ktor.application.feature
import io.ktor.auth.Authentication
import io.ktor.auth.authentication
import io.ktor.routing.Route
import io.ktor.routing.RouteSelector
import io.ktor.routing.RouteSelectorEvaluation
import io.ktor.routing.RoutingResolveContext
import io.ktor.routing.application
import io.ktor.util.AttributeKey
import io.ktor.util.pipeline.PipelinePhase
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.domain.bruker.KunneIkkeHenteNavnForNavIdent
import no.nav.su.se.bakover.domain.bruker.NavIdentBruker
import no.nav.su.se.bakover.web.getGroupsFromJWT
import no.nav.su.se.bakover.web.getNAVidentFromJwt
import no.nav.su.se.bakover.web.getNavnFromJwt

/**
 * Dette er basert løst på denne bloggposten: https://www.ximedes.com/2020-09-17/role-based-authorization-in-ktor/
 */

class SuUserFeature(private val configuration: Configuration) {

    class Configuration {
        lateinit var applicationConfig: ApplicationConfig
    }

    fun interceptPipeline(pipeline: ApplicationCallPipeline) {
        pipeline.insertPhaseAfter(ApplicationCallPipeline.Features, Authentication.ChallengePhase)
        pipeline.insertPhaseAfter(Authentication.ChallengePhase, SuUserContextPhase)

        pipeline.intercept(SuUserContextPhase) {
            SuUserContext.init(call, configuration.applicationConfig)
        }
    }

    companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, SuUserFeature> {
        override val key = AttributeKey<SuUserFeature>("SuUserFeature")
        val SuUserContextPhase = PipelinePhase("Foo")

        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): SuUserFeature {
            val config = Configuration().apply(configure)

            return SuUserFeature(config)
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

        internal fun from(call: ApplicationCall) =
            call.attributes[AttributeKey]
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
    application.feature(SuUserFeature).interceptPipeline(routeWithUser)
    routeWithUser.build()
    return routeWithUser
}
