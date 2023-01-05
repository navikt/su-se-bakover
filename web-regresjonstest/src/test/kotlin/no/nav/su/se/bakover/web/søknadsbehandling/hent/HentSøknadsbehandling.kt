package no.nav.su.se.bakover.web.søknadsbehandling.hent

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.web.SharedRegressionTestData.defaultRequest
import org.json.JSONObject

internal fun hentSøknadsbehandling(sakId: String, søknadsbehandlingId: String, client: HttpClient): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Get,
            "/saker/$sakId/behandlinger/$søknadsbehandlingId",
            listOf(Brukerrolle.Saksbehandler),
            client = client,
        ).apply {
            status shouldBe HttpStatusCode.OK
        }.bodyAsText()
    }
}

/**
 * Henter sakens id fra SøknadsbehandlingJson
 */
internal fun hentSakId(søknadsbehandlingJson: String): String {
    return JSONObject(søknadsbehandlingJson).get("sakId").toString()
}

internal fun hentFormueVilkår(søknadsbehandlingJson: String): String {
    return JSONObject(søknadsbehandlingJson)
        .getJSONObject("grunnlagsdataOgVilkårsvurderinger")
        .get("formue")
        .toString()
}
