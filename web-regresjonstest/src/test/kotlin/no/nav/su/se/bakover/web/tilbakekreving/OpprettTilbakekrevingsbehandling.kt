package no.nav.su.se.bakover.web.tilbakekreving

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.web.SharedRegressionTestData
import org.json.JSONObject
import org.skyscreamer.jsonassert.Customization
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import org.skyscreamer.jsonassert.comparator.CustomComparator

/**
 * Oppretter en tilbakekrevingsbehandling for en gitt sak.
 * Kjører også konsumenten som lytter på disse hendelsene for å opprette en oppgave.
 */
fun opprettTilbakekrevingsbehandling(
    sakId: String,
    expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.Created,
    client: HttpClient,
    verifiserRespons: Boolean = true,
    saksversjon: Long,
    expectedOpprettet: String = "2021-02-01T01:03:32.456789Z",
    expectedKontrollfelt: String = "2021-02-01-02.03.28.456789",
): Pair<String, Long> {
    return runBlocking {
        val correlationId = CorrelationId.generate()
        SharedRegressionTestData.defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/tilbakekreving/ny",
            listOf(Brukerrolle.Saksbehandler),
            client = client,
            correlationId = correlationId.toString(),
        ) { setBody("""{"versjon":$saksversjon}""") }.apply {
            withClue("opprettTilbakekrevingsbehandling feilet: ${this.bodyAsText()}") {
                status shouldBe expectedHttpStatusCode
            }
        }.bodyAsText().also {
            if (verifiserRespons) {
                verifiserOpprettetTilbakekrevingsbehandlingRespons(
                    actual = it,
                    sakId = sakId,
                    expectedVersjon = saksversjon + 1,
                    expectedOpprettet = expectedOpprettet,
                    expectedKontrollfelt = expectedKontrollfelt,
                )
            }
        } to saksversjon + 1
    }
}

fun verifiserOpprettetTilbakekrevingsbehandlingRespons(
    actual: String,
    sakId: String,
    expectedVersjon: Long,
    expectedOpprettet: String = "2021-02-01T01:03:32.456789Z",
    expectedKontrollfelt: String = "2021-02-01-02.03.28.456789",
) {
    val expected = """
{
  "id":"ignoreres-siden-denne-opprettes-av-tjenesten",
  "sakId":"$sakId",
  "opprettet":"$expectedOpprettet",
  "opprettetAv":"Z990Lokal",
  "kravgrunnlag":{
    "eksternKravgrunnlagsId":"123456",
    "eksternVedtakId":"654321",
    "kontrollfelt":"$expectedKontrollfelt",
    "status":"NY",
    "grunnlagsperiode":[
      {
        "periode":{
          "fraOgMed":"2021-01-01",
          "tilOgMed":"2021-01-31"
        },
        "betaltSkattForYtelsesgruppen":"6192",
        "bruttoTidligereUtbetalt":"20946",
        "bruttoNyUtbetaling":"8563",
        "bruttoFeilutbetaling":"12383",
        "skatteProsent":"50"
      }
    ],
    "summertBetaltSkattForYtelsesgruppen": "6192",
    "summertBruttoTidligereUtbetalt": 20946,
    "summertBruttoNyUtbetaling": 8563,
    "summertBruttoFeilutbetaling": 12383,
    "summertNettoFeilutbetaling": 6191,
    "summertSkattFeilutbetaling": 6192,
    "hendelseId": "ignoreres-siden-denne-opprettes-av-tjenesten"
  },
  "status":"OPPRETTET",
  "vurderinger":null,
  "fritekst":null,
  "forhåndsvarselsInfo": [],
  "sendtTilAttesteringAv": null,
  "versjon": $expectedVersjon,
  "attesteringer": [],
  "erKravgrunnlagUtdatert": false,
  "avsluttetTidspunkt": null
}"""
    JSONAssert.assertEquals(
        expected,
        actual,
        CustomComparator(
            JSONCompareMode.STRICT,
            Customization("id") { _, _ -> true },
            Customization("kravgrunnlag.hendelseId") { _, _ -> true },
        ),
    )
    JSONObject(actual).has("id") shouldBe true
}
