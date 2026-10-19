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
import no.nav.su.se.bakover.web.routes.vilkår.opplysningsplikt.Behandlingstype
import no.nav.su.se.bakover.web.routes.vilkår.opplysningsplikt.LeggTilOpplysningspliktVilkårBody
import no.nav.su.se.bakover.web.routes.vilkår.opplysningsplikt.OpplysningspliktBeskrivelseJson
import no.nav.su.se.bakover.web.routes.vilkår.opplysningsplikt.VurderingsperiodeOpplysningspliktVilkårJson

internal fun leggTilOpplysningsplikt(
    behandlingId: String,
    type: String = "SØKNADSBEHANDLING",
    beskrivelse: String = "TilstrekkeligDokumentasjon",
    fraOgMed: String = "2021-01-01",
    tilOgMed: String = "2021-12-31",
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
    url: String = "/vilkar/opplysningsplikt",
    client: HttpClient,
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            url,
            listOf(brukerrolle),
            client = client,
        ) {
            setBody(
                serialize(
                    LeggTilOpplysningspliktVilkårBody(
                        id = java.util.UUID.fromString(behandlingId),
                        type = Behandlingstype.valueOf(type),
                        data = listOf(
                            VurderingsperiodeOpplysningspliktVilkårJson(
                                periode = PeriodeJson(fraOgMed = fraOgMed, tilOgMed = tilOgMed),
                                beskrivelse = OpplysningspliktBeskrivelseJson.valueOf(beskrivelse),
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
