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
import tilbakekreving.domain.underkjent.UnderkjennAttesteringsgrunnTilbakekreving

fun underkjennTilbakekrevingsbehandling(
    sakId: String,
    tilbakekrevingsbehandlingId: String,
    expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.Created,
    client: HttpClient,
    verifiserRespons: Boolean = true,
    saksversjon: Long,
    brevtekst: String?,
    verifiserForhåndsvarselDokumenter: String,
    verifiserVurderinger: String,
    kommentar: String = "Underkjent av underkjennTilbakekrevingsbehandling() - TilbakekrevingsbehandlingIT",
    grunn: UnderkjennAttesteringsgrunnTilbakekreving = UnderkjennAttesteringsgrunnTilbakekreving.VURDERINGEN_ER_FEIL,
): Pair<String, Long> {
    val expectedVersjon: Long = saksversjon + 1
    return runBlocking {
        SharedRegressionTestData.defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/tilbakekreving/$tilbakekrevingsbehandlingId/underkjenn",
            listOf(Brukerrolle.Attestant),
            client = client,
        ) {
            setBody(
                //language=json
                """
                {
                    "versjon": $saksversjon,
                    "kommentar": "$kommentar",
                    "grunn": "$grunn"
                }
                """.trimIndent(),
            )
        }.apply {
            withClue("Kunne ikke underkjenne tilbakekrevingsbehandling: ${this.bodyAsText()}") {
                status shouldBe expectedHttpStatusCode
            }
        }.bodyAsText().also {
            if (verifiserRespons) {
                verifiserUnderkjentTilbakekrevingsbehandlingRespons(
                    actual = it,
                    sakId = sakId,
                    vurderinger = verifiserVurderinger,
                    tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                    forhåndsvarselDokumenter = verifiserForhåndsvarselDokumenter,
                    expectedVersjon = expectedVersjon,
                    brevtekst = brevtekst,
                    expectedGrunn = grunn.toString(),
                    expectedKommentar = kommentar,
                )
            }
        } to expectedVersjon
    }
}

fun verifiserUnderkjentTilbakekrevingsbehandlingRespons(
    actual: String,
    sakId: String,
    brevtekst: String?,
    tilbakekrevingsbehandlingId: String,
    vurderinger: String,
    forhåndsvarselDokumenter: String,
    expectedVersjon: Long,
    expectedGrunn: String,
    expectedKommentar: String,
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
  "status":"VEDTAKSBREV",
  "månedsvurderinger":$vurderinger,
  "fritekst": "$brevtekst",
  "forhåndsvarselDokumenter": $forhåndsvarselDokumenter,
  "sendtTilAttesteringAv": null,
  "versjon": $expectedVersjon,
  "attesteringer": [
    {
      "attestant": "Z990Lokal",
      "underkjennelse": {
        "grunn": "$expectedGrunn",
        "kommentar": "$expectedKommentar"
      },
    "opprettet": "2021-02-01T01:03:52.456789Z"
    }
  ]
}"""
    JSONAssert.assertEquals(
        expected,
        actual,
        true,
    )
}
