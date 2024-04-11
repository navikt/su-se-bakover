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

internal fun oppdaterKravgrunnlag(
    sakId: String,
    tilbakekrevingsbehandlingId: String,
    expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.Created,
    client: HttpClient,
    verifiserRespons: Boolean = true,
    saksversjon: Long,
): OppdaterKravgrunnlagTilbakekrevingsbehandlingRespons {
    // Dette kallet fører ikke til sideeffekter
    val sakFørKallJson = hentSak(sakId, client)
    return runBlocking {
        no.nav.su.se.bakover.test.application.defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/tilbakekreving/$tilbakekrevingsbehandlingId/oppdaterKravgrunnlag",
            listOf(Brukerrolle.Saksbehandler),
            client = client,
        ) {
            setBody(
                """{"versjon": $saksversjon}""",
            )
        }.apply {
            withClue("Kunne ikke oppdatere kravgrunnlag på tilbakekrevingsbehandling: ${this.bodyAsText()}") {
                status shouldBe expectedHttpStatusCode
            }
        }.bodyAsText().let { responseJson ->
            val sakEtterKallJson = hentSak(sakId, client)
            val saksversjonEtter = JSONObject(sakEtterKallJson).getLong("versjon")
            if (verifiserRespons) {
                sakEtterKallJson.shouldBeSimilarJsonTo(sakFørKallJson, "versjon", "tilbakekrevinger")
                val tilbakekrevingerResponseFromSak = JSONObject(sakEtterKallJson).getJSONArray("tilbakekrevinger").let {
                    it.length() shouldBe 1
                    it.getJSONObject(0).toString()
                }
                responseJson.shouldBeSimilarJsonTo(tilbakekrevingerResponseFromSak)
                listOf(
                    responseJson,
                    tilbakekrevingerResponseFromSak,
                ).forEach {
                    verifiserOppdatertKravgrunnlagRespons(
                        actual = it,
                        sakId = sakId,
                        tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                        expectedVersjon = saksversjon + 1,
                    )
                }
            }
            OppdaterKravgrunnlagTilbakekrevingsbehandlingRespons(
                responsJson = responseJson,
                saksversjon = saksversjonEtter,
            )
        }
    }
}

internal data class OppdaterKravgrunnlagTilbakekrevingsbehandlingRespons(
    val responsJson: String,
    val saksversjon: Long,
)

fun verifiserOppdatertKravgrunnlagRespons(
    actual: String,
    tilbakekrevingsbehandlingId: String,
    sakId: String,
    expectedVersjon: Long,
) {
    // TODO tilbakekreving jah: Emulering av simuleringen vil være feil her. Den antar at vi tar stilling til feilutbetalingen per revurdering, men det er ikke tilfelle lenger. Vi må endre simuleringstub til å ta høyde for dette.
    val expected = """
{
  "id":"$tilbakekrevingsbehandlingId",
  "sakId":"$sakId",
  "opprettet":"dette-sjekkes-av-opprettet-verifikasjonen",
  "opprettetAv":"Z990Lokal",
  "kravgrunnlag":{
    "eksternKravgrunnlagsId":"123456",
    "eksternVedtakId":"654321",
    "kontrollfelt":"2021-02-01-02.04.28.456789",
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
  "status":"FORHÅNDSVARSLET",
  "vurderinger":null,
  "fritekst":null,
  "forhåndsvarselsInfo": [],
  "sendtTilAttesteringAv": null,
  "versjon": $expectedVersjon,
  "attesteringer": [],
  "erKravgrunnlagUtdatert": false,
  "avsluttetTidspunkt": null,
  "notat": null,
}"""
    actual.shouldBeSimilarJsonTo(expected, "kravgrunnlag.hendelseId", "opprettet")
    JSONObject(actual).has("opprettet") shouldBe true
    JSONObject(actual).getJSONObject("kravgrunnlag").has("hendelseId") shouldBe true
}
