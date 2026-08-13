package no.nav.su.se.bakover.web.revurdering.utenlandsopphold

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
import no.nav.su.se.bakover.domain.vilkår.utenlandsopphold.UtenlandsoppholdStatus
import no.nav.su.se.bakover.test.application.defaultRequest
import no.nav.su.se.bakover.web.routes.vilkår.utenlandsopphold.UtenlandsoppholdBody
import no.nav.su.se.bakover.web.routes.vilkår.utenlandsopphold.UtenlandsoppholdVurderingBody

internal fun leggTilUtenlandsoppholdRevurdering(
    sakId: String,
    behandlingId: String,
    fraOgMed: String = "2021-01-01",
    tilOgMed: String = "2021-12-31",
    vurdering: UtenlandsoppholdStatus = UtenlandsoppholdStatus.SkalHoldeSegINorge,
    begrunnelse: String = "Revurdering av utenlandsopphold",
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
    url: String = "/saker/$sakId/revurderinger/$behandlingId/utenlandsopphold",
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
                    UtenlandsoppholdBody(
                        vurderinger = listOf(
                            UtenlandsoppholdVurderingBody(
                                periode = PeriodeJson(fraOgMed = fraOgMed, tilOgMed = tilOgMed),
                                status = vurdering,
                            ),
                        ),
                    ),
                ),
            )
        }.apply {
            withClue("body=${this.bodyAsText()}") {
                status shouldBe HttpStatusCode.OK
                contentType() shouldBe ContentType.parse("application/json")
            }
        }.bodyAsText()
    }
}
