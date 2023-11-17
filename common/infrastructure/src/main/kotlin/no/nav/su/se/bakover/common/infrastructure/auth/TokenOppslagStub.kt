package no.nav.su.se.bakover.common.infrastructure.auth

import no.nav.su.se.bakover.common.domain.auth.AccessToken
import no.nav.su.se.bakover.common.domain.auth.TokenOppslag
import org.json.JSONObject

data object TokenOppslagStub : TokenOppslag {
    override fun token() = AccessToken("token")
    override fun jwkConfig(): JSONObject {
        return JSONObject(
            mapOf(
                "authorization_endpoint" to "http://localhost:8080/login",
                "token_endpoint" to "http://localhost:8080/login",
                "issuer" to AuthStubCommonConfig.ISSUER,
                "end_session_endpoint" to "http://localhost:8080/logout",
                "jwks_uri" to "http://localhost:8080/jwks",
            ),
        )
    }
}
