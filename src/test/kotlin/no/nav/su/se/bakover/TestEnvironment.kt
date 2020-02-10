package no.nav.su.se.bakover

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkProvider
import com.github.tomakehurst.wiremock.WireMockServer
import com.opentable.db.postgres.embedded.EmbeddedPostgres
import io.ktor.application.Application
import io.ktor.config.MapApplicationConfig
import io.ktor.http.HttpHeaders.XRequestId
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.server.testing.TestApplicationCall
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.handleRequest
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.azure.TokenExchange
import no.nav.su.se.bakover.db.DataSourceBuilder.Role.Admin
import no.nav.su.se.bakover.inntekt.InntektOppslag
import no.nav.su.se.bakover.person.PersonOppslag
import org.json.JSONObject
import java.util.*

const val AZURE_CLIENT_ID = "clientId"
const val AZURE_CLIENT_SECRET = "secret"
const val AZURE_REQUIRED_GROUP = "su-group"
const val AZURE_WELL_KNOWN_URL = "/.well-known"
const val AZURE_JWKS_PATH = "/keys"
const val AZURE_ISSUER = "azure"
const val AZURE_BACKEND_CALLBACK_URL = "/callback"
const val AZURE_TOKEN_URL = "/token"
const val SUBJECT = "enSaksbehandler"
const val SU_PERSON_AZURE_CLIENT_ID = "personClientId"
const val SU_INNTEKT_AZURE_CLIENT_ID = "inntektClientId"
const val SU_FRONTEND_REDIRECT_URL = "auth/complete"
const val SU_FRONTEND_ORIGIN = "localhost"
const val DEFAULT_CALL_ID = "callId"
const val DB_USERNAME = "postgres"
const val DB_PASSWORD = "postgres"
const val DB_VAULT_MOUNTPATH = ""
const val DB_NAME = "postgres"


@KtorExperimentalAPI
fun Application.testEnv(wireMockServer: WireMockServer? = null) {
    val baseUrl = wireMockServer?.baseUrl() ?: SU_FRONTEND_ORIGIN

    val embeddedPostgres = configureEmbeddedPostgres()

    (environment.config as MapApplicationConfig).apply {
        put("cors.allow.origin", SU_FRONTEND_ORIGIN)
        put("integrations.suPerson.url", baseUrl)
        put("integrations.suPerson.clientId", SU_PERSON_AZURE_CLIENT_ID)
        put("integrations.suInntekt.url", baseUrl)
        put("integrations.suInntekt.clientId", SU_INNTEKT_AZURE_CLIENT_ID)
        put("integrations.suSeFramover.redirectUrl", "$baseUrl$SU_FRONTEND_REDIRECT_URL")
        put("azure.requiredGroup", AZURE_REQUIRED_GROUP)
        put("azure.clientId", AZURE_CLIENT_ID)
        put("azure.clientSecret", AZURE_CLIENT_SECRET)
        put("azure.wellknownUrl", "$baseUrl$AZURE_WELL_KNOWN_URL")
        put("azure.backendCallbackUrl", "$baseUrl$AZURE_BACKEND_CALLBACK_URL")
        put("issuer", AZURE_ISSUER)
        put("db.username", DB_USERNAME)
        put("db.password", DB_PASSWORD)
        put("db.jdbcUrl", embeddedPostgres.getJdbcUrl(DB_USERNAME, DB_NAME))
        put("db.vaultMountPath", DB_VAULT_MOUNTPATH)
        put("db.name", DB_NAME)
    }
}

fun configureEmbeddedPostgres(): EmbeddedPostgres {
    val embeddedPostgres = EmbeddedPostgres.builder()
            .setLocaleConfig("locale", "en_US.UTF-8") //Feiler med Process [/var/folders/l2/q666s90d237c37rwkw9x71bw0000gn/T/embedded-pg/PG-73dc0043fe7bdb624d5e8726bc457b7e/bin/initdb ...  hvis denne ikke er med.
            .start()
    embeddedPostgres.getDatabase(DB_NAME, DB_NAME).connection.prepareStatement("""create role "$DB_NAME-$Admin" """).execute()//Må legge til rollen i databasen for at Flyway skal få kjørt migrering.
    return embeddedPostgres
}

val jwtStub = JwtStub()

private val e = Base64.getEncoder().encodeToString(jwtStub.publicKey.publicExponent.toByteArray())
private val n = Base64.getEncoder().encodeToString(jwtStub.publicKey.modulus.toByteArray())
private val defaultJwk = Jwk("key-1234", "RSA", "RS256", null, emptyList(), null, null, null, mapOf("e" to e, "n" to n))
private val defaultJwkConfig = JSONObject("""{"issuer": "azure"}""")
private val defaultAzure = object : TokenExchange {
    override fun onBehalfOFToken(originalToken: String, otherAppId: String): String = originalToken
}
private val failingPersonClient = object : PersonOppslag {
    override fun person(ident: String, innloggetSaksbehandlerToken: String): Resultat = Resultat.resultatMedMelding(HttpStatusCode.fromValue(501), "dette var en autogenerert feil fra person")
}
private val failingInntektClient = object : InntektOppslag {
    override fun inntekt(ident: String, innloggetSaksbehandlerToken: String, fomDato: String, tomDato: String): Resultat = Resultat.resultatMedMelding(HttpStatusCode.fromValue(501), "dette var en autogenerert feil fra inntekt")
}

@KtorExperimentalLocationsAPI
@KtorExperimentalAPI
internal fun Application.usingMocks(
        jwkConfig: JSONObject = defaultJwkConfig,
        jwkProvider: JwkProvider = JwkProvider { defaultJwk },
        personClient: PersonOppslag = failingPersonClient,
        inntektClient: InntektOppslag = failingInntektClient,
        azureClient: TokenExchange = defaultAzure
) {
    susebakover(
            jwkConfig = jwkConfig,
            jwkProvider = jwkProvider,
            tokenExchange = azureClient,
            personOppslag = personClient,
            inntektOppslag = inntektClient
    )
}

fun TestApplicationEngine.withCallId(
        method: HttpMethod,
        uri: String,
        setup: TestApplicationRequest.() -> Unit = {}
): TestApplicationCall {
    return handleRequest(method, uri) {
        addHeader(XRequestId, DEFAULT_CALL_ID)
        setup()
    }
}
