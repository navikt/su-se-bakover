package no.nav.su.se.bakover.web.søknadsbehandling.bosituasjon

import io.kotest.matchers.shouldBe
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.contentType
import io.ktor.server.testing.setBody
import no.nav.su.se.bakover.domain.bruker.Brukerrolle
import no.nav.su.se.bakover.web.SharedRegressionTestData.defaultRequest

internal fun TestApplicationEngine.taStillingTilEps(
    sakId: String,
    behandlingId: String,
    epsFnr: String? = null,
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
): String {
    return defaultRequest(
        HttpMethod.Post,
        "/saker/$sakId/behandlinger/$behandlingId/grunnlag/bosituasjon/eps",
        listOf(brukerrolle),
    ) {
        addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        setBody(
            //language=JSON
            """
              {
                "epsFnr":${if (epsFnr == null) null else "$epsFnr"}
              }
            """.trimIndent(),
        )
    }.apply {
        response.status() shouldBe HttpStatusCode.Created
        response.contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
    }.response.content!!
}

internal fun TestApplicationEngine.fullførBosituasjon(
    sakId: String,
    behandlingId: String,
    bosituasjon: String = "BOR_ALENE",
    begrunnelse: String = "Vurderinger rundt bosituasjon er lagt til automatisk av LeggTilBosituasjon.kt",
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
): String {
    return defaultRequest(
        HttpMethod.Post,
        "/saker/$sakId/behandlinger/$behandlingId/grunnlag/bosituasjon/fullfør",
        listOf(brukerrolle),
    ) {
        addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        setBody(
            //language=JSON
            """
                  {
                    "bosituasjon":"$bosituasjon",
                    "begrunnelse":"$begrunnelse"
                  }
            """.trimIndent(),
        )
    }.apply {
        response.status() shouldBe HttpStatusCode.Created
        response.contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
    }.response.content!!
}
