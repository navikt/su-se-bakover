package no.nav.su.se.bakover.web.søknadsbehandling

import org.json.JSONObject

object BehandlingJson {
    fun hentBehandlingId(søknadsbehandlingResponseJson: String): String {
        return JSONObject(søknadsbehandlingResponseJson).getString("id").toString()
    }

    fun hentSakId(søknadsbehandlingResponseJson: String): String {
        return JSONObject(søknadsbehandlingResponseJson).getString("sakId").toString()
    }
}

object SakJson {
    fun hentSaksnummer(sakJson: String): String {
        return JSONObject(sakJson).getLong("saksnummer").toString()
    }
}
