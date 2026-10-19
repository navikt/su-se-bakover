package no.nav.su.se.bakover.web

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
import no.nav.su.se.bakover.web.routes.vilkår.pensjon.LeggTilVurderingsperiodePensjonsvilkårJson
import no.nav.su.se.bakover.web.routes.vilkår.pensjon.PensjonsopplysningerJson
import no.nav.su.se.bakover.web.routes.vilkår.pensjon.PensjonsoppysningerSvarJson

internal fun leggTilPensjonsVilkår(
    sakId: String,
    behandlingId: String,
    fraOgMed: String = "2021-01-01",
    tilOgMed: String = "2021-12-31",
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
    body: () -> String = { innvilgetPensjonsvilkårJson(fraOgMed, tilOgMed) },
    url: String = "/saker/$sakId/behandlinger/$behandlingId/pensjon",
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
                status shouldBe HttpStatusCode.Created
                contentType() shouldBe ContentType.parse("application/json")
            }
        }.bodyAsText()
    }
}

internal fun innvilgetPensjonsvilkårJson(fraOgMed: String, tilOgMed: String): String {
    return serialize(
        listOf(
            LeggTilVurderingsperiodePensjonsvilkårJson(
                periode = PeriodeJson(fraOgMed = fraOgMed, tilOgMed = tilOgMed),
                pensjonsopplysninger = PensjonsopplysningerJson(
                    folketrygd = PensjonsoppysningerSvarJson.JA,
                    andreNorske = PensjonsoppysningerSvarJson.IKKE_AKTUELT,
                    utenlandske = PensjonsoppysningerSvarJson.JA,
                ),
            ),
        ),
    )
}

internal fun avslåttPensjonsvilkårJson(fraOgMed: String, tilOgMed: String): String {
    return serialize(
        listOf(
            LeggTilVurderingsperiodePensjonsvilkårJson(
                periode = PeriodeJson(fraOgMed = fraOgMed, tilOgMed = tilOgMed),
                pensjonsopplysninger = PensjonsopplysningerJson(
                    folketrygd = PensjonsoppysningerSvarJson.NEI,
                    andreNorske = PensjonsoppysningerSvarJson.IKKE_AKTUELT,
                    utenlandske = PensjonsoppysningerSvarJson.JA,
                ),
            ),
        ),
    )
}
