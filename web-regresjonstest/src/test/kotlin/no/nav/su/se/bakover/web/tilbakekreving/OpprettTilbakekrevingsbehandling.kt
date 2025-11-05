package no.nav.su.se.bakover.web.tilbakekreving

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.test.json.shouldBeSimilarJsonTo
import no.nav.su.se.bakover.test.jwt.DEFAULT_IDENT
import no.nav.su.se.bakover.web.komponenttest.AppComponents
import no.nav.su.se.bakover.web.sak.hent.hentSak
import org.json.JSONObject

/**
 * Oppretter en tilbakekrevingsbehandling for en gitt sak.
 * Kjører også konsumenten som lytter på disse hendelsene for å opprette en oppgave.
 */
internal fun AppComponents.opprettTilbakekrevingsbehandling(
    sakId: String,
    expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.Created,
    client: HttpClient,
    verifiserRespons: Boolean = true,
    utførSideeffekter: Boolean = true,
    saksversjon: Long,
): OpprettetTilbakekrevingsbehandlingRespons {
    val appComponents = this
    val sakFørKallJson = hentSak(sakId, client)
    val tidligereUtførteSideeffekter = hentUtførteSideeffekter(sakId)
    return runBlocking {
        val correlationId = CorrelationId.generate()
        no.nav.su.se.bakover.test.application.defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/tilbakekreving/ny",
            listOf(Brukerrolle.Saksbehandler),
            client = client,
            correlationId = correlationId.toString(),
        ) { setBody("""{"versjon":$saksversjon}""") }.apply {
            withClue("opprettTilbakekrevingsbehandling feilet: ${this.bodyAsText()}") {
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
                    antallOpprettetOppgaver = 1,
                )
                // Vi sletter statusen på jobben, men ikke selve oppgavehendelsen for å verifisere at vi ikke oppretter duplikate oppgaver i disse tilfellene.
                appComponents.slettOpprettetOppgaveKonsumentJobb()
                appComponents.kjørAlleTilbakekrevingskonsumenter()
                appComponents.kjørAlleVerifiseringer(
                    sakId = sakId,
                    tidligereUtførteSideeffekter = tidligereUtførteSideeffekter,
                    antallOpprettetOppgaver = 1,
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
                    verifiserOpprettetTilbakekrevingsbehandlingRespons(
                        actual = it,
                        sakId = sakId,
                        expectedVersjon = saksversjon + 1,
                    )
                }
            }
            OpprettetTilbakekrevingsbehandlingRespons(
                tilbakekrevingsbehandlingId = hentTilbakekrevingsbehandlingId(responseJson),
                saksversjon = saksversjonEtter,
                responseJson = responseJson,
            )
        }
    }
}

internal data class OpprettetTilbakekrevingsbehandlingRespons(
    val tilbakekrevingsbehandlingId: String,
    val saksversjon: Long,
    val responseJson: String,
)

fun verifiserOpprettetTilbakekrevingsbehandlingRespons(
    actual: String,
    sakId: String,
    expectedVersjon: Long,
) {
    //language=json
    val expected = """
{
  "id":"ignoreres-siden-denne-opprettes-av-tjenesten",
  "sakId":"$sakId",
  "opprettet":"dette-sjekkes-av-opprettet-verifikasjonen",
  "opprettetAv":"$DEFAULT_IDENT",
  "kravgrunnlag":{
    "eksternKravgrunnlagsId":"123456",
    "eksternVedtakId":"654321",
    "kontrollfelt":"2021-02-01-02.03.48.456789",
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
  "status":"OPPRETTET",
  "vurderinger":null,
  "fritekst":null,
  "forhåndsvarselsInfo": [],
  "sendtTilAttesteringAv": null,
  "versjon": $expectedVersjon,
  "attesteringer": [],
  "erKravgrunnlagUtdatert": false,
  "avsluttetTidspunkt": null,
  "notat": null
}"""
    actual.shouldBeSimilarJsonTo(expected, "id", "kravgrunnlag.hendelseId", "kravgrunnlag.kontrollfelt", "opprettet")
    JSONObject(actual).has("id") shouldBe true
    JSONObject(actual).has("opprettet") shouldBe true
    JSONObject(actual).getJSONObject("kravgrunnlag").has("hendelseId") shouldBe true
}
