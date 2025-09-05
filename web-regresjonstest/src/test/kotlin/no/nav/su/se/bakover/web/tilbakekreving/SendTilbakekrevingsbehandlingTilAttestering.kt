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
import no.nav.su.se.bakover.web.komponenttest.AppComponents
import no.nav.su.se.bakover.web.sak.hent.hentSak
import org.json.JSONObject

internal fun AppComponents.sendTilbakekrevingsbehandlingTilAttestering(
    sakId: String,
    tilbakekrevingsbehandlingId: String,
    expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.Created,
    client: HttpClient,
    verifiserRespons: Boolean = true,
    utførSideeffekter: Boolean = true,
    saksversjon: Long,
    verifiserForhåndsvarselDokumenter: String,
    verifiserVurderinger: String,
    verifiserFritekst: String,
    expectedAttesteringer: String = "[]",
): SendTilbakekrevingsbehandlingTilAttesteringRespons {
    val appComponents = this
    val sakFørKallJson = hentSak(sakId, client)
    val tidligereUtførteSideeffekter = hentUtførteSideeffekter(sakId)
    return runBlocking {
        no.nav.su.se.bakover.test.application.defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/tilbakekreving/$tilbakekrevingsbehandlingId/tilAttestering",
            listOf(Brukerrolle.Saksbehandler),
            client = client,
        ) { setBody("""{"versjon":$saksversjon}""") }.apply {
            withClue("Kunne ikke sende tilbakekrevingsbehandling til attestering: ${this.bodyAsText()}") {
                status shouldBe expectedHttpStatusCode
            }
        }.bodyAsText().let { responseJson ->
            if (utførSideeffekter) {
                // Vi kjører konsumentene 2 ganger, for å se at vi ikke oppretter duplikate oppgaver.
                appComponents.kjørAlleTilbakekrevingskonsumenter()
                appComponents.kjørAlleTilbakekrevingskonsumenter()
                appComponents.kjørAlleVerifiseringer(
                    sakId = sakId,
                    tidligereUtførteSideeffekter = tidligereUtførteSideeffekter,
                    antallOppdatertOppgaveHendelser = 1,
                )
                // Vi sletter statusen på jobben, men ikke selve oppgavehendelsen for å verifisere at vi ikke oppretter duplikate oppgaver i disse tilfellene.
                appComponents.slettOppdatertOppgaveKonsumentJobb()
                appComponents.kjørAlleTilbakekrevingskonsumenter()
                appComponents.kjørAlleVerifiseringer(
                    sakId = sakId,
                    tidligereUtførteSideeffekter = tidligereUtførteSideeffekter,
                    antallOppdatertOppgaveHendelser = 1,
                )
            }
            val sakEtterKallJson = hentSak(sakId, client)
            val saksversjonEtter = JSONObject(sakEtterKallJson).getLong("versjon")
            if (verifiserRespons) {
                if (utførSideeffekter) {
                    // oppgavehendelse
                    saksversjonEtter shouldBe saksversjon + 2
                } else {
                    saksversjonEtter shouldBe saksversjon + 1
                }
                sakEtterKallJson.shouldBeSimilarJsonTo(sakFørKallJson, "versjon", "tilbakekrevinger")
                listOf(
                    responseJson,
                    JSONObject(sakEtterKallJson).getJSONArray("tilbakekrevinger").getJSONObject(0).toString(),
                ).forEach {
                    verifiserTilbakekrevingsbehandlingTilAttesteringRespons(
                        actual = it,
                        sakId = sakId,
                        tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                        forhåndsvarselDokumenter = verifiserForhåndsvarselDokumenter,
                        vurderinger = verifiserVurderinger,
                        fritekst = verifiserFritekst,
                        expectedVersjon = saksversjon + 1,
                        expectedAttesteringer = expectedAttesteringer,
                    )
                }
            }
            SendTilbakekrevingsbehandlingTilAttesteringRespons(
                jsonRespons = responseJson,
                saksversjon = saksversjonEtter,
            )
        }
    }
}

internal data class SendTilbakekrevingsbehandlingTilAttesteringRespons(
    val jsonRespons: String,
    val saksversjon: Long,
)

fun verifiserTilbakekrevingsbehandlingTilAttesteringRespons(
    actual: String,
    tilbakekrevingsbehandlingId: String,
    forhåndsvarselDokumenter: String,
    sakId: String,
    vurderinger: String,
    fritekst: String,
    expectedVersjon: Long,
    expectedAttesteringer: String,
) {
    val expected = """
{
  "id":"$tilbakekrevingsbehandlingId",
  "sakId":"$sakId",
  "opprettet":"dette-sjekkes-av-opprettet-verifikasjonen",
  "opprettetAv":"Z990Lokal",
  "kravgrunnlag":{
    "eksternKravgrunnlagsId":"123456",
    "eksternVedtakId":"654321",
    "kontrollfelt":"2021-02-01-02.03.47.456789",
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
  "status":"TIL_ATTESTERING",
  "vurderinger":$vurderinger,
  "fritekst":"$fritekst",
  "forhåndsvarselsInfo": $forhåndsvarselDokumenter,
  "sendtTilAttesteringAv": "Z990Lokal",
  "versjon": $expectedVersjon,
  "attesteringer": $expectedAttesteringer,
  "erKravgrunnlagUtdatert": false,
  "avsluttetTidspunkt": null,
  "notat": "notatet",
}"""
    actual.shouldBeSimilarJsonTo(expected, "kravgrunnlag.hendelseId", "opprettet")
    JSONObject(actual).has("opprettet") shouldBe true
    JSONObject(actual).getJSONObject("kravgrunnlag").has("hendelseId") shouldBe true
}
