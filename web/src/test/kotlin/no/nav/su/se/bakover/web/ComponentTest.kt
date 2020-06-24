package no.nav.su.se.bakover.web

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkProvider
import no.nav.su.se.bakover.client.*
import no.nav.su.se.bakover.client.stubs.InntektOppslagStub
import no.nav.su.se.bakover.client.stubs.PersonOppslagStub
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import java.util.*

internal open class ComponentTest {
    internal var jwt = "Bearer is not set"

    fun buildClients(
            azure: OAuth = OauthStub(),
            personOppslag: PersonOppslag = PersonOppslagStub,
            inntektOppslag: InntektOppslag = InntektOppslagStub
    ): Clients {
        return ClientBuilder.build(azure, personOppslag, inntektOppslag)
    }

    @BeforeEach
    fun start() {
        jwt = Jwt.create()
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
                "jwks_uri": "http://localhost/keys",
                "token_endpoint": "http://localhost/token",
                "issuer": "azure"
            }
        """.trimIndent())
    }
}