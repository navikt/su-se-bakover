package no.nav.su.se.bakover.web.søknadsbehandling

import org.json.JSONObject

object BehandlingJson {
    fun hentBehandlingId(søknadsbehandlingResponseJson: String): String {
        return JSONObject(søknadsbehandlingResponseJson).getString("id").toString()
    }

    fun hentSakId(søknadsbehandlingResponseJson: String): String {
        return JSONObject(søknadsbehandlingResponseJson).getString("sakId").toString()
    }

    fun hentPensjonsVilkår(json: String): String {
        return JSONObject(json).getJSONObject("grunnlagsdataOgVilkårsvurderinger").getJSONObject("pensjon").toString()
    }

    fun hentFlyktningVilkår(json: String): String {
        return JSONObject(json).getJSONObject("grunnlagsdataOgVilkårsvurderinger").getJSONObject("flyktning").toString()
    }

    fun hentStatus(json: String): String {
        return JSONObject(json).getString("status").toString()
    }

    fun hentFastOppholdVilkår(json: String): String {
        return JSONObject(json).getJSONObject("grunnlagsdataOgVilkårsvurderinger").getJSONObject("fastOpphold").toString()
    }

    fun hentPersonligOppmøteVilkår(json: String): String {
        return JSONObject(json).getJSONObject("grunnlagsdataOgVilkårsvurderinger").getJSONObject("personligOppmøte").toString()
    }

    fun hentInstitusjonsoppholdVilkår(json: String): String {
        return JSONObject(json).getJSONObject("grunnlagsdataOgVilkårsvurderinger").getJSONObject("institusjonsopphold").toString()
    }
}

object RevurderingJson {
    fun hentRevurderingId(json: String): String {
        return JSONObject(json).getString("id").toString()
    }

    fun hentFlyktningVilkår(json: String): String {
        return JSONObject(json).getJSONObject("revurdering").getJSONObject("grunnlagsdataOgVilkårsvurderinger").getJSONObject("flyktning").toString()
    }

    fun hentFastOppholdVilkår(json: String): String {
        return JSONObject(json).getJSONObject("revurdering").getJSONObject("grunnlagsdataOgVilkårsvurderinger").getJSONObject("fastOpphold").toString()
    }

    fun hentPersonligOppmøteVilkår(json: String): String {
        return JSONObject(json).getJSONObject("revurdering").getJSONObject("grunnlagsdataOgVilkårsvurderinger").getJSONObject("personligOppmøte").toString()
    }

    fun hentInstitusjonsoppholdVilkår(json: String): String {
        return JSONObject(json).getJSONObject("revurdering").getJSONObject("grunnlagsdataOgVilkårsvurderinger").getJSONObject("institusjonsopphold").toString()
    }
}
