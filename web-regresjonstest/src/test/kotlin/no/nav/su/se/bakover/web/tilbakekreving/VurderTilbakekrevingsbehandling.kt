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
import no.nav.su.se.bakover.test.json.shouldBeSimilarJsonTo
import no.nav.su.se.bakover.web.sak.hent.hentSak
import org.json.JSONObject

internal fun vurderTilbakekrevingsbehandling(
    sakId: String,
    tilbakekrevingsbehandlingId: String,
    expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.Created,
    client: HttpClient,
    verifiserRespons: Boolean = true,
    saksversjon: Long,
    verifiserForhåndsvarselDokumenter: String = "[]",
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
            "betaltSkattForYtelsesgruppen":1192,
            "bruttoTidligereUtbetalt":10946,
            "bruttoNyUtbetaling":8563,
            "bruttoSkalTilbakekreve":2383,
            "nettoSkalTilbakekreve":1191,
            "bruttoSkalIkkeTilbakekreve":0,
            "skatteProsent":"50"
          }
        ],
        "eksternKravgrunnlagId":"123456",
        "eksternVedtakId":"654321",
        "eksternKontrollfelt":"2021-02-01-02.03.44.456789",
        "bruttoSkalTilbakekreveSummert":2383,
        "nettoSkalTilbakekreveSummert":1191,
        "bruttoSkalIkkeTilbakekreveSummert":0,
        "betaltSkattForYtelsesgruppenSummert":1192,
        "bruttoNyUtbetalingSummert":8563,
        "bruttoTidligereUtbetaltSummert":10946
      }
    """.trimIndent(),
    expectedFritekst: String? = null,
    expectedAttesteringer: String = "[]",
    expectedNotat: String? = null,
): VurderTilbakekrevingsbehandlingRespons {
    val sakFørKallJson = hentSak(sakId, client)
    return runBlocking {
        no.nav.su.se.bakover.test.application.defaultRequest(
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
        }.bodyAsText().let { responseJson ->
            // Dette kallet har ingen side-effekter.
            val sakEtterKallJson = hentSak(sakId, client)
            val saksversjonEtter = JSONObject(sakEtterKallJson).getLong("versjon")
            if (verifiserRespons) {
                sakEtterKallJson.shouldBeSimilarJsonTo(sakFørKallJson, "versjon", "tilbakekrevinger")
                saksversjonEtter shouldBe saksversjon + 1
                listOf(
                    responseJson,
                    JSONObject(sakEtterKallJson).getJSONArray("tilbakekrevinger").getJSONObject(0).toString(),
                ).forEach {
                    verifiserVurdertTilbakekrevingsbehandlingRespons(
                        actual = it,
                        sakId = sakId,
                        status = tilstand,
                        tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                        forhåndsvarselDokumenter = verifiserForhåndsvarselDokumenter,
                        expectedVersjon = saksversjon + 1,
                        expectedFritekst = expectedFritekst,
                        expectedAttesteringer = expectedAttesteringer,
                        expectedVurderinger = expectedVurderinger,
                        expectedNotat = expectedNotat,
                    )
                }
            }
            VurderTilbakekrevingsbehandlingRespons(
                saksversjon = saksversjonEtter,
                vurderinger = hentVurderinger(responseJson),
                responseJson = responseJson,
            )
        }
    }
}

internal data class VurderTilbakekrevingsbehandlingRespons(
    val vurderinger: String,
    val saksversjon: Long,
    val responseJson: String,
)

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
    expectedNotat: String?,
) {
    //language=json
    val expected = """
{
  "id":"$tilbakekrevingsbehandlingId",
  "sakId":"$sakId",
  "opprettet":"dette-sjekkes-av-opprettet-verifikasjonen",
  "opprettetAv":"Z990Lokal",
  "kravgrunnlag":{
    "eksternKravgrunnlagsId":"123456",
    "eksternVedtakId":"654321",
    "kontrollfelt":"2021-02-01-02.03.44.456789",
    "status":"NY",
    "grunnlagsperiode":[
      {
        "periode":{
          "fraOgMed":"2021-01-01",
          "tilOgMed":"2021-01-31"
        },
        "betaltSkattForYtelsesgruppen":"1192",
        "bruttoTidligereUtbetalt":"10946",
        "bruttoNyUtbetaling":"8563",
        "bruttoFeilutbetaling":"2383",
        "nettoFeilutbetaling": "1191",
        "skatteProsent":"50",
        "skattFeilutbetaling":"1192"
      }
    ],
    "summertBetaltSkattForYtelsesgruppen": "1192",
    "summertBruttoTidligereUtbetalt": 10946,
    "summertBruttoNyUtbetaling": 8563,
    "summertBruttoFeilutbetaling": 2383,
    "summertNettoFeilutbetaling": 1191,
    "summertSkattFeilutbetaling": 1192,
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
  "avsluttetTidspunkt": null,
  "notat": ${expectedNotat?.let { "\"$it\"" }}
}"""
    actual.shouldBeSimilarJsonTo(expected, "kravgrunnlag.hendelseId", "opprettet")
    JSONObject(actual).has("opprettet") shouldBe true
    JSONObject(actual).getJSONObject("kravgrunnlag").has("hendelseId") shouldBe true
}
