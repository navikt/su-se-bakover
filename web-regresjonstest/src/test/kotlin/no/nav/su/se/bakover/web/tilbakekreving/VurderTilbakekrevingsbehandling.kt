package no.nav.su.se.bakover.web.tilbakekreving

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.web.SharedRegressionTestData
import org.skyscreamer.jsonassert.JSONAssert

fun vurderTilbakekrevingsbehandling(
    sakId: String,
    tilbakekrevingsbehandlingId: String,
    expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.Created,
    client: HttpClient,
    verifiserRespons: Boolean = true,
    saksversjon: Long,
    verifiserForhåndsvarselDokumenter: String,
    vurderinger: String = """
        [
            {
                "måned": "2021-01",
                "vurdering": "SkalTilbakekreve"
            }
        ]
    """.trimIndent(),
): String {
    return runBlocking {
        SharedRegressionTestData.defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/tilbakekreving/$tilbakekrevingsbehandlingId/vurder",
            listOf(Brukerrolle.Saksbehandler),
            client = client,
        ) {
            setBody(
                """
            {
                "versjon": $saksversjon,
                "måneder": $vurderinger
            }
                """.trimIndent(),
            )
        }.apply {
            withClue("Kunne ikke forhåndsvarsle tilbakekrevingsbehandling: ${this.bodyAsText()}") {
                status shouldBe expectedHttpStatusCode
            }
        }.bodyAsText().also {
            if (verifiserRespons) {
                verifiserVurdertTilbakekrevingsbehandlingRespons(
                    actual = it,
                    sakId = sakId,
                    måneder = vurderinger,
                    tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                    forhåndsvarselDokumenter = verifiserForhåndsvarselDokumenter,
                )
            }
        }
    }
}

fun verifiserVurdertTilbakekrevingsbehandlingRespons(
    actual: String,
    tilbakekrevingsbehandlingId: String,
    forhåndsvarselDokumenter: String,
    sakId: String,
    måneder: String,
) {
    val expected = """
{
  "id":"$tilbakekrevingsbehandlingId",
  "sakId":"$sakId",
  "opprettet":"2021-02-01T01:03:43.456789Z",
  "opprettetAv":"Z990Lokal",
  "kravgrunnlag":{
    "eksternKravgrunnlagsId":"123456",
    "eksternVedtakId":"654321",
    "kontrollfelt":"2021-02-01-02.03.39.456789",
    "status":"NY",
    "grunnlagsperiode":[
      {
        "periode":{
          "fraOgMed":"2021-01-01",
          "tilOgMed":"2021-01-31"
        },
        "beløpSkattMnd":"6192",
          "ytelse": {
            "beløpTidligereUtbetaling":"20946",
            "beløpNyUtbetaling":"8563",
            "beløpSkalTilbakekreves":"12383",
            "beløpSkalIkkeTilbakekreves":"0",
            "skatteProsent":"50"
          },
      }
    ]
  },
  "status":"VURDERT",
  "månedsvurderinger":$måneder,
  "fritekst":null,
  "forhåndsvarselDokumenter": $forhåndsvarselDokumenter
}"""
    JSONAssert.assertEquals(
        expected,
        actual,
        true,
    )
}
