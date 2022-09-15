package no.nav.su.se.bakover.client.sts

import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpGet
import no.nav.su.se.bakover.client.AccessToken
import no.nav.su.se.bakover.client.ExpiringTokenResponse
import no.nav.su.se.bakover.client.isValid
import org.json.JSONObject
import java.time.Clock

internal class StsClient(
    private val baseUrl: String,
    private val username: String,
    private val password: String,
    private val clock: Clock,
) : TokenOppslag {
    private var stsToken: ExpiringTokenResponse? = null
    private val wellKnownUrl = "$baseUrl/.well-known/openid-configuration"

    override fun token(): AccessToken {
        if (!stsToken.isValid()) {
            val (_, _, result) = "$baseUrl/rest/v1/sts/token?grant_type=client_credentials&scope=openid".httpGet()
                .authentication().basic(username, password)
                .header("Accept", "application/json")
                .responseString()

            stsToken = result.fold(
                { ExpiringTokenResponse(JSONObject(it), clock = clock) },
                { throw RuntimeException("Error while getting token from STS, message:${it.message}, error:${String(it.errorData)}") },
            )
        }
        return stsToken?.accessToken!!
    }

    override fun jwkConfig(): JSONObject {
        val (_, _, result) = wellKnownUrl.httpGet().responseString()
        return result.fold(
            { JSONObject(it) },
            { throw RuntimeException("Could not get JWK config from url $wellKnownUrl, error:$it") },
        )
    }
}
