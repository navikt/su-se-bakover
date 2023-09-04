package no.nav.su.se.bakover.web.revurdering.gjenopptak

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

internal fun opprettGjenopptak(
    sakId: String,
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
    revurderingsårsak: String = Revurderingsårsak.Årsak.MOTTATT_KONTROLLERKLÆRING.toString(),
    begrunnelse: String = "Begrunnelse",
    client: HttpClient,
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/revurderinger/gjenoppta",
            listOf(brukerrolle),
            "automatiskOpprettetGjenopptak",
            client = client,
        ) {
            setBody(
                """
                   {
                    "årsak": "$revurderingsårsak",
                    "begrunnelse": "$begrunnelse"
                   }
                """.trimIndent(),
            )
        }.apply {
            status shouldBe HttpStatusCode.Created
            contentType() shouldBe ContentType.parse("application/json")
        }.bodyAsText()
    }
}

internal fun iverksettGjenopptak(
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
            "/saker/$sakId/revurderinger/gjenoppta/$behandlingId/iverksett",
            listOf(brukerrolle),
            "automatiskIverksattGjenopptak",
            client = client,
        ).apply {
            if (assertResponse) {
                status shouldBe HttpStatusCode.OK
            }
            contentType() shouldBe ContentType.parse("application/json")
        }.bodyAsText().also {
            appComponents?.mottaKvitteringForUtbetalingFraØkonomi(sakId = UUID.fromString(sakId))
        }
    }
}
