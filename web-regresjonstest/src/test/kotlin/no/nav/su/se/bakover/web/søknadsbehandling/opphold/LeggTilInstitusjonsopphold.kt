package no.nav.su.se.bakover.web.søknadsbehandling.opphold

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
import no.nav.su.se.bakover.web.routes.vilkår.institusjonsopphold.LeggTilVurderingsperiodeInstitusjonsoppholdJson
import no.nav.su.se.bakover.web.routes.vilkår.institusjonsopphold.VurderingInstitusjonsoppholdJson
import no.nav.su.se.bakover.web.routes.vilkår.institusjonsopphold.VurderingsperiodeInstitusjonsoppholdJson

internal fun leggTilInstitusjonsopphold(
    sakId: String,
    behandlingId: String,
    vurdering: VurderingInstitusjonsoppholdJson = VurderingInstitusjonsoppholdJson.VilkårOppfylt,
    fraOgMed: String = "2021-01-01",
    tilOgMed: String = "2021-12-31",
    url: String = "/saker/$sakId/behandlinger/$behandlingId/institusjonsopphold",
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
    client: HttpClient,
): String {
    return runBlocking {
        defaultRequest(
            method = HttpMethod.Post,
            uri = url,
            roller = listOf(brukerrolle),
            client = client,
        ) {
            setBody(
                serialize(
                    LeggTilVurderingsperiodeInstitusjonsoppholdJson(
                        vurderingsperioder = listOf(
                            VurderingsperiodeInstitusjonsoppholdJson(
                                periode = PeriodeJson(fraOgMed = fraOgMed, tilOgMed = tilOgMed),
                                vurdering = vurdering,
                            ),
                        ),
                    ),
                ),
            )
        }.apply {
            withClue("body=${this.bodyAsText()}") {
                status shouldBe HttpStatusCode.Created
                contentType() shouldBe ContentType.parse("application/json")
            }
        }.bodyAsText()
    }
}
