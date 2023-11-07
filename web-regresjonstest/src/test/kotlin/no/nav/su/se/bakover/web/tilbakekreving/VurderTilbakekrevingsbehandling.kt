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
    tilstand: String = "VURDERT",
    vurderinger: String = """
        [
            {
                "måned": "2021-01",
                "vurdering": "SkalTilbakekreve"
            }
        ]
    """.trimIndent(),
    expectedFritekst: String? = null,
    expectedAttesteringer: String = "[]",
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
                    status = tilstand,
                    tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                    forhåndsvarselDokumenter = verifiserForhåndsvarselDokumenter,
                    expectedVersjon = saksversjon + 1,
                    expectedFritekst = expectedFritekst,
                    expectedAttesteringer = expectedAttesteringer,
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
    status: String,
    måneder: String,
    expectedVersjon: Long,
    expectedFritekst: String?,
    expectedAttesteringer: String,
) {
    //language=json
    val expected = """
{
  "id":"$tilbakekrevingsbehandlingId",
  "sakId":"$sakId",
  "opprettet":"2021-02-01T01:03:32.456789Z",
  "opprettetAv":"Z990Lokal",
  "kravgrunnlag":{
    "eksternKravgrunnlagsId":"123456",
    "eksternVedtakId":"654321",
    "kontrollfelt":"2021-02-01-02.03.28.456789",
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
          }
      }
    ]
  },
  "status":"$status",
  "månedsvurderinger":$måneder,
  "fritekst": ${expectedFritekst?.let { "\"$it\"" }},
  "forhåndsvarselDokumenter": $forhåndsvarselDokumenter,
  "sendtTilAttesteringAv": null,
  "versjon": $expectedVersjon,
  "attesteringer": $expectedAttesteringer
}"""
    JSONAssert.assertEquals(
        expected,
        actual,
        true,
    )
}
