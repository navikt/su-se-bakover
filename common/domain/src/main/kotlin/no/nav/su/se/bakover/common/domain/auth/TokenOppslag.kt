package no.nav.su.se.bakover.common.domain.auth

import org.json.JSONObject

/**
 * Brukes av klienter for å hente jwkConfig.
 */
interface TokenOppslag {
    fun jwkConfig(): JSONObject
}
