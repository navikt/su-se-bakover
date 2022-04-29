package no.nav.su.se.bakover.web.features

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.BaseApplicationPlugin
import io.ktor.server.application.call
import io.ktor.server.application.plugin
import io.ktor.server.auth.Principal
import io.ktor.server.auth.authentication
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext
import io.ktor.server.routing.application
import io.ktor.util.AttributeKey
import io.ktor.util.pipeline.PipelineContext
import io.ktor.util.pipeline.PipelinePhase
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.web.ErrorJson
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
        // TODO jah: De har fjernet Authentication.ChallengePhase
        val ChallengePhase = PipelinePhase("Challenge")
        pipeline.insertPhaseAfter(ApplicationCallPipeline.Plugins, ChallengePhase)
        pipeline.insertPhaseAfter(ChallengePhase, AuthorizationPhase)

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

    companion object Plugin : BaseApplicationPlugin<ApplicationCallPipeline, Configuration, Authorization> {
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
    application.plugin(Authorization).interceptPipeline(authorizedRoute, roller.toSet())
    authorizedRoute.build()
    return authorizedRoute
}

suspend fun PipelineContext<Unit, ApplicationCall>.authorize(
    theFirstRolle: Brukerrolle,
    vararg roller: Brukerrolle,
    build: suspend PipelineContext<Unit, ApplicationCall>.() -> Unit,
) {
    val krevdeRoller = (listOf(theFirstRolle) + roller.toList())
    val grupper = call.suUserContext.grupper.map { Brukerrolle.valueOf(it) }
    val containsRolle = krevdeRoller.any { grupper.contains(it) }

    if (containsRolle) {
        build()
    } else {
        call.respond(
            HttpStatusCode.Forbidden,
            ErrorJson("Bruker mangler en av de tillatte rollene: $krevdeRoller"),
        )
    }
}
