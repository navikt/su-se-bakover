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
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.web.SharedRegressionTestData
import org.skyscreamer.jsonassert.JSONAssert

fun iverksettTilbakekrevingsbehandling(
    sakId: String,
    tilbakekrevingsbehandlingId: String,
    expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.Created,
    client: HttpClient,
    verifiserRespons: Boolean = true,
    attestant: NavIdentBruker.Attestant = NavIdentBruker.Attestant("AttestantLokal"),
    saksversjon: Long,
    verifiserForhåndsvarselDokumenter: String,
    verifiserVurderinger: String,
    verifiserFritekst: String,
    tidligereAttesteringer: String? = null,
): String {
    return runBlocking {
        SharedRegressionTestData.defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/tilbakekreving/$tilbakekrevingsbehandlingId/iverksett",
            listOf(Brukerrolle.Attestant),
            client = client,
            navIdent = attestant.toString(),
        ) { setBody("""{"versjon":$saksversjon}""") }.apply {
            withClue("Kunne ikke sende tilbakekrevingsbehandling til attestering: ${this.bodyAsText()}") {
                status shouldBe expectedHttpStatusCode
            }
        }.bodyAsText().also {
            if (verifiserRespons) {
                verifiserIverksattTilbakekrevingsbehandlingRespons(
                    actual = it,
                    sakId = sakId,
                    tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                    forhåndsvarselDokumenter = verifiserForhåndsvarselDokumenter,
                    vurderinger = verifiserVurderinger,
                    fritekst = verifiserFritekst,
                    expectedVersjon = saksversjon + 1,
                    tidligereAttesteringer = tidligereAttesteringer,
                )
            }
        }
    }
}

fun verifiserIverksattTilbakekrevingsbehandlingRespons(
    actual: String,
    tilbakekrevingsbehandlingId: String,
    forhåndsvarselDokumenter: String,
    sakId: String,
    vurderinger: String,
    fritekst: String,
    expectedVersjon: Long,
    tidligereAttesteringer: String?,
) {
    //language=json
    val expected = """
{
  "id":$tilbakekrevingsbehandlingId,
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
        }
      }
    ]
  },
  "status":"IVERKSATT",
  "månedsvurderinger":$vurderinger,
  "fritekst":"$fritekst",
  "forhåndsvarselDokumenter": $forhåndsvarselDokumenter,
  "sendtTilAttesteringAv": "Z990Lokal",
  "versjon": $expectedVersjon,
  "attesteringer": [
    ${tidligereAttesteringer?.removeFirstAndLastCharacter()?.let { "$it," } ?: ""}
    {
      "attestant": "AttestantLokal",
      "underkjennelse":null,
       "opprettet": ${if (tidligereAttesteringer == null) "\"2021-02-01T01:03:54.456789Z\"" else "\"2021-02-01T01:03:57.456789Z\""}  
    }
  ] 
}"""
    JSONAssert.assertEquals(
        expected,
        actual,
        true,
    )
}

fun String.removeFirstAndLastCharacter(): String {
    return if (this.length >= 2) {
        this.substring(1, this.length - 1)
    } else {
        // Handle the case where the input string has fewer than 2 characters (e.g., it's empty or has only one character).
        ""
    }
}
