package no.nav.su.se.bakover.web.tilbakekreving

import org.json.JSONObject

fun hentTilbakekrevingsbehandlingId(json: String): String {
    return JSONObject(json).getString("id")
}
