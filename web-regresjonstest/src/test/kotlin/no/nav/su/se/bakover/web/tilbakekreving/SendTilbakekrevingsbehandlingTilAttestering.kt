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

fun sendTilbakekrevingsbehandlingTilAttestering(
    sakId: String,
    tilbakekrevingsbehandlingId: String,
    expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.Created,
    client: HttpClient,
    verifiserRespons: Boolean = true,
    saksversjon: Long,
    verifiserForhåndsvarselDokumenter: String,
    verifiserVurderinger: String,
    verifiserFritekst: String,
    expectedAttesteringer: String = "[]",
): Pair<String, Long> {
    val expectedVersjon: Long = saksversjon + 1
    return runBlocking {
        SharedRegressionTestData.defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/tilbakekreving/$tilbakekrevingsbehandlingId/tilAttestering",
            listOf(Brukerrolle.Saksbehandler),
            client = client,
        ) { setBody("""{"versjon":$saksversjon}""") }.apply {
            withClue("Kunne ikke sende tilbakekrevingsbehandling til attestering: ${this.bodyAsText()}") {
                status shouldBe expectedHttpStatusCode
            }
        }.bodyAsText().also {
            if (verifiserRespons) {
                verifiserTilbakekrevingsbehandlingTilAttesteringRespons(
                    actual = it,
                    sakId = sakId,
                    tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                    forhåndsvarselDokumenter = verifiserForhåndsvarselDokumenter,
                    vurderinger = verifiserVurderinger,
                    fritekst = verifiserFritekst,
                    expectedVersjon = expectedVersjon,
                    expectedAttesteringer = expectedAttesteringer,
                )
            }
        } to expectedVersjon
    }
}

fun verifiserTilbakekrevingsbehandlingTilAttesteringRespons(
    actual: String,
    tilbakekrevingsbehandlingId: String,
    forhåndsvarselDokumenter: String,
    sakId: String,
    vurderinger: String,
    fritekst: String,
    expectedVersjon: Long,
    expectedAttesteringer: String,
) {
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
          "skatteProsent":"50",
            "nettoBeløp": "6191"
          },
      }
    ],
    "summertGrunnlagsmåneder":{
        "betaltSkattForYtelsesgruppen":"6192",
        "beløpTidligereUtbetaling":"20946",
        "beløpNyUtbetaling":"8563",
        "beløpSkalTilbakekreves":"12383",
        "beløpSkalIkkeTilbakekreves":"0",
        "nettoBeløp": "6191"
    } 
  },
  "status":"TIL_ATTESTERING",
  "månedsvurderinger":$vurderinger,
  "fritekst":"$fritekst",
  "forhåndsvarselsInfo": $forhåndsvarselDokumenter,
  "sendtTilAttesteringAv": "Z990Lokal",
  "versjon": $expectedVersjon,
  "attesteringer": $expectedAttesteringer,
  "erKravgrunnlagUtdatert": false
}"""
    JSONAssert.assertEquals(
        expected,
        actual,
        true,
    )
}
