package no.nav.su.se.bakover.client.azure

import org.json.JSONObject

interface OAuth {
    fun onBehalfOFToken(originalToken: String, otherAppId: String): String
    fun refreshTokens(refreshToken: String): JSONObject
    fun jwkConfig(): JSONObject
}
