package no.nav.su.se.bakover.web.stans

import io.kotest.matchers.shouldBe
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.web.SharedRegressionTestData.defaultRequest

internal fun ApplicationTestBuilder.opprettStans(
    sakId: String,
    fraOgMed: String,
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
    revurderingsårsak: String = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING.toString(),
    begrunnelse: String = "Begrunnelse",
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/revurderinger/stans",
            listOf(brukerrolle),
            "automatiskOpprettetStans",
        ) {
            setBody(
                """
                   {
                    "fraOgMed": "$fraOgMed",
                    "årsak": "$revurderingsårsak",
                    "begrunnelse": "$begrunnelse"
                   }
                """.trimIndent(),
            )
        }.apply {
            status shouldBe HttpStatusCode.Created
            contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
        }.bodyAsText()
    }
}

internal fun ApplicationTestBuilder.iverksettStans(
    sakId: String,
    behandlingId: String,
    brukerrolle: Brukerrolle = Brukerrolle.Attestant,
    assertResponse: Boolean = true,
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/revurderinger/stans/$behandlingId/iverksett",
            listOf(brukerrolle),
            "automatiskIverksattStans",
        ).apply {
            if (assertResponse) {
                status shouldBe HttpStatusCode.OK
            }
            contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
        }.bodyAsText()
    }
}
