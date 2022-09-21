package no.nav.su.se.bakover.client.stubs.azure

import no.nav.su.se.bakover.client.azure.AzureAd
import no.nav.su.se.bakover.common.stubs.AuthStubCommonConfig
import org.json.JSONObject

object AzureClientStub : AzureAd {
    override fun onBehalfOfToken(originalToken: String, otherAppId: String): String = originalToken

    override fun jwkConfig(): JSONObject {
        return JSONObject(
            mapOf(
                "authorization_endpoint" to "http://localhost:8080/login",
                "token_endpoint" to "http://localhost:8080/login",
                "issuer" to AuthStubCommonConfig.issuer,
                "end_session_endpoint" to "http://localhost:8080/logout",
                "jwks_uri" to "http://localhost:8080/jwks",
            ),
        )
    }

    override fun getSystemToken(otherAppId: String): String = "etFintSystemtoken"
}
