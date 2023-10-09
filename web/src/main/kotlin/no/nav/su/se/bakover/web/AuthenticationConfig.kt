package no.nav.su.se.bakover.web

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import no.nav.su.se.bakover.client.sts.TokenOppslag
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.web.getGroupsFromJWT
import no.nav.su.se.bakover.web.stubs.JwkProviderStub
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.util.concurrent.TimeUnit

internal fun Application.configureAuthentication(
    azureAd: AzureAd,
    applicationConfig: ApplicationConfig,
    tokenOppslag: TokenOppslag,
) {
    val log: Logger = LoggerFactory.getLogger("Application.configureAuthentication()")

    val jwkProvider =
        if (applicationConfig.runtimeEnvironment == ApplicationConfig.RuntimeEnvironment.Test) {
            JwkProviderStub
        } else {
            JwkProviderBuilder(URL(azureAd.jwksUri))
                .cached(10, 24, TimeUnit.HOURS) // cache up to 10 JWKs for 24 hours
                .rateLimited(
                    10,
                    1,
                    TimeUnit.MINUTES,
                ) // if not cached, only allow max 10 different keys per minute to be fetched from external provider
                .build()
        }

    val stsJwkConfig = tokenOppslag.jwkConfig()
    val jwkStsProvider =
        if (applicationConfig.runtimeEnvironment == ApplicationConfig.RuntimeEnvironment.Test || applicationConfig.frikort.useStubForSts) {
            JwkProviderStub
        } else {
            JwkProviderBuilder(URL(stsJwkConfig.getString("jwks_uri")))
                .cached(10, 24, TimeUnit.HOURS) // cache up to 10 JWKs for 24 hours
                .rateLimited(
                    10,
                    1,
                    TimeUnit.MINUTES,
                ) // if not cached, only allow max 10 different keys per minute to be fetched from external provider
                .build()
        }

    install(Authentication) {
        jwt("jwt") {
            verifier(jwkProvider, azureAd.issuer)
            realm = "su-se-bakover"
            validate { credentials ->
                try {
                    requireNotNull(credentials.payload.audience) {
                        "Auth: Missing audience in token"
                    }
                    require(
                        credentials.payload.audience.any {
                            it == applicationConfig.azure.clientId ||
                                // TODO jah, ia: En tilpasning for mock-oauth. Vi kan f.eks. bytte til en mer Azure-spesifikk mock.
                                it == """api://${applicationConfig.azure.clientId}/.default"""
                        },
                    ) {
                        "Auth: Valid audience not found in claims"
                    }
                    val allowedGroups = applicationConfig.azure.groups.asList()
                    require(getGroupsFromJWT(applicationConfig, credentials).any { allowedGroups.contains(it) }) {
                        "Auth: Valid group not found in claims"
                    }
                    JWTPrincipal(credentials.payload)
                } catch (e: Throwable) {
                    log.debug("Auth: Validation error during authentication", e)
                    null
                }
            }
        }
        jwt("frikort") {
            verifier(jwkStsProvider, stsJwkConfig.getString("issuer"))
            realm = "su-se-bakover"
            validate { credentials ->
                if (credentials.payload.subject !in applicationConfig.frikort.serviceUsername && credentials.payload.subject != applicationConfig.serviceUser.username) {
                    log.debug("Frikort Auth: Invalid subject")
                    null
                } else {
                    JWTPrincipal(credentials.payload)
                }
            }
        }
    }
}
