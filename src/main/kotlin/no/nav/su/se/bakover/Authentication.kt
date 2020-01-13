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
import io.ktor.response.respondRedirect
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.util.KtorExperimentalAPI
import org.json.JSONObject

@KtorExperimentalAPI
fun Application.setupAuthentication(
        jwkConfig: JSONObject,
        jwkProvider: JwkProvider,
        config: ApplicationConfig
) {
    install(Authentication) {
        oauth("azure") {
            client = HttpClient(Apache)
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
        }
    }
}

fun Application.oauthRoutes(frontendRedirectUrl: String) {
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