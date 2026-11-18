package no.nav.su.se.bakover.web

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkProvider
import com.auth0.jwk.SigningKeyNotFoundException
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.infrastructure.auth.AuthStubCommonConfig
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig.RuntimeEnvironment
import no.nav.su.se.bakover.common.infrastructure.web.erLokaltStubToken
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.withUser
import no.nav.su.se.bakover.test.applicationConfig
import no.nav.su.se.bakover.test.jwt.asBearerToken
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import java.util.Date

/**
 * Lokalt godtar appen både tokens fra mock-oauth2 (frontend) og tokens appen selv signerer med
 * [AuthStubCommonConfig] (dev-rutene). [AuthenticationTest] kjører i [RuntimeEnvironment.Test] og dekker ikke dette.
 */
internal class LokalAutentiseringTest {

    @Test
    fun `stub-token fra dev-hjelperne godtas lokalt med hjelpernes audience`() {
        medApp(RuntimeEnvironment.Local) {
            hentMeg(stubToken(audience = HJELPER_AUDIENCE)).apply {
                status shouldBe OK
                bodyAsText() shouldBe "Z990Lokal|Brukerens navn"
            }
        }
    }

    @Test
    fun `stub-token gir ulike identer for saksbehandler og attestant lokalt`() {
        medApp(RuntimeEnvironment.Local) {
            hentMeg(stubToken(navIdent = "Z111111", navn = "Saks Behandler")).bodyAsText() shouldBe "Z111111|Saks Behandler"
            hentMeg(stubToken(navIdent = "attestant", navn = "Atte Stant")).bodyAsText() shouldBe "attestant|Atte Stant"
        }
    }

    @Test
    fun `mock-oauth2-token beholder frontend-identiteten lokalt`() {
        medApp(RuntimeEnvironment.Local) {
            hentMeg(mockOauth2Token()).apply {
                status shouldBe OK
                bodyAsText() shouldBe "Z9999991|Ulrik Utvikler"
            }
        }
    }

    @Test
    fun `mock-oauth2-token med feil audience avvises lokalt`() {
        medApp(RuntimeEnvironment.Local) {
            hentMeg(mockOauth2Token(audience = "feil-audience")).status shouldBe Unauthorized
        }
    }

    @Test
    fun `mock-oauth2-token med ukjent issuer avvises lokalt`() {
        medApp(RuntimeEnvironment.Local) {
            hentMeg(mockOauth2Token(issuer = "http://ukjent-issuer")).status shouldBe Unauthorized
        }
    }

    @Test
    fun `token med stub-nøkkel-id men feil signatur avvises lokalt`() {
        medApp(RuntimeEnvironment.Local) {
            val forfalsket = JWT.create()
                .withKeyId(AuthStubCommonConfig.KEY_ID)
                .withIssuer(AuthStubCommonConfig.ISSUER)
                .withAudience(HJELPER_AUDIENCE)
                .withClaim("NAVident", "Z123456")
                .withClaim("name", "Falsk Bruker")
                .withExpiresAt(Date(Long.MAX_VALUE))
                .sign(Algorithm.RSA256(mockOauth2Public, mockOauth2Private))
            hentMeg(forfalsket).status shouldBe Unauthorized
        }
    }

    @Test
    fun `mock-oauth2-token med stub-issuer avvises lokalt`() {
        medApp(RuntimeEnvironment.Local) {
            // Stub-issueren er bundet til stub-nøkkelen, så mock-oauth2 kan ikke utgi seg for å være appen selv.
            hentMeg(mockOauth2Token(issuer = AuthStubCommonConfig.ISSUER, audience = HJELPER_AUDIENCE))
                .status shouldBe Unauthorized
            hentMeg(mockOauth2Token(issuer = AuthStubCommonConfig.ISSUER)).status shouldBe Unauthorized
        }
    }

    @Test
    fun `utgått stub-token avvises lokalt`() {
        medApp(RuntimeEnvironment.Local) {
            hentMeg(jwtStub.createJwtToken(audience = HJELPER_AUDIENCE, expiresAt = Date(0))).status shouldBe Unauthorized
        }
    }

    @Test
    fun `token uten nøkkel-id avvises lokalt`() {
        medApp(RuntimeEnvironment.Local) {
            val utenKeyId = JWT.create()
                .withIssuer(MOCK_OAUTH2_ISSUER)
                .withAudience(config(RuntimeEnvironment.Local).azure.clientId)
                .withExpiresAt(Date(Long.MAX_VALUE))
                .sign(Algorithm.RSA256(mockOauth2Public, mockOauth2Private))
            hentMeg(utenKeyId).status shouldBe Unauthorized
        }
    }

    @Test
    fun `audience-unntaket for stub-token gjelder ikke utenfor Local`() {
        medApp(RuntimeEnvironment.Test) {
            hentMeg(stubToken(audience = HJELPER_AUDIENCE)).status shouldBe Unauthorized
        }
    }

    @Test
    fun `stub-token avvises i Nais-miljøer`() {
        listOf(RuntimeEnvironment.Dev, RuntimeEnvironment.Prod).forEach { miljø ->
            medApp(miljø) {
                hentMeg(stubToken()).status shouldBe Unauthorized
                hentMeg(stubToken(audience = HJELPER_AUDIENCE)).status shouldBe Unauthorized
            }
        }
    }

    @Test
    fun `stub-token regnes bare som lokalt stub-token i Local`() {
        val token = JWT.decode(stubToken())
        RuntimeEnvironment.entries.forEach { miljø ->
            erLokaltStubToken(config(miljø), token) shouldBe (miljø == RuntimeEnvironment.Local)
        }
    }

    private companion object {
        const val HJELPER_AUDIENCE = "dev-hjelper"
        const val MOCK_OAUTH2_ISSUER = "http://localhost:4321/default"
        const val MOCK_OAUTH2_KEY_ID = "mock-oauth2-key"

        private val mockOauth2Nøkler = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.genKeyPair()
        val mockOauth2Public = mockOauth2Nøkler.public as RSAPublicKey
        val mockOauth2Private = mockOauth2Nøkler.private as RSAPrivateKey

        val mockOauth2JwkProvider = JwkProvider { keyId ->
            if (keyId != MOCK_OAUTH2_KEY_ID) throw SigningKeyNotFoundException("Ukjent nøkkel $keyId", null)
            Jwk.fromValues(
                mapOf(
                    "kid" to MOCK_OAUTH2_KEY_ID,
                    "kty" to "RSA",
                    "alg" to "RS256",
                    "e" to Base64.getUrlEncoder().encodeToString(mockOauth2Public.publicExponent.toByteArray()),
                    "n" to Base64.getUrlEncoder().encodeToString(mockOauth2Public.modulus.toByteArray()),
                ),
            )
        }

        val mockOauth2AzureAd = object : AzureAd {
            override fun onBehalfOfToken(originalToken: String, otherAppId: String) = originalToken
            override val jwksUri = "http://brukes-ikke/jwks"
            override fun getSystemToken(otherAppId: String) = "token"
            override val issuer = MOCK_OAUTH2_ISSUER
        }

        fun config(miljø: RuntimeEnvironment): ApplicationConfig = applicationConfig().copy(runtimeEnvironment = miljø)

        fun stubToken(
            audience: String = config(RuntimeEnvironment.Local).azure.clientId,
            navIdent: String = "Z990Lokal",
            navn: String = "Brukerens navn",
        ): String = jwtStub.createJwtToken(audience = audience, navIdent = navIdent, navn = navn)

        fun mockOauth2Token(
            issuer: String = MOCK_OAUTH2_ISSUER,
            audience: String = config(RuntimeEnvironment.Local).azure.clientId,
        ): String = JWT.create()
            .withKeyId(MOCK_OAUTH2_KEY_ID)
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("NAVident", "Z990000")
            .withClaim("name", "Lokal Utvikler")
            .withExpiresAt(Date(Long.MAX_VALUE))
            .sign(Algorithm.RSA256(mockOauth2Public, mockOauth2Private))

        fun Application.medMeg(miljø: RuntimeEnvironment) {
            val config = config(miljø)
            configureAuthentication(mockOauth2AzureAd, config) { mockOauth2JwkProvider }
            routing {
                authenticate("jwt") {
                    withUser(config) {
                        get(SECURE_ENDPOINT) {
                            call.respondText("${call.suUserContext.navIdent}|${call.suUserContext.navn}")
                        }
                    }
                }
            }
        }

        fun medApp(miljø: RuntimeEnvironment, test: suspend ApplicationTestBuilder.() -> Unit) {
            testApplication {
                application { medMeg(miljø) }
                test()
            }
        }

        suspend fun ApplicationTestBuilder.hentMeg(token: String): HttpResponse =
            client.get(SECURE_ENDPOINT) { header(Authorization, token.asBearerToken()) }
    }
}
