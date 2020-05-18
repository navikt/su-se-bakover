package no.nav.su.se.bakover

import com.github.kittinunf.fuel.httpPost
import io.ktor.http.ContentType.Application.FormUrlEncoded
import io.ktor.http.HttpHeaders.ContentType
import org.json.JSONObject

internal interface OAuth {
    fun onBehalfOFToken(originalToken: String, otherAppId: String): String
    fun refreshTokens(refreshToken: String): JSONObject
    fun token(otherAppId: String): String
}

internal class AzureClient(
        private val thisClientId: String,
        private val thisClientSecret: String,
        private val tokenEndpoint: String
) : OAuth {
    companion object {
        const val AZURE_ON_BEHALF_OF_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer"
        const val REQUESTED_TOKEN_USE = "on_behalf_of"
    }

    override fun onBehalfOFToken(originalToken: String, otherAppId: String): String {
        val (_, _, result) = tokenEndpoint.httpPost(listOf(
                "grant_type" to AZURE_ON_BEHALF_OF_GRANT_TYPE,
                "client_id" to thisClientId,
                "client_secret" to thisClientSecret,
                "assertion" to originalToken.replace("Bearer ", ""),
                "scope" to "$otherAppId/.default",
                "requested_token_use" to REQUESTED_TOKEN_USE
        ))
                .header(ContentType, FormUrlEncoded)
                .responseString()
        return result.fold(
                { JSONObject(it).getString("access_token") },
                { throw RuntimeException("Error while exchanging token in Azure, message:${it.message}}, error:${String(it.errorData)}") }
        )
    }

    override fun refreshTokens(refreshToken: String): JSONObject {
        val (_, _, result) = tokenEndpoint.httpPost(listOf(
                "grant_type" to "refresh_token",
                "client_id" to thisClientId,
                "client_secret" to thisClientSecret,
                "refresh_token" to refreshToken))
                .header(ContentType, FormUrlEncoded)
                .responseString()
        return result.fold(
                { JSONObject(it) },
                { throw RuntimeException("Error while refreshing token in Azure, message:${it.message}}, error:${String(it.errorData)}") }
        )
    }

    override fun token(otherAppId: String): String {
        val (_, _, result) = tokenEndpoint.httpPost(listOf(
                        "grant_type" to "client_credentials",
                        "client_id" to thisClientId,
                        "client_secret" to thisClientSecret,
                        "scope" to "$otherAppId/.default"))
                .header(ContentType, FormUrlEncoded)
                .responseString()
        return result.fold(
                { JSONObject(it).getString("access_token") },
                { throw RuntimeException("Error while getting token from Azure, message:${it.message}}, error:${String(it.errorData)}") }
        )
    }
}