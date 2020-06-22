package no.nav.su.se.bakover.client

import com.github.kittinunf.fuel.httpGet
import org.json.JSONObject

interface Jwk {
    fun config(): JSONObject
}

internal class JwkClient(
        private val wellKnownUrl: String
) : Jwk {
    override fun config(): JSONObject {
        val (_, _, result) = wellKnownUrl.httpGet().responseString()
        return result.fold(
                { JSONObject(it) },
                { throw RuntimeException("Could not get JWK config from url ${wellKnownUrl}, error:${it}") }
        )
    }
}