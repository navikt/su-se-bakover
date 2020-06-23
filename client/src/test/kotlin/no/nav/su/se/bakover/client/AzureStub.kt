package no.nav.su.se.bakover.client


import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import java.net.URLEncoder
import java.nio.charset.Charset
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.*

const val AZURE_WELL_KNOWN_URL = "/.well-known"
const val AZURE_JWKS_PATH = "/keys"
const val AZURE_ISSUER = "azure"
const val AZURE_TOKEN_URL = "/token"
const val AZURE_ON_BEHALF_OF_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer"
const val ON_BEHALF_OF_TOKEN = "ONBEHALFOFTOKEN"

class AzureStub(private val wireMockServer: WireMockServer? = null) {
    private val privateKey: RSAPrivateKey
    private val publicKey: RSAPublicKey

    init {
        wireMockServer?.apply {
            val client = WireMock.create().port(wireMockServer.port()).build()
            WireMock.configureFor(client)
        }

        val keyPair = RSAKeyPairGenerator.generate()
        publicKey = keyPair.first
        privateKey = keyPair.second
    }

    fun keys() = publicKey to privateKey

    fun jwkProvider() = WireMock.get(WireMock.urlPathEqualTo(AZURE_JWKS_PATH)).willReturn(
            WireMock.okJson("""
{
    "keys": [
        {
            "kty": "RSA",
            "alg": "RS256",
            "kid": "key-1234",
            "e": "${String(Base64.getEncoder().encode(publicKey.publicExponent.toByteArray()))}",
            "n": "${String(Base64.getEncoder().encode(publicKey.modulus.toByteArray()))}"
        }
    ]
}
""".trimIndent()
            )
    )

    fun config() = WireMock.get(WireMock.urlPathEqualTo(AZURE_WELL_KNOWN_URL)).willReturn(
            WireMock.okJson("""
{
    "jwks_uri": "${wireMockServer?.baseUrl()}$AZURE_JWKS_PATH",
    "token_endpoint": "${wireMockServer?.baseUrl()}$AZURE_TOKEN_URL",
    "issuer": "$AZURE_ISSUER"
}
""".trimIndent()
            )
    )

    fun onBehalfOfToken() = WireMock.post(WireMock.urlPathEqualTo(AZURE_TOKEN_URL))
            .withRequestBody(WireMock.containing("grant_type=${URLEncoder.encode(AZURE_ON_BEHALF_OF_GRANT_TYPE, Charset.defaultCharset())}"))
            .willReturn(WireMock.okJson("""
{
  "token_type": "Bearer",
  "scope": "exchanged scope",
  "expires_in": 3269,
  "ext_expires_in": 0,
  "access_token": "$ON_BEHALF_OF_TOKEN",
  "refresh_token": "exchanged refresh token"
}
""".trimIndent()
            )
            )

    fun token() = WireMock.post(WireMock.urlPathEqualTo(AZURE_TOKEN_URL))
            .withRequestBody(WireMock.containing("grant_type=client_credentials"))
            .willReturn(WireMock.okJson("""
{
  "token_type": "Bearer",
  "expires_in": 3269,
  "access_token": "$ON_BEHALF_OF_TOKEN"
}
""".trimIndent()
            )
            )
}