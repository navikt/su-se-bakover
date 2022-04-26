package no.nav.su.se.bakover.web.revurdering

import org.json.JSONObject

fun hentRevurderingId(revurderingJson: String): String {
    return JSONObject(revurderingJson).getString("id").toString()
}
