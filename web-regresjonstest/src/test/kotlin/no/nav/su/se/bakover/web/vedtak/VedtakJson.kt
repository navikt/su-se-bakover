package no.nav.su.se.bakover.web.vedtak

import org.json.JSONObject

data object VedtakJson {
    fun hentVedtakId(vedtakResponseJson: String): String {
        return JSONObject(vedtakResponseJson).getString("id")
    }
}
