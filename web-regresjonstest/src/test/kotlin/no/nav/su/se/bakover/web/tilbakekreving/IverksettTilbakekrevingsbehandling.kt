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
import org.skyscreamer.jsonassert.Customization
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import org.skyscreamer.jsonassert.comparator.CustomComparator

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
): Pair<String, Long> {
    val expectedVersjon: Long = saksversjon + 1
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
                    expectedVersjon = expectedVersjon,
                    tidligereAttesteringer = tidligereAttesteringer,
                )
            }
        } to expectedVersjon
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
        "betaltSkattForYtelsesgruppen":"6192",
        "bruttoTidligereUtbetalt":"20946",
        "bruttoNyUtbetaling":"8563",
        "bruttoFeilutbetaling":"12383", 
        "nettoFeilutbetaling": "6191",
        "skatteProsent":"50",
        "skattFeilutbetaling":"6192"
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
  "status":"IVERKSATT",
  "vurderinger":$vurderinger,
  "fritekst":"$fritekst",
  "forhåndsvarselsInfo": $forhåndsvarselDokumenter,
  "sendtTilAttesteringAv": "Z990Lokal",
  "versjon": $expectedVersjon,
  "attesteringer": [
    ${tidligereAttesteringer?.removeFirstAndLastCharacter()?.let { "$it," } ?: ""}
    {
      "attestant": "AttestantLokal",
      "underkjennelse":null,
       "opprettet": ${if (tidligereAttesteringer == null) "\"2021-02-01T01:03:54.456789Z\"" else "\"2021-02-01T01:04:01.456789Z\""}  
    }
  ],
  "erKravgrunnlagUtdatert": false,
  "avsluttetTidspunkt": null
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

fun String.removeFirstAndLastCharacter(): String {
    return if (this.length >= 2) {
        this.substring(1, this.length - 1)
    } else {
        // Handle the case where the input string has fewer than 2 characters (e.g., it's empty or has only one character).
        ""
    }
}
