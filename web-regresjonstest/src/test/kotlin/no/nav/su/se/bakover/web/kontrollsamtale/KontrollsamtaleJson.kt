package no.nav.su.se.bakover.web.kontrollsamtale

import org.json.JSONObject

fun hentKontrollsamtaleId(kontrollsamtaleResponseJson: String): String {
    return JSONObject(kontrollsamtaleResponseJson).getString("id").toString()
}
