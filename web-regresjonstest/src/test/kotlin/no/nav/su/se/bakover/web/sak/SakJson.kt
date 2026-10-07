package no.nav.su.se.bakover.web.sak

import org.json.JSONObject

data object SakJson {

    fun hentFørsteVedtak(sakJson: String): String {
        return JSONObject(sakJson).getJSONArray("vedtak").first().toString()
    }

    fun hentFnr(sakJson: String): String {
        return JSONObject(sakJson).getString("fnr")
    }
}
