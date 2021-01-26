package no.nav.su.se.bakover.web

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import no.nav.su.se.bakover.client.azure.OAuth
import no.nav.su.se.bakover.common.ApplicationConfig
import java.net.URL
import java.util.concurrent.TimeUnit

internal fun Application.configureAuthentication(
    oAuth: OAuth,
    applicationConfig: ApplicationConfig
) {
    val jwkConfig = oAuth.jwkConfig()
    val jwkProvider =
        JwkProviderBuilder(URL(jwkConfig.getString("jwks_uri")))
            .cached(10, 24, TimeUnit.HOURS) // cache up to 10 JWKs for 24 hours
            .rateLimited(
                10,
                1,
                TimeUnit.MINUTES
            ) // if not cached, only allow max 10 different keys per minute to be fetched from external provider
            .build()

    install(Authentication) {
        jwt("jwt") {
            verifier(jwkProvider, jwkConfig.getString("issuer"))
            realm = "su-se-bakover"
            validate { credentials ->
                try {
                    requireNotNull(credentials.payload.audience) {
                        "Auth: Missing audience in token"
                    }
                    require(credentials.payload.audience.contains(applicationConfig.azure.clientId)) {
                        "Auth: Valid audience not found in claims"
                    }
                    val allowedGroups = applicationConfig.azure.groups.asList()
                    require(getGroupsFromJWT(applicationConfig, credentials).any { allowedGroups.contains(it) }) {
                        "Auth: Valid group not found in claims"
                    }
                    JWTPrincipal(credentials.payload)
                } catch (e: Throwable) {
                    null
                }
            }
        }
    }
}
