package no.nav.su.se.bakover.web.features

import arrow.core.Either
import arrow.core.getOrHandle
import io.ktor.application.ApplicationCall
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.ApplicationFeature
import io.ktor.application.call
import io.ktor.application.feature
import io.ktor.auth.Authentication
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

        if (suUserContext.isInitialized()) {
            return
        }

        val authHeader = context.call.request.header(HttpHeaders.Authorization)

        suUserContext.setUser(
            if (authHeader != null) {
                clients.microsoftGraphApiClient.hentBrukerinformasjon(authHeader)
                    .mapLeft { KallMotMicrosoftGraphApiFeilet(it) }
            } else {
                Either.Left(ManglerAuthHeader)
            }
        )
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
internal data class KallMotMicrosoftGraphApiFeilet(override val message: String) : SuUserFeaturefeil(message)
internal object FantBrukerMenManglerNAVIdent : SuUserFeaturefeil("Bruker mangler NAVIdent")

class SuUserContext(val call: ApplicationCall) {
    private var _user: Either<SuUserFeaturefeil, MicrosoftGraphResponse>? = null

    val user: MicrosoftGraphResponse
        get() = _user
            ?.getOrHandle { throw it }
            ?: throw IkkeInitialisert

    internal fun setUser(u: Either<SuUserFeaturefeil, MicrosoftGraphResponse>) {
        _user = u
    }

    companion object {
        private val AttributeKey = AttributeKey<SuUserContext>("SuUserContext")

        internal fun from(call: ApplicationCall) =
            call.attributes.computeIfAbsent(AttributeKey) { SuUserContext(call) }
    }

    internal fun isInitialized() = _user != null

    fun getNAVIdent(): String = user.onPremisesSamAccountName ?: throw FantBrukerMenManglerNAVIdent
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
