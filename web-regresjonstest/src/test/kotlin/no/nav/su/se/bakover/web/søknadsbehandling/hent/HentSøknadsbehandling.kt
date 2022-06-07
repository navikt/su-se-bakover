package no.nav.su.se.bakover.web.søknadsbehandling.hent

import io.kotest.matchers.shouldBe
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.web.SharedRegressionTestData.defaultRequest
import org.json.JSONObject

internal fun ApplicationTestBuilder.hentSøknadsbehandling(sakId: String, søknadsbehandlingId: String): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Get,
            "/saker/$sakId/behandlinger/$søknadsbehandlingId",
            listOf(Brukerrolle.Saksbehandler),
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
