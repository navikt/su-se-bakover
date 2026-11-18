package no.nav.su.se.bakover.web

import com.auth0.jwk.JwkException
import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTDecodeException
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTCredential
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.domain.roller.Eksternrolle
import no.nav.su.se.bakover.common.infrastructure.auth.AuthStubCommonConfig
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.web.erLokaltStubToken
import no.nav.su.se.bakover.common.infrastructure.web.getGroupsFromJWT
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.web.stubs.JwkProviderStub
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.TimeUnit

/**
 * @param azureJwkProvider Nøklene til Azure (mock-oauth2 lokalt). Kan overstyres i tester av lokal autentisering.
 */
internal fun Application.configureAuthentication(
    azureAd: AzureAd,
    applicationConfig: ApplicationConfig,
    azureJwkProvider: () -> JwkProvider = { azureJwkProvider(azureAd) },
) {
    val log: Logger = LoggerFactory.getLogger("no.nav.su.se.bakover.web.Application.configureAuthentication()")

    val erLokalt = applicationConfig.runtimeEnvironment == ApplicationConfig.RuntimeEnvironment.Local

    val jwkProvider =
        if (applicationConfig.runtimeEnvironment == ApplicationConfig.RuntimeEnvironment.Test) {
            JwkProviderStub
        } else {
            azureJwkProvider()
        }

    install(Authentication) {
        jwt("jwt") {
            if (erLokalt) {
                // Lokalt godtar vi også tokens som appen selv signerer (dev-rutene i LocalTestDataRoutes).
                verifier { authHeader -> lokalVerifier(authHeader, jwkProvider, azureAd.issuer) }
            } else {
                verifier(jwkProvider, azureAd.issuer)
            }
            realm = "su-se-bakover"
            validate { credentials ->
                log.debug("jwt-auth su-se-framover: Validating token")
                try {
                    requireNotNull(credentials.payload.audience) {
                        "jwt-auth su-se-framover: Missing audience in token"
                    }
                    require(
                        erLokaltStubToken(applicationConfig, credentials.payload) ||
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
                    require(credentials.getRoles().any { it == Eksternrolle.FRIKORT.rolle }) {
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

private fun azureJwkProvider(azureAd: AzureAd): JwkProvider =
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

/**
 * Lokalt kommer tokens fra mock-oauth2 (frontend) og fra appen selv (dev-rutene bruker testhjelpere som signerer med
 * [AuthStubCommonConfig]). Stub-nøkkelen genereres i samme JVM, så bare den lokale appen kan lage slike tokens.
 *
 * Verifisereren velges ut fra nøkkel-id, slik at issuer er bundet til nøkkelen: stub-nøkkelen godtar bare
 * stub-issueren, og mock-oauth2-nøklene godtar bare mock-oauth2-issueren. Et token med stub-issuer er dermed alltid
 * signert med stub-nøkkelen.
 */
private fun lokalVerifier(authHeader: HttpAuthHeader, azureJwkProvider: JwkProvider, azureIssuer: String): JWTVerifier? {
    val token = (authHeader as? HttpAuthHeader.Single)
        ?.takeIf { it.authScheme.equals("Bearer", ignoreCase = true) }
        ?.blob
        ?: return null
    val keyId = try {
        JWT.decode(token).keyId
    } catch (_: JWTDecodeException) {
        return null
    }
    return if (keyId == AuthStubCommonConfig.KEY_ID) {
        JWT.require(Algorithm.RSA256(AuthStubCommonConfig.public, null))
            .withIssuer(AuthStubCommonConfig.ISSUER)
            .build()
    } else {
        val nøkkel = try {
            azureJwkProvider.get(keyId).publicKey as? RSAPublicKey
        } catch (_: JwkException) {
            null
        } ?: return null
        JWT.require(Algorithm.RSA256(nøkkel, null))
            .withIssuer(azureIssuer)
            .build()
    }
}
