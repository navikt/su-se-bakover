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
import tilbakekreving.application.service.consumer.OpprettOppgaveForTilbakekrevingshendelserKonsument

/**
 * Oppretter en tilbakekrevingsbehandling for en gitt sak.
 * Kjører også konsumenten som lytter på disse hendelsene for å opprette en oppgave.
 */
fun opprettTilbakekrevingsbehandling(
    sakId: String,
    expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.Created,
    client: HttpClient,
    opprettOppgaveForTilbakekrevingshendelserKonsument: OpprettOppgaveForTilbakekrevingshendelserKonsument,
    verifiserRespons: Boolean = true,
    saksversjon: Long,
): String {
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
                )
            }
            opprettOppgaveForTilbakekrevingshendelserKonsument.opprettOppgaver(
                correlationId = correlationId,
            )
        }
    }
}

fun verifiserOpprettetTilbakekrevingsbehandlingRespons(
    actual: String,
    sakId: String,
    expectedVersjon: Long,
) {
    val expected = """
{
  "id":"ignoreres-siden-denne-opprettes-av-tjenesten",
  "sakId":"$sakId",
  "opprettet":"2021-02-01T01:03:42.456789Z",
  "opprettetAv":"Z990Lokal",
  "kravgrunnlag":{
    "eksternKravgrunnlagsId":"123456",
    "eksternVedtakId":"654321",
    "kontrollfelt":"2021-02-01-02.03.38.456789",
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
  "status":"OPPRETTET",
  "månedsvurderinger":[],
  "fritekst":null,
  "forhåndsvarselDokumenter": [],
  "sendtTilAttesteringAv": null,
  "versjon": $expectedVersjon,
  "attesteringer": [],
}"""
    JSONAssert.assertEquals(
        expected,
        actual,
        CustomComparator(
            JSONCompareMode.STRICT,
            Customization(
                "id",
            ) { _, _ -> true },
        ),
    )
    JSONObject(actual).has("id") shouldBe true
}
