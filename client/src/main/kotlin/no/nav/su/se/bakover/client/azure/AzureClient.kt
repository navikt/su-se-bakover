package no.nav.su.se.bakover.client.azure

import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import org.json.JSONObject

internal class AzureClient(
    private val thisClientId: String,
    private val thisClientSecret: String,
    private val wellknownUrl: String
) : AzureAd {
    private val tokenEndpoint = jwkConfig().getString("token_endpoint")

    companion object {
        const val AZURE_ON_BEHALF_OF_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer"
        const val REQUESTED_TOKEN_USE = "on_behalf_of"
    }

    override fun onBehalfOfToken(originalToken: String, otherAppId: String): String {
        val (_, _, result) = tokenEndpoint.httpPost(
            listOf(
                "grant_type" to AZURE_ON_BEHALF_OF_GRANT_TYPE,
                "client_id" to thisClientId,
                "client_secret" to thisClientSecret,
                "assertion" to originalToken.replace("Bearer ", ""),
                "scope" to "$otherAppId/.default",
                "requested_token_use" to REQUESTED_TOKEN_USE
            )
        )
            .header("Content-Type", "application/x-www-form-urlencoded")
            .responseString()
        return result.fold(
            { JSONObject(it).getString("access_token") },
            { throw RuntimeException("Error while exchanging token in Azure, message:${it.message}}, error:${String(it.errorData)}") }
        )
    }

    override fun getSystemToken(otherAppId: String): String {
        val (_, _, result) = tokenEndpoint.httpPost(
            listOf(
                "grant_type" to "client_credentials",
                "client_id" to thisClientId,
                "client_secret" to thisClientSecret,
                "scope" to "$otherAppId/.default",
            )
        )
            .header("Content-Type", "application/x-www-form-urlencoded")
            .responseString()
        return result.fold(
            { JSONObject(it).getString("access_token") },
            { throw RuntimeException("Error while exchanging token in Azure, message:${it.message}}, error:${String(it.errorData)}") }
        )
    }

    override fun jwkConfig(): JSONObject {
        val (_, _, result) = wellknownUrl.httpGet().responseString()
        return result.fold(
            { JSONObject(it) },
            { throw RuntimeException("Could not get JWK config from url $wellknownUrl, error:$it") }
        )
    }
}
