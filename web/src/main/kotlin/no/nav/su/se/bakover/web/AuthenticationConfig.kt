package no.nav.su.se.bakover.web

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTCredential
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.domain.auth.TokenOppslag
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.web.getGroupsFromJWT
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.web.stubs.JwkProviderStub
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.concurrent.TimeUnit

internal fun Application.configureAuthentication(
    azureAd: AzureAd,
    applicationConfig: ApplicationConfig,
    tokenOppslag: TokenOppslag,
) {
    val log: Logger = LoggerFactory.getLogger("no.nav.su.se.bakover.web.Application.configureAuthentication()")

    val jwkProvider =
        if (applicationConfig.runtimeEnvironment == ApplicationConfig.RuntimeEnvironment.Test) {
            JwkProviderStub
        } else {
            JwkProviderBuilder(
                URI(azureAd.jwksUri).toURL(),
            )
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
            JwkProviderBuilder(URI(stsJwkConfig.getString("jwks_uri")).toURL())
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
                log.debug("jwt-auth su-se-framover: Validating token")
                try {
                    requireNotNull(credentials.payload.audience) {
                        "jwt-auth su-se-framover: Missing audience in token"
                    }
                    require(
                        credentials.payload.audience.any {
                            it == applicationConfig.azure.clientId ||
                                // TODO jah, ia: En tilpasning for mock-oauth. Vi kan f.eks. bytte til en mer Azure-spesifikk mock.
                                it == """api://${applicationConfig.azure.clientId}/.default"""
                        },
                    ) {
                        "jwt-auth su-se-framover: Valid audience not found in claims"
                    }
                    val allowedGroups = applicationConfig.azure.groups.asList()
                    require(getGroupsFromJWT(applicationConfig, credentials).any { allowedGroups.contains(it) }) {
                        "jwt-auth su-se-framover: Valid group not found in claims"
                    }
                    JWTPrincipal(credentials.payload)
                } catch (e: Throwable) {
                    log.debug("jwt-auth su-se-framover: Validation error during authentication", e)
                    null
                }
            }
        }
        jwt("frikort") {
            log.debug("jwt-auth frikort sts: Verifiserer frikort sts-token")
            verifier(jwkStsProvider, stsJwkConfig.getString("issuer"))
            log.debug("jwt-auth frikort sts: Verifisert frikort sts-token mot issuer")
            realm = "su-se-bakover"
            validate { credentials ->
                log.debug("jwt-auth frikort sts: Validating token")
                if (credentials.payload.subject !in applicationConfig.frikort.serviceUsername && credentials.payload.subject != applicationConfig.serviceUser.username) {
                    log.debug("jwt-auth frikort sts: Invalid subject")
                    null
                } else {
                    log.debug("jwt-auth frikort sts: Gyldig token.")
                    JWTPrincipal(credentials.payload)
                }
            }
        }
        jwt("frikort2") {
            verifier(jwkProvider, azureAd.issuer)
            realm = "su-se-bakover"
            validate { credentials ->
                log.debug("jwt-auth frikort azure: Validating token")
                try {
                    val printableToken = credentials.payload.claims.mapValues { it.value.asMap() }.toString()
                    requireNotNull(credentials.payload.audience) {
                        sikkerLogg.debug("jwt-auth frikort azure: Mangler audience-token. Token: $printableToken")
                        "jwt-auth frikort azure: Mangler audience-token. Se sikkerlogg for mer informasjon."
                    }
                    require(credentials.payload.audience.any { it == applicationConfig.azure.clientId }) {
                        sikkerLogg.debug("jwt-auth frikort azure: audience-token var ikke gyldig. Forventet audience: ${applicationConfig.azure.clientId}. Token: $printableToken")
                        "jwt-auth frikort azure: audience-token var ikke gyldig. Se sikkerlogg for mer informasjon."
                    }
                    require(credentials.getRoles().any { it == "frikort" }) {
                        sikkerLogg.debug("jwt-auth frikort azure: Forventet claim 'roles' inneholder 'frikort'. Token: $printableToken")
                        "jwt-auth frikort azure: Forventet claim 'roles' inneholder 'frikort'. Se sikkerlogg for mer informasjon."
                    }
                    log.debug("jwt-auth frikort azure: Gyldig token.")
                    JWTPrincipal(credentials.payload)
                } catch (e: Throwable) {
                    log.debug("jwt-auth frikort azure: Validation error during authentication", e)
                    null
                }
            }
        }
    }
}

fun JWTCredential.getRoles(): List<String> {
    return this.payload.getClaim("roles")?.asList(String::class.java) ?: emptyList()
}
