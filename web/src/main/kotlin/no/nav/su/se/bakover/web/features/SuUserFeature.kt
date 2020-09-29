package no.nav.su.se.bakover.web.features

import arrow.core.Either
import io.ktor.application.ApplicationCall
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.ApplicationFeature
import io.ktor.application.call
import io.ktor.application.feature
import io.ktor.auth.Authentication
import io.ktor.auth.authentication
import io.ktor.http.HttpHeaders
import io.ktor.request.header
import io.ktor.routing.Route
import io.ktor.routing.RouteSelector
import io.ktor.routing.RouteSelectorEvaluation
import io.ktor.routing.RoutingResolveContext
import io.ktor.routing.application
import io.ktor.util.AttributeKey
import io.ktor.util.KtorExperimentalAPI
import io.ktor.util.pipeline.PipelineContext
import io.ktor.util.pipeline.PipelinePhase
import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.client.person.MicrosoftGraphResponse

/**
 * Dette er basert løst på denne bloggposten: https://www.ximedes.com/2020-09-17/role-based-authorization-in-ktor/
 */

@KtorExperimentalAPI
class SuUserFeature(configuration: Configuration) {
    val clients = configuration.clients

    class Configuration {
        lateinit var clients: Clients
    }

    fun interceptPipeline(pipeline: ApplicationCallPipeline) {
        pipeline.insertPhaseAfter(ApplicationCallPipeline.Features, Authentication.ChallengePhase)
        pipeline.insertPhaseAfter(Authentication.ChallengePhase, SuUserContextPhase)

        pipeline.intercept(SuUserContextPhase) {
            intercept(this)
        }
    }

    private fun intercept(context: PipelineContext<Unit, ApplicationCall>) {
        val suUserContext = SuUserContext.from(context.call)

        if (suUserContext.user != null || context.call.authentication.allErrors.isNotEmpty()) {
            return
        }

        val authHeader = context.call.request.header(HttpHeaders.Authorization) ?: return

        suUserContext.user = clients.microsoftGraphApiClient.hentBrukerinformasjon(authHeader)
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

class SuUserContext(val call: ApplicationCall) {
    var user: Either<String, MicrosoftGraphResponse>? = null

    companion object {
        private val AttributeKey = AttributeKey<SuUserContext>("SuUserContext")

        internal fun from(call: ApplicationCall) =
            call.attributes.computeIfAbsent(AttributeKey) { SuUserContext(call) }
    }

    fun getNAVIdent(): String? = user?.let { u ->
        u
            .map { it.onPremisesSamAccountName }
            .orNull()
    }
}

val ApplicationCall.suUserContext: SuUserContext
    get() = SuUserContext.from(this)

class SuUserRouteSelector :
    RouteSelector(RouteSelectorEvaluation.qualityConstant) {
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int) = RouteSelectorEvaluation.Constant

    override fun toString(): String = "(with user)"
}

@KtorExperimentalAPI
fun Route.withUser(build: Route.() -> Unit): Route {
    val routeWithUser = createChild(SuUserRouteSelector())
    application.feature(SuUserFeature).interceptPipeline(routeWithUser)
    routeWithUser.build()
    return routeWithUser
}
