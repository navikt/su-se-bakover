package no.nav.su.se.bakover

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkProvider
import com.github.tomakehurst.wiremock.WireMockServer
import io.ktor.application.Application
import io.ktor.config.MapApplicationConfig
import io.ktor.http.HttpHeaders.XCorrelationId
import io.ktor.http.HttpMethod
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.server.testing.TestApplicationCall
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.handleRequest
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.client.*
import no.nav.su.se.bakover.database.EmbeddedDatabase.getEmbeddedJdbcUrl
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.web.EmbeddedKafka
import no.nav.su.se.bakover.web.Jwt
import no.nav.su.se.bakover.web.susebakover
import org.json.JSONObject
import java.util.*

const val AZURE_CLIENT_ID = "clientId"
const val AZURE_CLIENT_SECRET = "secret"
const val AZURE_REQUIRED_GROUP = "su-group"
const val AZURE_ISSUER = "azure"
const val AZURE_BACKEND_CALLBACK_URL = "/callback"
const val SUBJECT = "enSaksbehandler"
const val SU_FRONTEND_REDIRECT_URL = "auth/complete"
const val SU_FRONTEND_ORIGIN = "localhost"
const val DEFAULT_CALL_ID = "her skulle vi sikkert hatt en korrelasjonsid" // FIXME: This means that we don't test correlationID , this does not currently work
const val DB_USERNAME = "postgres"
const val DB_PASSWORD = "postgres"
const val DB_VAULT_MOUNTPATH = ""
const val DB_NAME = "postgres"

@KtorExperimentalAPI
fun Application.testEnv(wireMockServer: WireMockServer? = null) {
    val baseUrl = wireMockServer?.baseUrl() ?: SU_FRONTEND_ORIGIN
    (environment.config as MapApplicationConfig).apply {
        put("cors.allow.origin", SU_FRONTEND_ORIGIN)
        put("integrations.suSeFramover.redirectUrl", "$baseUrl$SU_FRONTEND_REDIRECT_URL")
        put("azure.requiredGroup", AZURE_REQUIRED_GROUP)
        put("azure.clientId", AZURE_CLIENT_ID)
        put("azure.clientSecret", AZURE_CLIENT_SECRET)
        put("azure.backendCallbackUrl", "$baseUrl$AZURE_BACKEND_CALLBACK_URL")
        put("issuer", AZURE_ISSUER)
        put("db.username", DB_USERNAME)
        put("db.password", DB_PASSWORD)
        put("db.jdbcUrl", getEmbeddedJdbcUrl())
        put("db.vaultMountPath", DB_VAULT_MOUNTPATH)
        put("db.name", DB_NAME)
        put("kafka.username", "kafkaUser")
        put("kafka.password", "kafkaPassword")
        put("kafka.bootstrap", EmbeddedKafka.kafkaInstance.brokersURL)
        put("kafka.trustStorePath", "")
        put("kafka.trustStorePassword", "")
    }
}

@KtorExperimentalLocationsAPI
fun Application.componentTest(wireMockServer: WireMockServer) = susebakover(clients = ClientBuilderTest(baseUrl = wireMockServer.baseUrl()).build())

private val e = Base64.getEncoder().encodeToString(Jwt.keys.first.publicExponent.toByteArray())
private val n = Base64.getEncoder().encodeToString(Jwt.keys.first.modulus.toByteArray())
private val defaultJwk = Jwk("key-1234", "RSA", "RS256", null, emptyList(), null, null, null, mapOf("e" to e, "n" to n))
private val defaultJwkConfig = JSONObject("""{"issuer": "azure"}""")
private val defaultJwkClient = object : no.nav.su.se.bakover.client.Jwk {
    override fun config() = defaultJwkConfig
}
private val defaultOAuth = object : OAuth {
    override fun onBehalfOFToken(originalToken: String, otherAppId: String): String = originalToken
    override fun refreshTokens(refreshToken: String): JSONObject = JSONObject("""{"access_token":"abc","refresh_token":"cba"}""")
    override fun token(otherAppId: String): String = "token"
}
private val failingPersonClient = object : PersonOppslag {
    override fun person(ident: Fnr): ClientResponse = ClientResponse(501, "dette var en autogenerert feil fra person")
    override fun aktørId(ident: Fnr): String = throw RuntimeException("Kall mot PDL feilet")
}
private val failingInntektClient = object : InntektOppslag {
    override fun inntekt(ident: Fnr, innloggetSaksbehandlerToken: String, fomDato: String, tomDato: String): ClientResponse = ClientResponse(501, "dette var en autogenerert feil fra inntekt")
}

@KtorExperimentalLocationsAPI
@KtorExperimentalAPI
internal fun Application.usingMocks(
        jwkClient: no.nav.su.se.bakover.client.Jwk = defaultJwkClient,
        jwkConfig: JSONObject = defaultJwkConfig,
        jwkProvider: JwkProvider = JwkProvider { defaultJwk },
        personClient: PersonOppslag = failingPersonClient,
        inntektClient: InntektOppslag = failingInntektClient,
        oAuth: OAuth = defaultOAuth
) {
    susebakover(
            clients = Clients(
                    jwk = jwkClient,
                    oauth = oAuth,
                    personOppslag = personClient,
                    inntektOppslag = inntektClient
            ),
            jwkConfig = jwkConfig,
            jwkProvider = jwkProvider
    )
}

fun TestApplicationEngine.withCorrelationId(
        method: HttpMethod,
        uri: String,
        setup: TestApplicationRequest.() -> Unit = {}
): TestApplicationCall {
    return handleRequest(method, uri) {
        addHeader(XCorrelationId, DEFAULT_CALL_ID)
        setup()
    }
}
