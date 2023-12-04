package no.nav.su.se.bakover.web.tilbakekreving

import org.json.JSONObject

fun hentTilbakekrevingsbehandlingId(json: String): String {
    return JSONObject(json).getString("id")
}

fun hentForhåndsvarselDokumenter(json: String): String {
    return JSONObject(json).getJSONArray("forhåndsvarselsInfo").toString()
}
fun hentFritekst(json: String): String {
    return JSONObject(json).getString("fritekst")
}
fun hentVurderinger(json: String): String {
    return JSONObject(json).getJSONObject("vurderinger").toString()
}
fun hentAttesteringer(json: String): String {
    return JSONObject(json).getJSONArray("attesteringer").toString()
}
fun hentNotat(json: String): String {
    return JSONObject(json).getString("notat").toString()
}
