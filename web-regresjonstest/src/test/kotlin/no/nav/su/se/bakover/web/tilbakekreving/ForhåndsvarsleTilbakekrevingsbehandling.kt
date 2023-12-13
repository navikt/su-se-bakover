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
import no.nav.su.se.bakover.test.json.shouldBeSimilarJsonTo
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.komponenttest.AppComponents
import no.nav.su.se.bakover.web.sak.hent.hentSak
import org.json.JSONObject

internal fun AppComponents.forhåndsvarsleTilbakekrevingsbehandling(
    sakId: String,
    tilbakekrevingsbehandlingId: String,
    expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.Created,
    client: HttpClient,
    verifiserRespons: Boolean = true,
    utførSideeffekter: Boolean = true,
    saksversjon: Long,
    fritekst: String = "Regresjonstest: Fritekst til forhåndsvarsel under tilbakekrevingsbehandling.",
): ForhåndsvarsletTilbakekrevingRespons {
    val sakFørKallJson = hentSak(sakId, client)
    val tidligereUtførteSideeffekter = hentUtførteSideeffekter(sakId)
    val appComponents = this
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
        }.bodyAsText().let { responseJson ->
            if (utførSideeffekter) {
                // Vi kjører konsumentene 2 ganger, for å se at vi ikke oppretter duplikate oppgaver.
                appComponents.kjørAlleTilbakekrevingskonsumenter()
                appComponents.kjørAlleTilbakekrevingskonsumenter()
                appComponents.kjørAlleVerifiseringer(
                    sakId = sakId,
                    tidligereUtførteSideeffekter = tidligereUtførteSideeffekter,
                    antallOppdatertOppgaveHendelser = 1,
                    antallGenererteForhåndsvarsler = 1,
                    antallJournalførteDokumenter = 1,
                    antallDistribuertDokumenter = 1,
                )
                // Vi sletter statusen på jobben, men ikke selve oppgavehendelsen for å verifisere at vi ikke oppretter duplikate oppgaver i disse tilfellene.
                appComponents.slettOppdatertOppgaveKonsumentJobb()
                appComponents.slettGenererDokumentForForhåndsvarselKonsumentJobb()
                // Ikke denne testen sitt ansvar og verifisere journalføring og distribusjon av dokumenter, så vi sletter ikke de.
                appComponents.kjørAlleTilbakekrevingskonsumenter()
                appComponents.kjørAlleVerifiseringer(
                    sakId = sakId,
                    tidligereUtførteSideeffekter = tidligereUtførteSideeffekter,
                    antallOppdatertOppgaveHendelser = 1,
                    antallGenererteForhåndsvarsler = 1,
                    antallJournalførteDokumenter = 1,
                    antallDistribuertDokumenter = 1,
                )
            }
            val sakEtterKallJson = hentSak(sakId, client)
            val saksversjonEtter = JSONObject(sakEtterKallJson).getLong("versjon")

            if (verifiserRespons) {
                if (utførSideeffekter) {
                    saksversjonEtter shouldBe saksversjon + 5 // hendelse + oppdatert oppgave + generering av brev + journalført + distribuert
                } else {
                    saksversjonEtter shouldBe saksversjon + 1 // kun hendelsen
                }
                sakEtterKallJson.shouldBeSimilarJsonTo(sakFørKallJson, "versjon", "tilbakekrevinger")
                listOf(
                    responseJson,
                    JSONObject(sakEtterKallJson).getJSONArray("tilbakekrevinger").getJSONObject(0).toString(),
                ).forEach {
                    verifiserForhåndsvarsletTilbakekrevingsbehandlingRespons(
                        actual = it,
                        sakId = sakId,
                        tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                        expectedVersjon = saksversjon + 1,
                    )
                }
            }
            ForhåndsvarsletTilbakekrevingRespons(
                forhåndsvarselInfo = hentForhåndsvarselDokumenter(responseJson),
                saksversjon = saksversjonEtter,
                responseJson = responseJson,
            )
        }
    }
}

internal data class ForhåndsvarsletTilbakekrevingRespons(
    val forhåndsvarselInfo: String,
    val saksversjon: Long,
    val responseJson: String,
)

fun verifiserForhåndsvarsletTilbakekrevingsbehandlingRespons(
    actual: String,
    sakId: String,
    tilbakekrevingsbehandlingId: String,
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
        "skattFeilutbetaling":"6192",
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
  "status":"FORHÅNDSVARSLET",
  "vurderinger":null,
  "fritekst":null,
  "forhåndsvarselsInfo": [
      {
        "id": "ignoreres-siden-denne-opprettes-av-tjenesten",
        "hendelsestidspunkt": "2021-02-01T01:03:38.456789Z"
      }
   ],
  "sendtTilAttesteringAv": null,
  "versjon": $expectedVersjon,
  "attesteringer": [],
  "erKravgrunnlagUtdatert": false,
  "avsluttetTidspunkt": null,
  "notat": null,
}"""
    actual.shouldBeSimilarJsonTo(expected, "forhåndsvarselsInfo", "kravgrunnlag.hendelseId", "opprettet")
    JSONObject(actual).getJSONArray("forhåndsvarselsInfo").shouldHaveSize(1)
    JSONObject(actual).has("opprettet") shouldBe true
    JSONObject(actual).getJSONObject("kravgrunnlag").has("hendelseId") shouldBe true
}
