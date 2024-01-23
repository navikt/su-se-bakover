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
import tilbakekreving.domain.underkjennelse.UnderkjennAttesteringsgrunnTilbakekreving

internal fun AppComponents.underkjennTilbakekrevingsbehandling(
    sakId: String,
    tilbakekrevingsbehandlingId: String,
    expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.Created,
    client: HttpClient,
    verifiserRespons: Boolean = true,
    utførSideeffekter: Boolean = true,
    saksversjon: Long,
    brevtekst: String?,
    verifiserForhåndsvarselDokumenter: String,
    verifiserVurderinger: String,
    kommentar: String = "Underkjent av underkjennTilbakekrevingsbehandling() - TilbakekrevingsbehandlingIT",
    grunn: UnderkjennAttesteringsgrunnTilbakekreving = UnderkjennAttesteringsgrunnTilbakekreving.VURDERINGEN_ER_FEIL,
): UnderkjennTilbakekrevingsbehandlingRespons {
    val appComponents = this
    val sakFørKallJson = hentSak(sakId, client)
    val tidligereUtførteSideeffekter = hentUtførteSideeffekter(sakId)
    return runBlocking {
        no.nav.su.se.bakover.test.application.defaultRequest(
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
            if (utførSideeffekter) {
                // oppgavehendelse
                saksversjonEtter shouldBe saksversjon + 2
            } else {
                saksversjonEtter shouldBe saksversjon + 1
            }
            sakEtterKallJson.shouldBeSimilarJsonTo(sakFørKallJson, "versjon", "tilbakekrevinger")
            if (verifiserRespons) {
                listOf(
                    responseJson,
                    JSONObject(sakEtterKallJson).getJSONArray("tilbakekrevinger").getJSONObject(0).toString(),
                ).forEach {
                    verifiserUnderkjentTilbakekrevingsbehandlingRespons(
                        actual = it,
                        sakId = sakId,
                        vurderinger = verifiserVurderinger,
                        tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                        forhåndsvarselDokumenter = verifiserForhåndsvarselDokumenter,
                        expectedVersjon = saksversjon + 1,
                        brevtekst = brevtekst,
                        expectedGrunn = grunn.toString(),
                        expectedKommentar = kommentar,
                    )
                }
            }
            UnderkjennTilbakekrevingsbehandlingRespons(
                underkjentAttestering = hentAttesteringer(responseJson),
                saksversjon = saksversjonEtter,
                responseJson = responseJson,
            )
        }
    }
}

internal data class UnderkjennTilbakekrevingsbehandlingRespons(
    val underkjentAttestering: String,
    val saksversjon: Long,
    val responseJson: String,
)

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
  "opprettet":"dette-sjekkes-av-opprettet-verifikasjonen",
  "opprettetAv":"Z990Lokal",
  "kravgrunnlag":{
    "eksternKravgrunnlagsId":"123456",
    "eksternVedtakId":"654321",
    "kontrollfelt":"2021-02-01-02.03.26.456789",
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
  "status":"VEDTAKSBREV",
  "vurderinger":$vurderinger,
  "fritekst": "$brevtekst",
  "forhåndsvarselsInfo": $forhåndsvarselDokumenter,
  "sendtTilAttesteringAv": null,
  "versjon": $expectedVersjon,
  "attesteringer": [
    {
      "attestant": "Z990Lokal",
      "underkjennelse": {
        "grunn": "$expectedGrunn",
        "kommentar": "$expectedKommentar"
      },
      "opprettet": "ignore-me"
    }
  ],
  "erKravgrunnlagUtdatert": false,
  "avsluttetTidspunkt": null,
  "notat": "notatet"
}"""
    actual.shouldBeSimilarJsonTo(expected, "kravgrunnlag.hendelseId", "opprettet", "attesteringer[*].opprettet")
    JSONObject(actual).has("opprettet") shouldBe true
    JSONObject(actual).getJSONObject("kravgrunnlag").has("hendelseId") shouldBe true
    JSONObject(actual).getJSONArray("attesteringer").all {
        (it as JSONObject).has("opprettet")
    } shouldBe true
}
