package no.nav.su.se.bakover.web.sak

import org.json.JSONObject

data object SakJson {

    fun hentFørsteVedtak(sakJson: String): String {
        return JSONObject(sakJson).getJSONArray("vedtak").first().toString()
    }

    fun hentVedtak(sakJson: String, vedtakId: String): String {
        JSONObject(sakJson).getJSONArray("vedtak").filter {
            it.toString().contains(vedtakId)
        }.let {
            if (it.isEmpty()) {
                throw IllegalArgumentException("Fant ikke vedtak med id $vedtakId")
            }
            return it.first().toString()
        }
    }
}
