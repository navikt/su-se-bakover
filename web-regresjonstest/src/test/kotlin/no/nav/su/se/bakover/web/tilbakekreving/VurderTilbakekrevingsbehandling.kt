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

fun vurderTilbakekrevingsbehandling(
    sakId: String,
    tilbakekrevingsbehandlingId: String,
    expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.Created,
    client: HttpClient,
    verifiserRespons: Boolean = true,
    saksversjon: Long,
    verifiserForhåndsvarselDokumenter: String,
    tilstand: String = "VURDERT",
    vurderingerRequest: String = """
        [
            {
                "periode": {
                    "fraOgMed": "2021-01-01",
                    "tilOgMed": "2021-01-31"
                },
                "vurdering": "SkalTilbakekreve"
            }
        ]
    """.trimIndent(),
    expectedVurderinger: String = """
      {
        "perioder":[
          {
            "periode":{
              "fraOgMed":"2021-01-01",
              "tilOgMed":"2021-01-31"
            },
            "vurdering":"SkalTilbakekreve",
            "betaltSkattForYtelsesgruppen":6192,
            "bruttoTidligereUtbetalt":20946,
            "bruttoNyUtbetaling":8563,
            "bruttoSkalTilbakekreve":12383,
            "nettoSkalTilbakekreve":6191,
            "bruttoSkalIkkeTilbakekreve":0,
            "skatteProsent":"50"
          }
        ],
        "eksternKravgrunnlagId":"123456",
        "eksternVedtakId":"654321",
        "eksternKontrollfelt":"2021-02-01-02.03.28.456789",
        "bruttoSkalTilbakekreveSummert":12383,
        "nettoSkalTilbakekreveSummert":6191,
        "bruttoSkalIkkeTilbakekreveSummert":0,
        "betaltSkattForYtelsesgruppenSummert":6192,
        "bruttoNyUtbetalingSummert":8563,
        "bruttoTidligereUtbetaltSummert":20946
      }
    """.trimIndent(),
    expectedFritekst: String? = null,
    expectedAttesteringer: String = "[]",
): Pair<String, Long> {
    val expectedVersjon: Long = saksversjon + 1
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
                "perioder": $vurderingerRequest
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
                    status = tilstand,
                    tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                    forhåndsvarselDokumenter = verifiserForhåndsvarselDokumenter,
                    expectedVersjon = expectedVersjon,
                    expectedFritekst = expectedFritekst,
                    expectedAttesteringer = expectedAttesteringer,
                    expectedVurderinger = expectedVurderinger,
                )
            }
        } to expectedVersjon
    }
}

fun verifiserVurdertTilbakekrevingsbehandlingRespons(
    actual: String,
    tilbakekrevingsbehandlingId: String,
    forhåndsvarselDokumenter: String,
    sakId: String,
    status: String,
    expectedVersjon: Long,
    expectedFritekst: String?,
    expectedAttesteringer: String,
    expectedVurderinger: String,
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
  "status":"$status",
  "vurderinger":$expectedVurderinger,
  "fritekst": ${expectedFritekst?.let { "\"$it\"" }},
  "forhåndsvarselsInfo": $forhåndsvarselDokumenter,
  "sendtTilAttesteringAv": null,
  "versjon": $expectedVersjon,
  "attesteringer": $expectedAttesteringer,
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
