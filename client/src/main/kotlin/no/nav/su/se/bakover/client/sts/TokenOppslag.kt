package no.nav.su.se.bakover.client.sts

import no.nav.su.se.bakover.client.AccessToken
import org.json.JSONObject

interface TokenOppslag {
    fun token(): AccessToken
    fun jwkConfig(): JSONObject
}
