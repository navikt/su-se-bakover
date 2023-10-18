package no.nav.su.se.bakover.web.tilbakekreving

import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.web.SharedRegressionTestData
import org.json.JSONObject
import org.skyscreamer.jsonassert.Customization
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import org.skyscreamer.jsonassert.comparator.CustomComparator

fun forhåndsvarsleTilbakekrevingsbehandling(
    sakId: String,
    tilbakekrevingsbehandlingId: String,
    expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.Created,
    client: HttpClient,
    verifiserRespons: Boolean = true,
    saksversjon: Long,
    fritekst: String = "Regresjonstest: Fritekst til forhåndsvarsel under tilbakekrevingsbehandling.",
): String {
    return runBlocking {
        SharedRegressionTestData.defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/tilbakekreving/$tilbakekrevingsbehandlingId/forhandsvarsel",
            listOf(Brukerrolle.Saksbehandler),
            client = client,
        ) { setBody("""{"versjon":$saksversjon,"fritekst":"$fritekst"}""") }.apply {
            withClue("Kunne ikke forhåndsvarsle tilbakekrevingsbehandling: ${this.bodyAsText()}") {
                status shouldBe expectedHttpStatusCode
            }
        }.bodyAsText().also {
            if (verifiserRespons) {
                verifiserForhåndsvarsletTilbakekrevingsbehandlingRespons(it, sakId)
            }
        }
    }
}

fun verifiserForhåndsvarsletTilbakekrevingsbehandlingRespons(
    actual: String,
    sakId: String,
) {
    val expected = """
{
  "id":"ignore-me",
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
  "status":"FORHÅNDSVARSLET",
  "månedsvurderinger":[],
  "fritekst":null,
  "forhåndsvarselDokumenter": ["ignore-me"]
}"""
    JSONAssert.assertEquals(
        expected,
        actual,
        CustomComparator(
            JSONCompareMode.STRICT,
            Customization(
                "id",
            ) { _, _ -> true },
            Customization(
                "forhåndsvarselDokumenter",
            ) { _, _ -> true },
        ),
    )
    JSONObject(actual).getJSONArray("forhåndsvarselDokumenter").shouldHaveSize(1)
}
