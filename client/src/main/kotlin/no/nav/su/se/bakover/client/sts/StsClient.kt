package no.nav.su.se.bakover.client.sts

import com.github.kittinunf.fuel.httpGet
import no.nav.su.se.bakover.common.domain.auth.TokenOppslag
import org.json.JSONObject

internal class StsClient(
    baseUrl: String,
) : TokenOppslag {
    private val wellKnownUrl = "$baseUrl/.well-known/openid-configuration"

    override fun jwkConfig(): JSONObject {
        val (_, _, result) = wellKnownUrl.httpGet().responseString()
        return result.fold(
            { JSONObject(it) },
            { throw RuntimeException("Could not get JWK config from url $wellKnownUrl, error:$it") },
        )
    }
}
