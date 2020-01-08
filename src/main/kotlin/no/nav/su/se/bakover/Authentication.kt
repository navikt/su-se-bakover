package no.nav.su.se.bakover

import com.auth0.jwk.JwkProviderBuilder
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.json.responseJson
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.application.log
import io.ktor.auth.Authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import io.ktor.http.HttpStatusCode
import org.json.JSONObject
import java.net.URL

private const val ISSUER = "issuer"
private const val JWKS_URI = "jwks_uri"
private const val JWT_REALM = "su-se-bakover"

fun Application.setupAuthentication(
        wellKnownUrl: String,
        requiredGroup: String,
        clientId: String
) {
    install(Authentication) {
        jwt {
            val jwkConfig = getJWKConfig(wellKnownUrl)
            val jwkProvider = JwkProviderBuilder(URL(jwkConfig.getString(JWKS_URI))).build()

            realm = JWT_REALM
            verifier(jwkProvider, jwkConfig.getString(ISSUER))
            validate { credential ->
                val validAudience = clientId in credential.payload.audience // should be ... su-se-bakover sin client_id
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

private fun getJWKConfig(oidcConfigUrl: String): JSONObject {
    val (_, response, result) = oidcConfigUrl.httpGet().responseJson()
    if (response.statusCode != HttpStatusCode.OK.value) {
        throw RuntimeException("Could not get JWK config from url ${oidcConfigUrl}, got statuscode=${response.statusCode}")
    } else {
        return result.get().obj()
    }
}