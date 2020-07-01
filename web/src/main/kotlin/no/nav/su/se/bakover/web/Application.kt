package no.nav.su.se.bakover.web

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.application.log
import io.ktor.auth.authenticate
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.config.ApplicationConfig
import io.ktor.features.CORS
import io.ktor.features.CallId
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.features.callId
import io.ktor.features.generate
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpHeaders.WWWAuthenticate
import io.ktor.http.HttpHeaders.XCorrelationId
import io.ktor.http.HttpMethod.Companion.Options
import io.ktor.http.HttpMethod.Companion.Patch
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.Locations
import io.ktor.request.header
import io.ktor.request.path
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.CollectorRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import no.nav.su.se.bakover.client.HttpClientBuilder
import no.nav.su.se.bakover.client.HttpClients
import no.nav.su.se.bakover.client.KafkaClientBuilder
import no.nav.su.se.bakover.client.SuKafkaClient
import no.nav.su.se.bakover.common.CallContext
import no.nav.su.se.bakover.common.CallContext.MdcContext
import no.nav.su.se.bakover.common.CallContext.SecurityContext
import no.nav.su.se.bakover.common.Either
import no.nav.su.se.bakover.common.Either.Left
import no.nav.su.se.bakover.common.Either.Right
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.ObjectRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.UgyldigFnrException
import no.nav.su.se.bakover.web.kafka.SøknadMottattEmitter
import no.nav.su.se.bakover.web.routes.IS_ALIVE_PATH
import no.nav.su.se.bakover.web.routes.IS_READY_PATH
import no.nav.su.se.bakover.web.routes.METRICS_PATH
import no.nav.su.se.bakover.web.routes.SøknadRouteMediator
import no.nav.su.se.bakover.web.routes.behandlingRoutes
import no.nav.su.se.bakover.web.routes.inntektRoutes
import no.nav.su.se.bakover.web.routes.installMetrics
import no.nav.su.se.bakover.web.routes.naisRoutes
import no.nav.su.se.bakover.web.routes.personRoutes
import no.nav.su.se.bakover.web.routes.sakRoutes
import no.nav.su.se.bakover.web.routes.soknadRoutes
import no.nav.su.se.bakover.web.routes.stønadsperiodeRoutes
import no.nav.su.se.bakover.web.routes.vilkårsvurderingRoutes
import org.json.JSONObject
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.net.URL
import java.util.Properties
import kotlin.coroutines.CoroutineContext

@KtorExperimentalLocationsAPI
@KtorExperimentalAPI
internal fun Application.susebakover(
    kafkaClient: SuKafkaClient = KafkaClientBuilder.build(),
    databaseRepo: ObjectRepo = DatabaseBuilder.build(),
    httpClients: HttpClients = HttpClientBuilder.build(),
    jwkConfig: JSONObject = httpClients.oauth.jwkConfig(),
    jwkProvider: JwkProvider = JwkProviderBuilder(URL(jwkConfig.getString("jwks_uri"))).build()
) {

    val søknadMottattEmitter = SøknadMottattEmitter(kafkaClient, httpClients.personOppslag)
    val søknadRoutesMediator = SøknadRouteMediator(databaseRepo, søknadMottattEmitter)

    install(CORS) {
        method(Options)
        method(Patch)
        header(Authorization)
        header("refresh_token")
        header(XCorrelationId)
        allowNonSimpleContentTypes = true
        exposeHeader(WWWAuthenticate)
        exposeHeader("access_token")
        exposeHeader("refresh_token")
        host(fromEnvironment("cors.allow.origin"), listOf("http", "https"))
    }

    install(StatusPages) {
        exception<UgyldigFnrException> {
            log.error(it.toString())
            call.respond(HttpStatusCode.BadRequest, it)
        }
        exception<Throwable> {
            log.error(it.toString())
            call.respond(HttpStatusCode.InternalServerError, it)
        }
    }

    val collectorRegistry = CollectorRegistry.defaultRegistry
    installMetrics(collectorRegistry)
    naisRoutes(collectorRegistry)

    setupAuthentication(
            jwkConfig = jwkConfig,
            jwkProvider = jwkProvider,
            config = environment.config
    )
    oauthRoutes(
            frontendRedirectUrl = fromEnvironment("integrations.suSeFramover.redirectUrl"),
            oAuth = httpClients.oauth
    )

    install(Locations)

    install(ContentNegotiation) {
        jackson {}
    }
    routing {
        authenticate("jwt") {
            install(CallId) {
                header(XCorrelationId)
                generate(17)
                verify { it.isNotEmpty() }
            }
            install(CallLogging) {
                level = Level.INFO
                filter { call ->
                    listOf(IS_ALIVE_PATH, IS_READY_PATH, METRICS_PATH).none {
                        call.request.path().startsWith(it)
                    }
                }
            }

            get(path = "/authenticated") {
                val principal = (call.authentication.principal as JWTPrincipal).payload
                call.respond("""
                    {
                        "data": "Congrats ${principal.getClaim("name").asString()}, you are successfully authenticated with a JWT token"
                    }
                """.trimIndent())
            }

            personRoutes(httpClients.personOppslag, databaseRepo)
            inntektRoutes(httpClients.inntektOppslag)
            sakRoutes(databaseRepo)
            soknadRoutes(søknadRoutesMediator)
            behandlingRoutes(databaseRepo)
            stønadsperiodeRoutes(databaseRepo)
            vilkårsvurderingRoutes(databaseRepo)
        }
    }
}

@KtorExperimentalAPI
fun Application.fromEnvironment(path: String): String = environment.config.property(path).getString()

@KtorExperimentalAPI
internal fun ApplicationConfig.getProperty(key: String): String = property(key).getString()
internal fun ApplicationConfig.getProperty(key: String, default: String): String = propertyOrNull(key)?.getString() ?: default

internal fun ApplicationCall.audit(msg: String) {
    val payload = (this.authentication.principal as JWTPrincipal).payload
    LoggerFactory.getLogger("sikkerLogg").info("${payload.getClaim("oid").asString()} $msg")
}

fun main(args: Array<String>) = io.ktor.server.netty.EngineMain.main(args)

internal fun Long.Companion.lesParameter(call: ApplicationCall, name: String): Either<String, Long> =
        call.parameters[name]?.let {
            it.toLongOrNull()?.let {
                Right(it)
            } ?: Left("$name er ikke et tall")
        } ?: Left("$name er ikke et parameter")

internal fun byggVersion(): String {
    val versionProps = Properties()
    versionProps.load(Application::class.java.getResourceAsStream("/VERSION"))
    return versionProps.getProperty("commit.sha", "ikke satt")
}

suspend fun launchWithContext(call: ApplicationCall, block: suspend CoroutineScope.() -> Unit) {
    val callContext = CallContext(
            security = SecurityContext(token = call.authHeader()),
            mdc = MdcContext(mapOf(XCorrelationId to call.callId.toString()))
    )
    val coroutineContext = Dispatchers.Default + callContext.toCoroutineContext()
    coroutineScope { launch(context = coroutineContext, block = block).join() }
}

fun CallContext.toCoroutineContext(): CoroutineContext {
    val security = securityContextElement()
    val mdc = mdcContextElement()
    return security.first.asContextElement(security.second) + mdc.first.asContextElement(mdc.second)
}

fun ApplicationCall.authHeader() = this.request.header(Authorization).toString()

internal fun Fnr.Companion.lesParameter(call: ApplicationCall) = Fnr(call.parameters[FNR])
