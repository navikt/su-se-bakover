package no.nav.su.se.bakover.web.søknadsbehandling.iverksett

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
import no.nav.su.se.bakover.test.application.defaultRequest
import no.nav.su.se.bakover.web.komponenttest.AppComponents
import no.nav.su.se.bakover.web.komponenttest.mottaKvitteringOgFerdigstillVedtak
import java.util.UUID

internal fun iverksett(
    sakId: String,
    behandlingId: String,
    brukerrolle: Brukerrolle = Brukerrolle.Attestant,
    assertResponse: Boolean = true,
    client: HttpClient,
    navIdent: String = "automatiskAttesteringAvSøknadsbehandling",
    appComponents: AppComponents?,
    fritekst: String = "Send til attestering er kjørt automatisk av SendTilAttestering.kt",
): String {
    return runBlocking {
        defaultRequest(
            method = HttpMethod.Post,
            uri = "/saker/$sakId/behandlinger/$behandlingId/iverksett",
            roller = listOf(brukerrolle),
            navIdent = navIdent,
            client = client,
        ) {
            setBody(
                //language=JSON
                """
              {
                "fritekst": "$fritekst"
              }
                """.trimIndent(),
            )
        }
            .apply {
                if (assertResponse) {
                    status shouldBe HttpStatusCode.OK
                }
                contentType() shouldBe ContentType.parse("application/json")
            }.bodyAsText().also {
                appComponents?.mottaKvitteringOgFerdigstillVedtak(sakId = UUID.fromString(sakId))
            }
    }
}
