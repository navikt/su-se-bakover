package no.nav.su.se.bakover

import com.auth0.jwk.JwkProvider
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.application.log
import io.ktor.auth.*
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.config.ApplicationConfig
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.response.header
import io.ktor.response.respondRedirect
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.azure.OAuth
import org.json.JSONObject

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
        }
    }
}

internal fun Application.oauthRoutes(frontendRedirectUrl: String, oAuth: OAuth) {
    routing {
        authenticate("azure") {
            get("/login") {
                //Initiate login sequence
            }
            get("/callback") {
                val tokenResponse = call.authentication.principal<OAuthAccessTokenResponse>() as OAuthAccessTokenResponse.OAuth2
                call.respondRedirect("$frontendRedirectUrl#${tokenResponse.accessToken}#${tokenResponse.refreshToken}")
            }
        }
        get("/auth/refresh") {
            call.request.headers["refresh_token"]?.let {
                val refreshedTokens = oAuth.refreshTokens(it)
                call.response.header("access_token", refreshedTokens.getString("access_token"))
                call.response.header("refresh_token", refreshedTokens.getString("refresh_token"))
                call.svar(OK.tekst("Tokens refreshed successfully"))
            } ?: call.svar(BadRequest.tekst("Header \"refresh_token\" mangler"))
        }
    }
}