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

fun avbrytTilbakekrevingsbehandling(
    sakId: String,
    tilbakekrevingsbehandlingId: String,
    expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.Created,
    client: HttpClient,
    verifiserRespons: Boolean = true,
    saksversjon: Long,
): Pair<String, Long> {
    val expectedVersjon: Long = saksversjon + 1
    return runBlocking {
        SharedRegressionTestData.defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/tilbakekreving/$tilbakekrevingsbehandlingId/avbryt",
            listOf(Brukerrolle.Saksbehandler),
            client = client,
        ) {
            setBody("""{"versjon": $saksversjon, "fritekst": "friteksten", "begrunnelse": "begrunnelsen", "skalSendeBrev": "SKAL_SENDE_BREV_MED_FRITEKST"}""")
        }.apply {
            withClue("Kunne ikke avbryte tilbakekrevingsbehandling: ${this.bodyAsText()}") {
                status shouldBe expectedHttpStatusCode
            }
        }.bodyAsText().also {
            if (verifiserRespons) {
                verifiserAvbrytTilbakekrevingRespons(
                    actual = it,
                    tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                    sakId = sakId,
                    expectedVersjon = expectedVersjon,
                )
            }
        } to expectedVersjon
    }
}

fun verifiserAvbrytTilbakekrevingRespons(
    actual: String,
    tilbakekrevingsbehandlingId: String,
    sakId: String,
    expectedVersjon: Long,
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
    "kontrollfelt":"2021-02-01-02.04.36.456789",
    "status":"NY",
    "grunnlagsperiode":[
      {
        "periode":{
          "fraOgMed":"2021-01-01",
          "tilOgMed":"2021-01-31"
        },
        "beløpSkattMnd":"3025",
          "ytelse": {
            "beløpTidligereUtbetaling":"8563",
            "beløpNyUtbetaling":"2513",
            "beløpSkalTilbakekreves":"6050",
            "beløpSkalIkkeTilbakekreves":"0",
            "skatteProsent":"50",
            "nettoBeløp": "3025"
          },
      }
    ],
    "summertGrunnlagsmåneder":{
        "betaltSkattForYtelsesgruppen":"3025",
        "beløpTidligereUtbetaling":"8563",
        "beløpNyUtbetaling":"2513",
        "beløpSkalTilbakekreves":"6050",
        "beløpSkalIkkeTilbakekreves":"0",
        "nettoBeløp": "3025"
    } 
  },
  "status":"AVBRUTT",
  "månedsvurderinger":[],
  "fritekst":null,
  "forhåndsvarselsInfo": [],
  "sendtTilAttesteringAv": null,
  "versjon": $expectedVersjon,
  "attesteringer": [],
  "erKravgrunnlagUtdatert": false,
  "avsluttetTidspunkt": "2021-02-01T01:04:44.456789Z"
}"""
    JSONAssert.assertEquals(
        expected,
        actual,
        true,
    )
}
