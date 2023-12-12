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
import org.skyscreamer.jsonassert.Customization
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import org.skyscreamer.jsonassert.comparator.CustomComparator

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
    "kontrollfelt":"2021-02-01-02.04.37.456789",
    "status":"NY",
    "grunnlagsperiode":[
      {
        "periode":{
          "fraOgMed":"2021-01-01",
          "tilOgMed":"2021-01-31"
        },
        "betaltSkattForYtelsesgruppen":"3025",
        "bruttoTidligereUtbetalt":"8563",
        "bruttoNyUtbetaling":"2513",
        "bruttoFeilutbetaling":"6050", 
        "nettoFeilutbetaling": "3025",
        "skatteProsent":"50",
        "skattFeilutbetaling":"3025"
      }
    ],
    "summertBetaltSkattForYtelsesgruppen": "3025",
    "summertBruttoTidligereUtbetalt": 8563,
    "summertBruttoNyUtbetaling": 2513,
    "summertBruttoFeilutbetaling": 6050,
    "summertNettoFeilutbetaling": 3025,
    "summertSkattFeilutbetaling": 3025,
    "hendelseId": "ignoreres-siden-denne-opprettes-av-tjenesten"
  },
  "status":"AVBRUTT",
  "vurderinger":null,
  "fritekst":null,
  "forhåndsvarselsInfo": [],
  "sendtTilAttesteringAv": null,
  "versjon": $expectedVersjon,
  "attesteringer": [],
  "erKravgrunnlagUtdatert": false,
  "avsluttetTidspunkt": "2021-02-01T01:04:42.456789Z",
  "notat": null,
}"""
    JSONAssert.assertEquals(
        expected,
        actual,
        CustomComparator(
            JSONCompareMode.STRICT,
            Customization("kravgrunnlag.hendelseId") { _, _ -> true },
        ),
    )
}
