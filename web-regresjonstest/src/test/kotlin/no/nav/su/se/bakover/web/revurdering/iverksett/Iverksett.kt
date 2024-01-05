package no.nav.su.se.bakover.web.revurdering.iverksett

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.test.application.defaultRequest
import no.nav.su.se.bakover.web.komponenttest.AppComponents
import no.nav.su.se.bakover.web.komponenttest.mottaKvitteringForUtbetalingFraØkonomi
import java.util.UUID

internal fun iverksett(
    sakId: String,
    behandlingId: String,
    brukerrolle: Brukerrolle = Brukerrolle.Attestant,
    url: String = "/saker/$sakId/revurderinger/$behandlingId/iverksett",
    assertResponse: Boolean = true,
    client: HttpClient,
    appComponents: AppComponents?,
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            url,
            listOf(brukerrolle),
            "automatiskAttesteringAvSøknadsbehandling",
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
