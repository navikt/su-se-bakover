package no.nav.su.se.bakover.client.stubs.sts

import no.nav.su.se.bakover.client.AccessToken
import no.nav.su.se.bakover.client.sts.TokenOppslag
import no.nav.su.se.bakover.common.infrastructure.auth.AuthStubCommonConfig
import org.json.JSONObject

object TokenOppslagStub : TokenOppslag {
    override fun token() = AccessToken("token")
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
}
