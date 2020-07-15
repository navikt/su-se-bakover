package no.nav.su.se.bakover.web

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.application.log
import io.ktor.auth.authenticate
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.features.CORS
import io.ktor.features.CallId
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.features.XForwardedHeaderSupport
import io.ktor.features.callIdMdc
import io.ktor.features.generate
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpHeaders.WWWAuthenticate
import io.ktor.http.HttpHeaders.XCorrelationId
import io.ktor.http.HttpMethod.Companion.Options
import io.ktor.http.HttpMethod.Companion.Patch
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.JacksonConverter
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.Locations
import io.ktor.request.path
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.CollectorRegistry
import no.nav.su.se.bakover.client.HttpClientBuilder
import no.nav.su.se.bakover.client.HttpClients
import no.nav.su.se.bakover.client.SOAPClientBuilder
import no.nav.su.se.bakover.client.SOAPClients
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.ObjectRepo
import no.nav.su.se.bakover.domain.UgyldigFnrException
import no.nav.su.se.bakover.web.routes.behandling.behandlingRoutes
import no.nav.su.se.bakover.web.routes.inntektRoutes
import no.nav.su.se.bakover.web.routes.installMetrics
import no.nav.su.se.bakover.web.routes.naisPaths
import no.nav.su.se.bakover.web.routes.naisRoutes
import no.nav.su.se.bakover.web.routes.personRoutes
import no.nav.su.se.bakover.web.routes.sak.sakRoutes
import no.nav.su.se.bakover.web.routes.søknad.SøknadRouteMediator
import no.nav.su.se.bakover.web.routes.søknad.søknadRoutes
import no.nav.su.se.bakover.web.routes.vilkårsvurdering.vilkårsvurderingRoutes
import org.json.JSONObject
import org.slf4j.event.Level
import java.net.URL

fun main(args: Array<String>) = io.ktor.server.netty.EngineMain.main(args)

@KtorExperimentalLocationsAPI
@KtorExperimentalAPI
internal fun Application.susebakover(
    databaseRepo: ObjectRepo = DatabaseBuilder.build(),
    httpClients: HttpClients = HttpClientBuilder.build(),
    jwkConfig: JSONObject = httpClients.oauth.jwkConfig(),
    jwkProvider: JwkProvider = JwkProviderBuilder(URL(jwkConfig.getString("jwks_uri"))).build(),
    authenticationHttpClient: HttpClient = HttpClient(Apache) {
        engine {
            customizeClient {
                useSystemProperties()
            }
        }
    },
    soapClients: SOAPClients = SOAPClientBuilder.build()
) {

    val søknadRoutesMediator = SøknadRouteMediator(
        repo = databaseRepo,
        pdfGenerator = httpClients.pdfGenerator,
        dokArkiv = httpClients.dokArkiv,
        oppgave = httpClients.oppgave,
        personOppslag = httpClients.personOppslag
    )

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
            log.error("Got UgyldigFnrException with message=${it.message}", it)
            call.respond(HttpStatusCode.BadRequest, it)
        }
        exception<Throwable> {
            log.error("Got Throwable with message=${it.message}", it)
            call.respond(HttpStatusCode.InternalServerError, it)
        }
    }

    val collectorRegistry = CollectorRegistry.defaultRegistry
    installMetrics(collectorRegistry)
    naisRoutes(collectorRegistry)

    setupAuthentication(
        jwkConfig = jwkConfig,
        jwkProvider = jwkProvider,
        config = environment.config,
        httpClient = authenticationHttpClient
    )
    oauthRoutes(
        frontendRedirectUrl = fromEnvironment("integrations.suSeFramover.redirectUrl"),
        oAuth = httpClients.oauth
    )

    install(Locations)

    install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter(objectMapper))
    }

    install(CallId) {
        header(XCorrelationId)
        generate(17)
        verify { it.isNotEmpty() }
    }
    install(CallLogging) {
        level = Level.INFO
        filter { call ->
            val path = call.request.path()
            (naisPaths + AUTH_CALLBACK_PATH).none {
                path.startsWith(it)
            }
        }
        callIdMdc("X-Correlation-ID")

        mdc("Authorization") { it.authHeader() }
    }

    install(XForwardedHeaderSupport)

    routing {
        authenticate("jwt") {
            get(path = "/authenticated") {
                val principal = (call.authentication.principal as JWTPrincipal).payload
                call.respond(
                    """
                    {
                        "data": "Congrats ${principal.getClaim("name")
                        .asString()}, you are successfully authenticated with a JWT token"
                    }
                """.trimIndent()
                )
            }

            personRoutes(httpClients.personOppslag)
            inntektRoutes(httpClients.inntektOppslag)
            sakRoutes(databaseRepo)
            søknadRoutes(søknadRoutesMediator)
            behandlingRoutes(databaseRepo)
            vilkårsvurderingRoutes(databaseRepo)
        }
    }
}
