package no.nav.su.se.bakover

import com.auth0.jwk.JwkProviderBuilder
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.json.responseJson
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.application.log
import io.ktor.auth.*
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.http.Cookie
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import org.json.JSONObject
import java.net.URL

private const val ISSUER = "issuer"
private const val JWKS_URI = "jwks_uri"
private const val JWT_REALM = "su-se-bakover"

fun Application.setupAuthentication(
        wellKnownUrl: String,
        requiredGroup: String,
        clientId: String,
        clientSecret: String,
        tenant: String
) {
    install(Authentication) {
        oauth("azure") {
            client = HttpClient(Apache)
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                        name = "azure",
                        authorizeUrl = "https://login.microsoftonline.com/$tenant/oauth2/authorize?resource=$clientId",
                        accessTokenUrl = "https://login.microsoftonline.com/$tenant/oauth2/token?resource=$clientId",
                        requestMethod = HttpMethod.Post,
                        clientId = clientId,
                        clientSecret = clientSecret,
                        defaultScopes = listOf("$clientId/.default", "openid")
                )
            }
            urlProvider = { "http://localhost:8080/callback" }
        }

        jwt("jwt") {
            val jwkConfig = getJWKConfig(wellKnownUrl)
            val jwkProvider = JwkProviderBuilder(URL(jwkConfig.getString(JWKS_URI))).build()

            realm = JWT_REALM
            verifier(jwkProvider, jwkConfig.getString(ISSUER))
            validate { credential ->
                val validAudience = clientId in credential.payload.audience
                val groups = credential.payload.getClaim("groups").asList(String::class.java)
                val validGroup = requiredGroup in groups

                if (validAudience && validGroup) {
                    JWTPrincipal(credential.payload)
                } else {
                    if (!validAudience) log.info("Invalid audience: ${credential.payload.audience}")
                    if (!validGroup) log.info("Subject in groups $groups, but not in required group $requiredGroup")
                    null
                }
            }
        }
    }
}

fun Application.oauthRoutes() {
    routing {

        get("/whee") {
            val cookie = call.request.cookies["isso"]
            println(cookie)
            call.respondText { "This is your cookie: \n$cookie" }
        }
        authenticate("azure") {
            get("/hemli") {
                val principal: OAuthAccessTokenResponse.OAuth2 =
                        call.authentication.principal<OAuthAccessTokenResponse>() as OAuthAccessTokenResponse.OAuth2
                println(principal)
                call.respond("this is secured by bill himself.")
            }
            get("/callback") {
                val principal = call.authentication.principal<OAuthAccessTokenResponse>()
                call.response.cookies.append(
                        Cookie(
                                name = "isso",
                                httpOnly = true,
                                value = (principal as OAuthAccessTokenResponse.OAuth2).accessToken
                        )
                )
                call.respondRedirect("/whee")
            }
        }
    }
}

private fun getJWKConfig(oidcConfigUrl: String): JSONObject {
    val (_, response, result) = oidcConfigUrl.httpGet().responseJson()
    if (response.statusCode != HttpStatusCode.OK.value) {
        throw RuntimeException("Could not get JWK config from url ${oidcConfigUrl}, got statuscode=${response.statusCode}")
    } else {
        return result.get().obj()
    }
}