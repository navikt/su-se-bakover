package no.nav.su.se.bakover.web.søknadsbehandling.fradrag

import common.presentation.beregning.FradragRequestJson
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
import vilkår.inntekt.domain.grunnlag.Fradragstype

internal fun leggTilFradrag(
    sakId: String,
    behandlingId: String,
    fraOgMed: String = "2021-01-01",
    tilOgMed: String = "2021-12-31",
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
    fradrag: List<FradragRequestJson> = listOf(
        FradragRequestJson(
            periode = PeriodeJson(fraOgMed = fraOgMed, tilOgMed = tilOgMed),
            type = Fradragstype.Kategori.PrivatPensjon.name,
            beskrivelse = null,
            beløp = 10000.0,
            utenlandskInntekt = null,
            tilhører = "BRUKER",
        ),
    ),
    body: () -> String = {
        serialize(mapOf("fradrag" to fradrag))
    },
    url: String = "/saker/$sakId/behandlinger/$behandlingId/fradrag",
    client: HttpClient,
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            url,
            listOf(brukerrolle),
            client = client,
        ) {
            setBody(body())
        }.apply {
            withClue("body=${this.bodyAsText()}") {
                status shouldBe HttpStatusCode.OK
                contentType() shouldBe ContentType.parse("application/json")
            }
        }.bodyAsText()
    }
}
