package no.nav.su.se.bakover.web.søknadsbehandling.familiegjenforening

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
import no.nav.su.se.bakover.domain.vilkår.familiegjenforening.FamiliegjenforeningvilkårStatus
import no.nav.su.se.bakover.test.application.defaultRequest
import no.nav.su.se.bakover.web.routes.vilkår.FamiliegjenforeningVilkårJson
import no.nav.su.se.bakover.web.routes.vilkår.VurderingsperiodeFamiliegjenforeningJson

internal fun leggTilFamiliegjenforening(
    sakId: String,
    behandlingId: String,
    resultat: FamiliegjenforeningvilkårStatus = FamiliegjenforeningvilkårStatus.VilkårOppfylt,
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
    url: String = "/saker/$sakId/behandlinger/$behandlingId/familiegjenforening",
    fraOgMed: String = "2021-01-01",
    tilOgMed: String = "2021-12-31",
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
                FamiliegjenforeningVilkårJson(
                    vurderinger = listOf(
                        VurderingsperiodeFamiliegjenforeningJson(
                            periode = PeriodeJson(fraOgMed, tilOgMed),
                            status = resultat,
                        ),
                    ),
                ).let { serialize(it) },
            )
        }.apply {
            withClue("body=${this.bodyAsText()}") {
                status shouldBe HttpStatusCode.Created
                contentType() shouldBe ContentType.parse("application/json")
            }
        }.bodyAsText()
    }
}
