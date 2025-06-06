package no.nav.su.se.bakover.web.søknadsbehandling

import org.json.JSONArray
import org.json.JSONObject

data object BehandlingJson {
    fun hentBehandlingId(søknadsbehandlingResponseJson: String): String {
        return JSONObject(søknadsbehandlingResponseJson).getString("id").toString()
    }

    fun hentSakId(søknadsbehandlingResponseJson: String): String {
        return JSONObject(søknadsbehandlingResponseJson).getString("sakId").toString()
    }

    fun hentSøknadId(søknadsbehandlingResponseJson: String): String {
        return JSONObject(søknadsbehandlingResponseJson).getJSONObject("søknad").getString("id")
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

    fun hentEksterneGrunnlag(json: String): String = JSONObject(json).getJSONObject("eksterneGrunnlag").toString()

    fun hentFamiliegjenforeningVilkår(json: String): String {
        return JSONObject(json).getJSONObject("grunnlagsdataOgVilkårsvurderinger").getJSONObject("familiegjenforening").toString()
    }
}

data object RevurderingJson {
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

    fun hentFamiliegjenforeningVilkår(json: String): String {
        return JSONObject(json).getJSONObject("revurdering").getJSONObject("grunnlagsdataOgVilkårsvurderinger").getJSONObject("familiegjenforening").toString()
    }

    fun hentBrevvalg(json: String): String {
        return JSONObject(json).getJSONObject("brevvalg").toString()
    }
}

data object ReguleringJson {
    fun hentSingleReglering(json: String): String = JSONArray(json).single().toString()
    fun id(json: String): String = JSONObject(json).getString("id")

    fun hentSingleUføregrunnlag(json: String): String =
        JSONObject(json).getJSONObject("grunnlagsdataOgVilkårsvurderinger").getJSONObject("uføre").getJSONArray("vurderinger").single().toString()

    fun supplement(reguleringEtterEttersendelse: String): String {
        return JSONObject(reguleringEtterEttersendelse).getJSONObject("supplement").toString()
    }
}

data object GrunnlagJson {
    fun id(json: String): String = JSONObject(json).getString("id")
    fun opprettet(json: String): String = JSONObject(json).getString("opprettet")
}
