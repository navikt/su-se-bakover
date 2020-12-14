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
import no.nav.su.se.bakover.client.azure.OAuth
import no.nav.su.se.bakover.common.Config
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant
import java.util.Base64.getDecoder
import java.util.Date

@OptIn(io.ktor.util.KtorExperimentalAPI::class)
internal fun Application.setupAuthentication(
    jwkConfig: JSONObject,
    jwkProvider: JwkProvider,
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
                    clientId = Config.azureClientId,
                    clientSecret = Config.azureClientSecret,
                    defaultScopes = listOf("${Config.azureClientId}/.default", "openid", "offline_access")
                )
            }
            urlProvider = { Config.azureBackendCallbackUrl }
        }

        jwt("jwt") {
            verifier(jwkProvider, jwkConfig.getString("issuer"))
            validate { credential ->
                val validAudience = Config.azureClientId in credential.payload.audience
                val groupsFromToken = credential.payload.getClaim("groups").asList(String::class.java)

                val allowedGroups =
                    listOf(Config.azureGroupVeileder, Config.azureGroupSaksbehandler, Config.azureGroupAttestant)
                val validGroup = groupsFromToken.any { it in allowedGroups }

                if (validAudience && validGroup) {
                    JWTPrincipal(credential.payload)
                } else {
                    if (!validAudience) log.info("Invalid audience: ${credential.payload.audience}")
                    if (!validGroup) log.info("Subject in groups $groupsFromToken, but not in any required group")
                    null
                }
            }
            challenge { defaultScheme, realm ->
                val errors: List<AuthenticationFailedCause> = call.authentication.allFailures
                when (errors.singleOrNull()) {
                    AuthenticationFailedCause.InvalidCredentials -> {
                        getExpiry(call.request)
                            ?.takeIf(::tokenHasExpired)
                            ?.also {
                                call.respond(HttpStatusCode.Unauthorized, errorMessage(it).also(log::debug))
                            } ?: call.respond(HttpStatusCode.Forbidden)
                    }
                    else ->
                        call.respond(
                            UnauthorizedResponse(
                                HttpAuthHeader.Parameterized(
                                    defaultScheme,
                                    mapOf(HttpAuthHeader.Parameters.Realm to realm)
                                )
                            )
                        )
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
internal const val PERSON_PATH = "/person"
internal const val LOGOUT_CALLBACK_PATH = "$AUTH_CALLBACK_PATH/logout-complete"

internal fun Application.oauthRoutes(
    frontendRedirectUrl: String,
    jwkConfig: JSONObject,
    oAuth: OAuth
) {
    routing {
        authenticate("azure") {
            get("/login") {
                // Initiate login sequence
            }
            get(AUTH_CALLBACK_PATH) {
                val tokenResponse =
                    call.authentication.principal<OAuthAccessTokenResponse>() as OAuthAccessTokenResponse.OAuth2

                call.respondRedirect("$frontendRedirectUrl#${tokenResponse.accessToken}#${tokenResponse.refreshToken}")
            }
        }
        get("/logout") {
            val endSessionEndpoint = jwkConfig.getString("end_session_endpoint")

            val redirectUri = URLEncoder.encode("${Config.azureBackendCallbackUrl}/logout-complete", "utf-8")

            call.respondRedirect("$endSessionEndpoint?post_logout_redirect_uri=$redirectUri")
        }
        get(LOGOUT_CALLBACK_PATH) {
            call.respondRedirect(Config.suSeFramoverLogoutSuccessUrl)
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
