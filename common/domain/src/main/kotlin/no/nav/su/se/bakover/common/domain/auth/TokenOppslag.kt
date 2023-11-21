package no.nav.su.se.bakover.common.domain.auth

import org.json.JSONObject

/**
 * Brukes av klienter for å hente token og jwkConfig.
 */
interface TokenOppslag {
    fun token(): AccessToken
    fun jwkConfig(): JSONObject
}
