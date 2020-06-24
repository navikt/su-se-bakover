package no.nav.su.se.bakover.web

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkProvider
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import no.nav.su.se.bakover.client.*
import no.nav.su.se.bakover.client.stubs.PersonOppslagStub
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.util.*

internal open class ComponentTest {
    internal var jwt = "Bearer is not set"

    internal val wireMockServer: WireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())

    fun buildClients(
            azure: OAuth = OauthStub(),
            personOppslag: PersonOppslag = PersonOppslagStub,
            inntektOppslag: InntektOppslag = ClientBuilder.inntekt(baseUrl = wireMockServer.baseUrl(), oAuth = azure, personOppslag = personOppslag)
    ): Clients {
        return ClientBuilder.build(azure, personOppslag, inntektOppslag)
    }

    @BeforeEach
    fun start() {
        wireMockServer.start()
        jwt = Jwt.create()
    }

    @AfterEach
    fun stop() {
        wireMockServer.stop()
    }

    object JwkProviderStub : JwkProvider {
        override fun get(keyId: String?) = Jwk(
                "key-1234",
                "RSA",
                "RS256",
                null,
                emptyList(),
                null,
                null,
                null,
                mapOf("e" to String(Base64.getEncoder().encode(Jwt.keys.first.publicExponent.toByteArray())), "n" to String(Base64.getEncoder().encode(Jwt.keys.first.modulus.toByteArray())))
        )
    }

    inner class OauthStub : OAuth {
        override fun onBehalfOFToken(originalToken: String, otherAppId: String) = "ONBEHALFOFTOKEN"
        override fun refreshTokens(refreshToken: String) = TODO("Not yet implemented")
        override fun jwkConfig() = JSONObject("""
            {
                "jwks_uri": "${wireMockServer.baseUrl()}/keys",
                "token_endpoint": "${wireMockServer.baseUrl()}/token",
                "issuer": "azure"
            }
        """.trimIndent())
    }
}