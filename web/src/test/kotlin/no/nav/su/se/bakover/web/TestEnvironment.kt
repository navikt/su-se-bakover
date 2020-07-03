package no.nav.su.se.bakover.web

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkProvider
import io.ktor.application.Application
import io.ktor.config.MapApplicationConfig
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.testing.TestApplicationCall
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.handleRequest
import io.ktor.util.KtorExperimentalAPI
import java.util.Base64
import no.nav.su.se.bakover.client.HttpClients
import no.nav.su.se.bakover.client.HttpClientBuilder
import no.nav.su.se.bakover.client.inntekt.InntektOppslag
import no.nav.su.se.bakover.client.azure.OAuth
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.client.SuKafkaClient
import no.nav.su.se.bakover.client.stubs.inntekt.InntektOppslagStub
import no.nav.su.se.bakover.client.stubs.person.PersonOppslagStub
import no.nav.su.se.bakover.client.stubs.SuKafkaClientStub
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.ObjectRepo
import org.json.JSONObject

const val AZURE_CLIENT_ID = "clientId"
const val AZURE_CLIENT_SECRET = "secret"
const val AZURE_REQUIRED_GROUP = "su-group"
const val AZURE_ISSUER = "azure"
const val AZURE_BACKEND_CALLBACK_URL = "/callback"
const val SUBJECT = "enSaksbehandler"
const val SU_FRONTEND_REDIRECT_URL = "auth/complete"
const val SU_FRONTEND_ORIGIN = "localhost"
const val DEFAULT_CALL_ID = "her skulle vi sikkert hatt en korrelasjonsid"

@KtorExperimentalAPI
internal fun Application.testEnv() {
    (environment.config as MapApplicationConfig).apply {
        put("cors.allow.origin", SU_FRONTEND_ORIGIN)
        put("integrations.suSeFramover.redirectUrl", SU_FRONTEND_REDIRECT_URL)
        put("azure.requiredGroup", AZURE_REQUIRED_GROUP)
        put("azure.clientId", AZURE_CLIENT_ID)
        put("azure.clientSecret", AZURE_CLIENT_SECRET)
        put("azure.backendCallbackUrl", AZURE_BACKEND_CALLBACK_URL)
        put("issuer", AZURE_ISSUER)
    }
}

internal fun Application.testSusebakover(
    httpClients: HttpClients = buildClients(),
    jwkProvider: JwkProvider = JwkProviderStub,
    kafkaClient: SuKafkaClient = SuKafkaClientStub(),
    databaseRepo: ObjectRepo = DatabaseBuilder.build(EmbeddedDatabase.instance())
) {
    return susebakover(
        httpClients = httpClients,
        jwkProvider = jwkProvider,
        kafkaClient = kafkaClient,
        databaseRepo = databaseRepo
    )
}

internal fun buildClients(
    azure: OAuth = OauthStub(),
    personOppslag: PersonOppslag = PersonOppslagStub,
    inntektOppslag: InntektOppslag = InntektOppslagStub
): HttpClients {
    return HttpClientBuilder.build(azure, personOppslag, inntektOppslag)
}

internal object JwkProviderStub : JwkProvider {
    override fun get(keyId: String?) = Jwk(
        "key-1234",
        "RSA",
        "RS256",
        null,
        emptyList(),
        null,
        null,
        null,
        mapOf(
            "e" to String(Base64.getEncoder().encode(Jwt.keys.first.publicExponent.toByteArray())),
            "n" to String(Base64.getEncoder().encode(Jwt.keys.first.modulus.toByteArray()))
        )
    )
}

internal class OauthStub : OAuth {
    override fun onBehalfOFToken(originalToken: String, otherAppId: String) = "ONBEHALFOFTOKEN"
    override fun refreshTokens(refreshToken: String) = JSONObject("""{"access_token":"abc","refresh_token":"cba"}""")
    override fun jwkConfig() = JSONObject(
        """
            {
                "jwks_uri": "http://localhost/keys",
                "token_endpoint": "http://localhost/token",
                "issuer": "azure"
            }
        """.trimIndent()
    )
}

fun TestApplicationEngine.defaultRequest(
    method: HttpMethod,
    uri: String,
    setup: TestApplicationRequest.() -> Unit = {}
): TestApplicationCall {
    return handleRequest(method, uri) {
        addHeader(HttpHeaders.XCorrelationId, DEFAULT_CALL_ID)
        addHeader(HttpHeaders.Authorization, Jwt.create())
        setup()
    }
}
