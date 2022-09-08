package no.nav.su.se.bakover.client.azure

import org.json.JSONObject

interface AzureAd {
    fun onBehalfOfToken(originalToken: String, otherAppId: String): String
    fun jwkConfig(): JSONObject
    fun getSystemToken(otherAppId: String): String
}
