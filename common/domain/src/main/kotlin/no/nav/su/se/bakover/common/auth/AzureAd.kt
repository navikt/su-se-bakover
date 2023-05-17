package no.nav.su.se.bakover.common.auth

interface AzureAd {
    fun onBehalfOfToken(originalToken: String, otherAppId: String): String
    val jwksUri: String
    fun getSystemToken(otherAppId: String): String
    val issuer: String
}
