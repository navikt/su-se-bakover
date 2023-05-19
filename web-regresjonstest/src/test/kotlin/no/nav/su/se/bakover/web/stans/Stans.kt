package no.nav.su.se.bakover.web.stans

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
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.web.SharedRegressionTestData.defaultRequest
import no.nav.su.se.bakover.web.komponenttest.AppComponents
import no.nav.su.se.bakover.web.komponenttest.mottaKvitteringForUtbetalingFraØkonomi
import java.util.UUID

internal fun opprettStans(
    sakId: String,
    fraOgMed: String,
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
    revurderingsårsak: String = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING.toString(),
    begrunnelse: String = "Begrunnelse",
    client: HttpClient,
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/revurderinger/stans",
            listOf(brukerrolle),
            "automatiskOpprettetStans",
            client = client,
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
            withClue("opprettStans feilet med status: $status og body: ${this.bodyAsText()}") {
                status shouldBe HttpStatusCode.Created
                contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
            }
        }.bodyAsText()
    }
}

internal fun iverksettStans(
    sakId: String,
    behandlingId: String,
    brukerrolle: Brukerrolle = Brukerrolle.Attestant,
    assertResponse: Boolean = true,
    client: HttpClient,
    appComponents: AppComponents?,
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/revurderinger/stans/$behandlingId/iverksett",
            listOf(brukerrolle),
            "automatiskIverksattStans",
            client = client,
        ).apply {
            withClue("iverksettStans feilet med status: $status og body: ${this.bodyAsText()}") {
                if (assertResponse) {
                    status shouldBe HttpStatusCode.OK
                }
                contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
            }
        }.bodyAsText().also {
            appComponents?.mottaKvitteringForUtbetalingFraØkonomi(sakId = UUID.fromString(sakId))
        }
    }
}
