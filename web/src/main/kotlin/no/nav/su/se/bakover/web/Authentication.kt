package no.nav.su.se.bakover.web

import com.auth0.jwk.JwkProvider
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.application.log
import io.ktor.auth.Authentication
import io.ktor.auth.AuthenticationFailedCause
import io.ktor.auth.OAuthAccessTokenResponse
import io.ktor.auth.OAuthServerSettings
import io.ktor.auth.UnauthorizedResponse
import io.ktor.auth.authenticate
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import io.ktor.auth.oauth
import io.ktor.client.HttpClient
import io.ktor.config.ApplicationConfig
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.request.ApplicationRequest
import io.ktor.response.header
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.client.azure.OAuth
import org.json.JSONObject
import java.time.Instant
import java.util.Base64.getDecoder
import java.util.Date

@KtorExperimentalAPI
internal fun Application.setupAuthentication(
    jwkConfig: JSONObject,
    jwkProvider: JwkProvider,
    config: ApplicationConfig,
    httpClient: HttpClient
) {
    install(Authentication) {
        oauth("azure") {
            client = httpClient
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                        name = "azure",
                        authorizeUrl = jwkConfig.getString("authorization_endpoint"),
                        accessTokenUrl = jwkConfig.getString("token_endpoint"),
                        requestMethod = Post,
                        clientId = config.getProperty("azure.clientId"),
                        clientSecret = config.getProperty("azure.clientSecret"),
                        defaultScopes = listOf("${config.getProperty("azure.clientId")}/.default", "openid", "offline_access")
                )
            }
            urlProvider = { config.getProperty("azure.backendCallbackUrl") }
        }

        jwt("jwt") {
            verifier(jwkProvider, jwkConfig.getString("issuer"))
            validate { credential ->
                val validAudience = config.getProperty("azure.clientId") in credential.payload.audience
                val groups = credential.payload.getClaim("groups").asList(String::class.java)
                val validGroup = config.getProperty("azure.requiredGroup") in groups

                if (validAudience && validGroup) {
                    JWTPrincipal(credential.payload)
                } else {
                    if (!validAudience) log.info("Invalid audience: ${credential.payload.audience}")
                    if (!validGroup) log.info("Subject in groups $groups, but not in required group")
                    null
                }
            }
            challenge { defaultScheme, realm ->
                val errors: Map<Any, AuthenticationFailedCause> = call.authentication.errors
                when (errors.values.singleOrNull()) {
                    AuthenticationFailedCause.InvalidCredentials -> {
                        getExpiry(call.request)
                                ?.takeIf(::tokenHasExpired)
                                ?.also {
                                    call.respond(HttpStatusCode.Unauthorized, errorMessage(it).also(log::debug))
                                } ?: call.respond(HttpStatusCode.Forbidden)
                    }
                    else ->
                        call.respond(UnauthorizedResponse(
                            HttpAuthHeader.Parameterized(
                                defaultScheme,
                                mapOf(HttpAuthHeader.Parameters.Realm to realm)
                        )))
                }
            }
        }
    }
}

private fun getExpiry(request: ApplicationRequest) =
        request.headers["Authorization"]?.substringAfter("Bearer ")
                ?.let { String(getDecoder().decode(it.split(".")[1]), Charsets.UTF_8) }
                ?.let(::JSONObject)
                ?.let { it["exp"] as Int }
                ?.let { Date.from(Instant.ofEpochSecond(it.toLong())) }

private fun tokenHasExpired(date: Date) = date.before(Date.from(Instant.now()))

private fun errorMessage(date: Date) =
        if (tokenHasExpired(date)) {
            "The token expired at $date"
        } else ""

internal const val AUTH_CALLBACK_PATH = "/callback"

internal fun Application.oauthRoutes(frontendRedirectUrl: String, oAuth: OAuth) {
    routing {
        authenticate("azure") {
            get("/login") {
                // Initiate login sequence
            }
            get(AUTH_CALLBACK_PATH) {
                val tokenResponse = call.authentication.principal<OAuthAccessTokenResponse>() as OAuthAccessTokenResponse.OAuth2
                call.respondRedirect("$frontendRedirectUrl#${tokenResponse.accessToken}#${tokenResponse.refreshToken}")
            }
        }
        get("/auth/refresh") {
            call.request.headers["refresh_token"]?.let {
                val refreshedTokens = oAuth.refreshTokens(it)
                call.response.header("access_token", refreshedTokens.getString("access_token"))
                call.response.header("refresh_token", refreshedTokens.getString("refresh_token"))
                call.svar(OK.message("Tokens refreshed successfully"))
            } ?: call.svar(BadRequest.message("Header \"refresh_token\" mangler"))
        }
    }
}
