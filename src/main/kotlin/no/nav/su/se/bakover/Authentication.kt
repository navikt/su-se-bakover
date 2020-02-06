package no.nav.su.se.bakover

import com.auth0.jwk.JwkProvider
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.config.ApplicationConfig
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.request.ApplicationRequest
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.util.KtorExperimentalAPI
import org.json.JSONObject
import java.time.Instant
import java.util.*
import java.util.Base64.getDecoder

@KtorExperimentalAPI
internal fun Application.setupAuthentication(
        jwkConfig: JSONObject,
        jwkProvider: JwkProvider,
        config: ApplicationConfig
) {
    install(Authentication) {
        oauth("azure") {
            client = HttpClient(Apache) {
                engine {
                    customizeClient {
                        useSystemProperties()
                    }
                }
            }
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                        name = "azure",
                        authorizeUrl = jwkConfig.getString("authorization_endpoint"),
                        accessTokenUrl = jwkConfig.getString("token_endpoint"),
                        requestMethod = Post,
                        clientId = config.getProperty("azure.clientId"),
                        clientSecret = config.getProperty("azure.clientSecret"),
                        defaultScopes = listOf("${config.getProperty("azure.clientId")}/.default", "openid")
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
                        call.respond(UnauthorizedResponse(HttpAuthHeader.Parameterized(
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

private fun Application.errorMessage(date: Date) =
        if (tokenHasExpired(date)) {
            "The token expired at $date"
        } else ""

internal fun Application.oauthRoutes(frontendRedirectUrl: String) {
    routing {
        authenticate("azure") {
            get("/login") {
                //Initiate login sequence
            }
            get("/callback") {
                val tokenResponse = call.authentication.principal<OAuthAccessTokenResponse>()
                call.respondRedirect("$frontendRedirectUrl#${(tokenResponse as OAuthAccessTokenResponse.OAuth2).accessToken}")
            }
        }
    }
}

@KtorExperimentalAPI
private fun ApplicationConfig.getProperty(key: String): String = property(key).getString()
