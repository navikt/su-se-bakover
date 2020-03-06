package no.nav.su.se.bakover

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.github.kittinunf.fuel.httpGet
import io.ktor.application.*
import io.ktor.auth.authenticate
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.config.ApplicationConfig
import io.ktor.features.*
import io.ktor.gson.gson
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpHeaders.WWWAuthenticate
import io.ktor.http.HttpHeaders.XCorrelationId
import io.ktor.http.HttpMethod.Companion.Options
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.Locations
import io.ktor.request.path
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.CollectorRegistry
import no.nav.su.se.bakover.Either.Left
import no.nav.su.se.bakover.Either.Right
import no.nav.su.se.bakover.azure.AzureClient
import no.nav.su.se.bakover.azure.OAuth
import no.nav.su.se.bakover.db.Postgres
import no.nav.su.se.bakover.db.Postgres.Role
import no.nav.su.se.bakover.db.Postgres.Role.Admin
import no.nav.su.se.bakover.db.Postgres.Role.User
import no.nav.su.se.bakover.db.DatabaseRepository
import no.nav.su.se.bakover.db.Flyway
import no.nav.su.se.bakover.domain.SakFactory
import no.nav.su.se.bakover.domain.SøknadFactory
import no.nav.su.se.bakover.inntekt.InntektOppslag
import no.nav.su.se.bakover.inntekt.SuInntektClient
import no.nav.su.se.bakover.inntekt.inntektRoutes
import no.nav.su.se.bakover.kafka.KafkaConfigBuilder
import no.nav.su.se.bakover.kafka.SøknadMottattEmitter
import no.nav.su.se.bakover.person.PersonOppslag
import no.nav.su.se.bakover.person.SuPersonClient
import no.nav.su.se.bakover.person.personRoutes
import no.nav.su.se.bakover.sak.sakRoutes
import no.nav.su.se.bakover.soknad.soknadRoutes
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringSerializer
import org.json.JSONObject
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.slf4j.event.Level
import java.net.URL
import java.util.*
import javax.sql.DataSource

@KtorExperimentalLocationsAPI
@KtorExperimentalAPI
internal fun Application.susebakover(
    kafkaConfig: KafkaConfigBuilder = KafkaConfigBuilder(environment.config),
    hendelseProducer: KafkaProducer<String, String> = KafkaProducer(
                kafkaConfig.producerConfig(),
                StringSerializer(),
                StringSerializer()
        ),
    dataSource: DataSource = getDatasource(),
    jwkConfig: JSONObject = getJWKConfig(fromEnvironment("azure.wellknownUrl")),
    jwkProvider: JwkProvider = JwkProviderBuilder(URL(jwkConfig.getString("jwks_uri"))).build(),
    oAuth: OAuth = AzureClient(
                fromEnvironment("azure.clientId"),
                fromEnvironment("azure.clientSecret"),
                jwkConfig.getString("token_endpoint")
        ),
    personOppslag: PersonOppslag = SuPersonClient(
                fromEnvironment("integrations.suPerson.url"),
                fromEnvironment("integrations.suPerson.clientId"),
                oAuth
        ),
    inntektOppslag: InntektOppslag = SuInntektClient(
                fromEnvironment("integrations.suInntekt.url"),
                fromEnvironment("integrations.suInntekt.clientId"),
                oAuth,
                personOppslag
        )
) {
    Flyway(getDatasource(Admin), fromEnvironment("db.name")).migrate()

    val databaseRepo = DatabaseRepository(dataSource)
    val kafkaEmittingSøknadObserver = SøknadMottattEmitter(hendelseProducer, oAuth, fromEnvironment("integrations.suPerson.clientId"), personOppslag)
    val søknadFactory = SøknadFactory(databaseRepo, listOf(kafkaEmittingSøknadObserver))
    val sakFactory = SakFactory(databaseRepo, emptyList(), søknadFactory)

    install(CORS) {
        method(Options)
        header(Authorization)
        header("refresh_token")
        allowNonSimpleContentTypes = true
        exposeHeader(WWWAuthenticate)
        exposeHeader("access_token")
        exposeHeader("refresh_token")
        host(fromEnvironment("cors.allow.origin"), listOf("http", "https"))
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
            oAuth = oAuth
    )

    install(Locations)

    install(ContentNegotiation) {
        gson {
            setPrettyPrinting()
        }
    }
    routing {
        authenticate("jwt") {
            install(CallId) {
                header(XCorrelationId)
                generate(17)
            }
            install(CallLogging) {
                level = Level.INFO
                intercept(ApplicationCallPipeline.Monitoring) {
                    MDC.put(XCorrelationId, call.callId)
                }
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

            personRoutes(personOppslag)
            inntektRoutes(inntektOppslag)
            sakRoutes(sakFactory)
            soknadRoutes(sakFactory, søknadFactory)
        }
    }
}

@KtorExperimentalAPI
fun Application.fromEnvironment(path: String): String = environment.config.property(path).getString()

@KtorExperimentalAPI
internal fun ApplicationConfig.getProperty(key: String): String = property(key).getString()

internal fun ApplicationCall.audit(msg: String) {
    val principal = (this.authentication.principal as JWTPrincipal).payload
    LoggerFactory.getLogger("sikkerLogg").info("${principal.subject} $msg")
}

@KtorExperimentalAPI
internal fun Application.getDatasource(role: Role = User): DataSource {
    return Postgres(environment.config).build().getDatasource(role)
}

fun main(args: Array<String>) = io.ktor.server.netty.EngineMain.main(args)

private fun getJWKConfig(wellKnownUrl: String): JSONObject {
    val (_, _, result) = wellKnownUrl.httpGet().responseString()
    return result.fold(
            { JSONObject(it) },
            { throw RuntimeException("Could not get JWK config from url ${wellKnownUrl}, error:${it}") }
    )
}

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