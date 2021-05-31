package no.nav.su.se.bakover.web.features

import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.ApplicationFeature
import io.ktor.application.call
import io.ktor.application.feature
import io.ktor.auth.Authentication
import io.ktor.auth.Principal
import io.ktor.auth.authentication
import io.ktor.request.path
import io.ktor.routing.Route
import io.ktor.routing.RouteSelector
import io.ktor.routing.RouteSelectorEvaluation
import io.ktor.routing.RoutingResolveContext
import io.ktor.routing.application
import io.ktor.util.AttributeKey
import io.ktor.util.pipeline.PipelinePhase
import no.nav.su.se.bakover.domain.Brukerrolle
import org.slf4j.LoggerFactory

class AuthorizationException(override val message: String) : RuntimeException(message)

class Authorization(config: Configuration) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private val getRoller = config._getRoller

    class Configuration {
        internal var _getRoller: (Principal) -> Set<Brukerrolle> = { emptySet() }

        fun getRoller(f: (Principal) -> Set<Brukerrolle>) {
            _getRoller = f
        }
    }

    fun interceptPipeline(
        pipeline: ApplicationCallPipeline,
        tillatteRoller: Set<Brukerrolle> = emptySet()
    ) {
        pipeline.insertPhaseAfter(ApplicationCallPipeline.Features, Authentication.ChallengePhase)
        pipeline.insertPhaseAfter(Authentication.ChallengePhase, AuthorizationPhase)

        pipeline.intercept(AuthorizationPhase) {
            val principal = call.authentication.principal<Principal>()
                ?: throw RuntimeException("Could not get principal")
            val roller = getRoller(principal)

            if (!roller.any { tillatteRoller.contains(it) }) {
                val msg = "Bruker mangler en av de tillatte rollene: ${tillatteRoller.joinToString(",")}."
                logger.info("Autorisering feilet for ${call.request.path()}. $msg")
                throw AuthorizationException(msg)
            }
        }
    }

    companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, Authorization> {
        override val key = AttributeKey<Authorization>("Authorization")

        val AuthorizationPhase = PipelinePhase("Authorization")

        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): Authorization {
            val configuration = Configuration().apply(configure)
            return Authorization(configuration)
        }
    }
}

class AuthorizedRouteSelector(private val description: String) : RouteSelector() {
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int) = RouteSelectorEvaluation.Constant

    override fun toString() = "(authorize $description)"
}

fun Route.authorize(vararg roller: Brukerrolle, build: Route.() -> Unit): Route {
    val authorizedRoute = createChild(AuthorizedRouteSelector(roller.joinToString(",")))
    application.feature(Authorization).interceptPipeline(authorizedRoute, roller.toSet())
    authorizedRoute.build()
    return authorizedRoute
}
