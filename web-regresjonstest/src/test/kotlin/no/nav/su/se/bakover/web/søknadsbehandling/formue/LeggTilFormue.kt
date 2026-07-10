package no.nav.su.se.bakover.web.søknadsbehandling.formue

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
import no.nav.su.se.bakover.web.routes.søknadsbehandling.vilkårOgGrunnlag.FormueBody

internal fun leggTilFormue(
    sakId: String,
    behandlingId: String,
    begrunnelse: String = "Vurdering av formue er lagt til automatisk av LeggTilFormue.kt",
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
    fraOgMed: String = "2021-01-01",
    tilOgMed: String = "2021-12-31",
    måInnhenteMerInformasjon: Boolean = false,
    harEps: Boolean = false,
    body: () -> String = {
        serialize(
            listOf(
                FormueBody(
                    periode = PeriodeJson(fraOgMed = fraOgMed, tilOgMed = tilOgMed),
                    epsFormue = if (harEps) formueVerdier() else null,
                    søkersFormue = formueVerdier(),
                    begrunnelse = begrunnelse,
                    måInnhenteMerInformasjon = måInnhenteMerInformasjon,
                ),
            ),
        )
    },
    client: HttpClient,
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/behandlinger/$behandlingId/formuegrunnlag",
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

/**
 * hardkodet defaults
 */
fun formueEpsJson(): String {
    return serialize(
        listOf(
            FormueBody(
                periode = PeriodeJson(fraOgMed = "2021-01-01", tilOgMed = "2021-12-31"),
                epsFormue = formueVerdier(),
                søkersFormue = formueVerdier(),
                begrunnelse = "Vurdering av formue er lagt til automatisk av LeggTilFormue.kt",
                måInnhenteMerInformasjon = false,
            ),
        ),
    )
}

private fun formueVerdier() = FormuegrunnlagJson.VerdierJson(
    verdiIkkePrimærbolig = 0,
    verdiEiendommer = 0,
    verdiKjøretøy = 0,
    innskudd = 0,
    verdipapir = 0,
    pengerSkyldt = 0,
    kontanter = 0,
    depositumskonto = 0,
)
