package no.nav.su.se.bakover.web

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.AuthenticationFailedCause
import io.ktor.auth.AuthenticationPipeline
import io.ktor.auth.AuthenticationProvider
import io.ktor.auth.OAuthAccessTokenResponse
import io.ktor.auth.OAuthServerSettings
import io.ktor.auth.UnauthorizedResponse
import io.ktor.auth.authenticate
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import io.ktor.auth.oauth
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.request.ApplicationRequest
import io.ktor.request.header
import io.ktor.response.header
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.routing.get
import io.ktor.routing.routing
import no.nav.su.se.bakover.client.azure.OAuth
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.Config
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.web.stubs.JwkProviderStub
import no.nav.su.se.bakover.web.stubs.JwtStub
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.util.Base64
import java.util.Date

internal const val AUTH_CALLBACK_PATH = "/callback"
internal const val LOGOUT_CALLBACK_PATH = "$AUTH_CALLBACK_PATH/logout-complete"

internal fun Application.configureAuthentication(
    oAuth: OAuth,
    applicationConfig: ApplicationConfig
) {
    val jwkConfig = oAuth.jwkConfig()
    when (Config.isLocalOrRunningTests) {
        true -> configureLocalAuth(jwkConfig, applicationConfig)
        false -> configureNonLocalAuth(jwkConfig, oAuth, applicationConfig)
    }
}

internal fun Application.configureLocalAuth(jwkConfig: JSONObject, applicationConfig: ApplicationConfig) {
    val jwtStub = JwtStub(applicationConfig)
    install(Authentication) {
        val provider =
            LocalhostAuthProvider(LocalhostAuthProvider.Configuration("azure")) // TODO name for auth-mechanism
        provider.pipeline.intercept(AuthenticationPipeline.RequestAuthentication) { context ->
            when (val bearerToken = context.call.request.header("Authorization")?.replace("Bearer ", "")) {
                null -> {
                    val jwt = jwtStub.create(
                        audience = applicationConfig.azure.clientId,
                        roller = listOf(Brukerrolle.Saksbehandler)
                    ).replace("Bearer ", "")
                    context.principal(JWTPrincipal(JWT.decode(jwt))) // TODO single invocation of jwt creation
                }
                else -> context.principal(JWTPrincipal(JWT.decode(bearerToken)))
            }
        }
        register(provider)

        installJwt(
            jwkProvider = JwkProviderStub,
            issuer = jwkConfig.getString("issuer"),
            applicationConfig = applicationConfig
        )
    }

    routing {
        authenticate("azure") { // TODO name for auth-mechanism
            get("/login") {
                call.respondRedirect(
                    "${Config.suSeFramoverLoginSuccessUrl}#${
                    jwtStub.create(audience = applicationConfig.azure.clientId)
                        .replace("Bearer ", "")
                    }#${
                    jwtStub.create(audience = applicationConfig.azure.clientId)
                        .replace("Bearer ", "") // TODO single invocation of jwl creation
                    }"
                )
            }
            get(AUTH_CALLBACK_PATH) { // Only for test to verify that we are not logging the tokens
                call.respondRedirect("${Config.suSeFramoverLoginSuccessUrl}#access#refresh")
            }
        }
        get("/auth/refresh") {
            call.request.headers["refresh_token"]?.let {
                val refreshedTokens = jwtStub.create(audience = applicationConfig.azure.clientId)
                    .replace("Bearer ", "")
                call.response.header("access_token", refreshedTokens)
                call.response.header("refresh_token", refreshedTokens)
                call.svar(HttpStatusCode.OK.message("Tokens refreshed successfully"))
            } ?: call.svar(HttpStatusCode.BadRequest.message("Header \"refresh_token\" mangler"))
        }
    }
}

class LocalhostAuthProvider(configuration: Configuration) : AuthenticationProvider(configuration) {
    class Configuration(name: String) : AuthenticationProvider.Configuration(name)
}

internal fun Application.configureNonLocalAuth(
    jwkConfig: JSONObject,
    oAuth: OAuth,
    applicationConfig: ApplicationConfig
) {
    val httpClient = HttpClient(Apache) {
        engine {
            customizeClient {
                useSystemProperties()
            }
        }
    }

    install(Authentication) {
        oauth("azure") { // TODO name for auth-mechanism
            client = httpClient
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                    name = "azure",
                    authorizeUrl = jwkConfig.getString("authorization_endpoint"),
                    accessTokenUrl = jwkConfig.getString("token_endpoint"),
                    requestMethod = HttpMethod.Post,
                    clientId = applicationConfig.azure.clientId,
                    clientSecret = applicationConfig.azure.clientSecret,
                    defaultScopes = listOf("${applicationConfig.azure.clientId}/.default", "openid", "offline_access")
                )
            }
            urlProvider = { applicationConfig.azure.backendCallbackUrl }
        }

        installJwt(
            jwkProvider = JwkProviderBuilder(URL(jwkConfig.getString("jwks_uri"))).build(),
            issuer = jwkConfig.getString("issuer"), // TODO extract to object
            applicationConfig = applicationConfig
        )
    }

    routing {
        authenticate("azure") { // TODO name for auth-mechanism
            get("/login") {
                // Initiate login sequence
            }
            get(AUTH_CALLBACK_PATH) {
                val tokenResponse =
                    call.authentication.principal<OAuthAccessTokenResponse>() as OAuthAccessTokenResponse.OAuth2

                call.respondRedirect("${Config.suSeFramoverLoginSuccessUrl}#${tokenResponse.accessToken}#${tokenResponse.refreshToken}")
            }
        }
        get("/logout") {
            val endSessionEndpoint = jwkConfig.getString("end_session_endpoint")

            val redirectUri =
                URLEncoder.encode("${applicationConfig.azure.backendCallbackUrl}/logout-complete", "utf-8")

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
                call.svar(HttpStatusCode.OK.message("Tokens refreshed successfully"))
            } ?: call.svar(HttpStatusCode.BadRequest.message("Header \"refresh_token\" mangler"))
        }
    }
}

internal fun Authentication.Configuration.installJwt(
    jwkProvider: JwkProvider,
    issuer: String,
    applicationConfig: ApplicationConfig
) {
    jwt("jwt") {
        verifier(jwkProvider, issuer)
        validate { credential ->
            val validAudience = applicationConfig.azure.clientId in credential.payload.audience
            val groupsFromToken = credential.payload.getClaim("groups")?.asList(String::class.java) ?: emptyList()

            val allowedGroups = applicationConfig.azure.groups.asList()
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

private fun getExpiry(request: ApplicationRequest) =
    request.headers["Authorization"]?.substringAfter("Bearer ")
        ?.let { String(Base64.getDecoder().decode(it.split(".")[1]), Charsets.UTF_8) }
        ?.let { JSONObject(it) }
        ?.let { it["exp"] as Int }
        ?.let { Date.from(Instant.ofEpochSecond(it.toLong())) }

private fun errorMessage(date: Date) = if (tokenHasExpired(date)) "The token expired at $date" else ""
private fun tokenHasExpired(date: Date) = date.before(Date.from(Instant.now()))
