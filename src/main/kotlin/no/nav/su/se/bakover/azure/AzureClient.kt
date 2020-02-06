package no.nav.su.se.bakover.azure

import com.github.kittinunf.fuel.httpPost
import io.ktor.http.ContentType.Application.FormUrlEncoded
import io.ktor.http.HttpHeaders.ContentType
import org.json.JSONObject

internal interface TokenExchange {
    fun onBehalfOFToken(originalToken: String, otherAppId: String): String
}

internal class AzureClient(
    private val thisClientId: String,
    private val thisClientSecret: String,
    private val tokenEndpoint: String
): TokenExchange {
    companion object {
        const val GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer"
        const val REQUESTED_TOKEN_USE = "on_behalf_of"
    }

    override fun onBehalfOFToken(originalToken: String, otherAppId: String): String {
        val (_, _, result) = tokenEndpoint.httpPost(listOf(
                "grant_type" to GRANT_TYPE,
                "client_id" to thisClientId,
                "client_secret" to thisClientSecret,
                "assertion" to originalToken.replace("Bearer ", ""),
                "scope" to "$otherAppId/.default",
                "requested_token_use" to REQUESTED_TOKEN_USE))
                .header(ContentType, FormUrlEncoded)
                .responseString()
        return result.fold(
                { JSONObject(it).getString("access_token") },
                { throw RuntimeException("Error while exchanging token in Azure, message:${it.message}}, error:${String(it.errorData)}") }
        )
    }
}