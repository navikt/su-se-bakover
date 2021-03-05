package no.nav.su.se.bakover.client.sts

import org.json.JSONObject

interface TokenOppslag {
    fun token(): String
    fun jwkConfig(): JSONObject
}
