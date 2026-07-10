package no.nav.su.se.bakover.web.søknadsbehandling.bosituasjon

import behandling.søknadsbehandling.presentation.bosituasjon.LeggTilBosituasjonForSøknadsbehandlingJsonRequest
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.test.application.defaultRequest

/**
 * uri defaultes til søknadsbehandling
 * body defaultes til bor alene
 */
internal fun leggTilBosituasjon(
    sakId: String,
    behandlingId: String,
    fraOgMed: String = "2021-01-01",
    tilOgMed: String = "2021-12-31",
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
    epsFnr: String? = null,
    delerBolig: Boolean? = false,
    erEpsFylt67: Boolean? = null,
    erEPSUførFlyktning: Boolean? = null,
    body: () -> String = {
        serialize(
            LeggTilBosituasjonForSøknadsbehandlingJsonRequest(
                bosituasjoner = listOf(
                    LeggTilBosituasjonForSøknadsbehandlingJsonRequest.JsonBody(
                        periode = PeriodeJson(fraOgMed = fraOgMed, tilOgMed = tilOgMed),
                        epsFnr = epsFnr,
                        delerBolig = delerBolig,
                        erEpsFylt67 = erEpsFylt67,
                        erEPSUførFlyktning = erEPSUførFlyktning,
                    ),
                ),
            ),
        )
    },
    url: String = "/saker/$sakId/behandlinger/$behandlingId/grunnlag/bosituasjon",
    client: HttpClient,
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            url,
            listOf(brukerrolle),
            client = client,
        ) {
            val body = body()
            setBody(body)
        }.apply {
            withClue("body=${this.bodyAsText()}") {
                status shouldBe HttpStatusCode.OK
                contentType() shouldBe ContentType.parse("application/json")
            }
        }.bodyAsText()
    }
}

/**
 * hardkodet defaults med untakk av epsFnr
 */
fun bosituasjonEpsJson(epsFnr: String): String {
    return serialize(
        LeggTilBosituasjonForSøknadsbehandlingJsonRequest(
            bosituasjoner = listOf(
                LeggTilBosituasjonForSøknadsbehandlingJsonRequest.JsonBody(
                    periode = PeriodeJson(fraOgMed = "2021-01-01", tilOgMed = "2021-12-31"),
                    epsFnr = epsFnr,
                    delerBolig = null,
                    erEpsFylt67 = false,
                    erEPSUførFlyktning = true,
                ),
            ),
        ),
    )
}
