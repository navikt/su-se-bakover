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

internal fun oppdaterVedtaksbrevTilbakekrevingsbehandling(
    sakId: String,
    tilbakekrevingsbehandlingId: String,
    expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.Created,
    client: HttpClient,
    verifiserRespons: Boolean = true,
    saksversjon: Long,
    brevtekst: String? = "Regresjonstest: Fritekst til vedtaksbrev under tilbakekrevingsbehandling.",
    verifiserForhåndsvarselDokumenter: String,
    verifiserVurderinger: String,
): OppdatertVedtaksbrevTilbakekrevingsbehandlingRespons {
    // Dette kallet fører ikke til sideeffekter
    val sakFørKallJson = hentSak(sakId, client)
    return runBlocking {
        no.nav.su.se.bakover.test.application.defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/tilbakekreving/$tilbakekrevingsbehandlingId/brevtekst",
            listOf(Brukerrolle.Saksbehandler),
            client = client,
        ) {
            setBody(
                """
            {
                "versjon": $saksversjon,
                "brevtekst": ${brevtekst?.let { "\"$brevtekst\"" } ?: "null"}
            }
                """.trimIndent(),
            )
        }.apply {
            withClue("Kunne ikke forhåndsvarsle tilbakekrevingsbehandling: ${this.bodyAsText()}") {
                status shouldBe expectedHttpStatusCode
            }
        }.bodyAsText().let { responseJson ->
            val sakEtterKallJson = hentSak(sakId, client)
            val saksversjonEtter = JSONObject(sakEtterKallJson).getLong("versjon")
            if (verifiserRespons) {
                sakEtterKallJson.shouldBeSimilarJsonTo(sakFørKallJson, "versjon", "tilbakekrevinger")
                listOf(
                    responseJson,
                    JSONObject(sakEtterKallJson).getJSONArray("tilbakekrevinger").getJSONObject(0).toString(),
                ).forEach {
                    verifiserOppdatertVedtaksbrevTilbakekrevingsbehandlingRespons(
                        actual = it,
                        sakId = sakId,
                        brevtekst = brevtekst,
                        tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                        vurderinger = verifiserVurderinger,
                        forhåndsvarselDokumenter = verifiserForhåndsvarselDokumenter,
                        expectedVersjon = saksversjon + 1,
                    )
                }
            }
            OppdatertVedtaksbrevTilbakekrevingsbehandlingRespons(
                fritekst = hentFritekst(responseJson),
                saksversjon = saksversjonEtter,
            )
        }
    }
}

internal data class OppdatertVedtaksbrevTilbakekrevingsbehandlingRespons(
    val fritekst: String,
    val saksversjon: Long,
)

fun verifiserOppdatertVedtaksbrevTilbakekrevingsbehandlingRespons(
    actual: String,
    sakId: String,
    brevtekst: String?,
    tilbakekrevingsbehandlingId: String,
    vurderinger: String,
    forhåndsvarselDokumenter: String,
    expectedVersjon: Long,
) {
    val expected = """
{
  "id":$tilbakekrevingsbehandlingId,
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
  "status":"VEDTAKSBREV",
  "vurderinger":$vurderinger,
  "fritekst":"${brevtekst?.let { "$brevtekst" } ?: ""}",
  "forhåndsvarselsInfo": $forhåndsvarselDokumenter,
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
