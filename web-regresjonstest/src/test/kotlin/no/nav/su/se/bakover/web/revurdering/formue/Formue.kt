package no.nav.su.se.bakover.web.revurdering.formue

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
import no.nav.su.se.bakover.web.routes.grunnlag.FormuegrunnlagJson
import no.nav.su.se.bakover.web.routes.revurdering.FormueBody

internal fun leggTilFormue(
    sakId: String,
    behandlingId: String,
    fraOgMed: String,
    tilOgMed: String,
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
    url: String = "/saker/$sakId/revurderinger/$behandlingId/formuegrunnlag",
    client: HttpClient,
    søkersFormue: FormuegrunnlagJson.VerdierJson = FormuegrunnlagJson.VerdierJson(
        verdiIkkePrimærbolig = 0,
        verdiEiendommer = 0,
        verdiKjøretøy = 0,
        innskudd = 6000,
        verdipapir = 5000,
        pengerSkyldt = 0,
        kontanter = 2000,
        depositumskonto = 4500,
    ),
    epsFormue: FormuegrunnlagJson.VerdierJson = FormuegrunnlagJson.VerdierJson(
        verdiIkkePrimærbolig = 0,
        verdiEiendommer = 0,
        verdiKjøretøy = 0,
        innskudd = 8000,
        verdipapir = 0,
        pengerSkyldt = 0,
        kontanter = 11000,
        depositumskonto = 0,
    ),
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
                    listOf(
                        FormueBody(
                            periode = PeriodeJson(fraOgMed = fraOgMed, tilOgMed = tilOgMed),
                            epsFormue = epsFormue,
                            søkersFormue = søkersFormue,
                            begrunnelse = "Lagt til automatisk av Formue.kt#leggTilFormue()",
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
