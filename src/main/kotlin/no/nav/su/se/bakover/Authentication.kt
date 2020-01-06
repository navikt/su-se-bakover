package no.nav.su.se.bakover

import com.auth0.jwk.UrlJwkProvider
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.application.log
import io.ktor.auth.Authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import java.net.URL

fun Application.setupAuthentication(
    jwksUrl: String,
    jwtIssuer: String,
    jwtRealm: String,
    requiredGroup: String,
    clientId: String
) {
    install(Authentication) {
        jwt {
            realm = jwtRealm
            verifier(UrlJwkProvider(URL(jwksUrl)), jwtIssuer)
            validate { credential ->
                val validAudience = clientId in credential.payload.audience // should be ... su-se-bakover sin client_id
                val groups = credential.payload.getClaim("groups").asList(String::class.java)
                val validGroup = requiredGroup in groups

                if (validAudience && validGroup) {
                    JWTPrincipal(credential.payload)
                } else {
                    if (!validAudience) log.info("Invalid audience: ${credential.payload.audience}")
                    if (!validGroup) log.info("Subject in groups $groups, but not in required group $requiredGroup")
                    null
                }
            }
        }
    }
}