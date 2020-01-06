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
    jwtRealm: String
) {
    install(Authentication) {
        jwt {
            realm = jwtRealm
            verifier(UrlJwkProvider(URL(jwksUrl)), jwtIssuer)
            validate { credential ->
                val validAudience = true
                val validSubject = true

                if (validAudience && validSubject) {
                    JWTPrincipal(credential.payload)
                } else {
                    if (!validAudience) log.info("Invalid audience: ${credential.payload.audience}")
                    if (!validSubject) log.info("Invalid subject: ${credential.payload.subject}")
                    null
                }
            }
        }
    }
}