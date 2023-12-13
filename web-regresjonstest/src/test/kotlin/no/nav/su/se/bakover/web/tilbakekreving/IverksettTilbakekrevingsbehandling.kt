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
import no.nav.su.se.bakover.test.json.shouldBeSimilarJsonTo
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.komponenttest.AppComponents
import no.nav.su.se.bakover.web.sak.hent.hentSak
import org.json.JSONArray
import org.json.JSONObject

fun AppComponents.iverksettTilbakekrevingsbehandling(
    sakId: String,
    tilbakekrevingsbehandlingId: String,
    expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.Created,
    client: HttpClient,
    verifiserRespons: Boolean = true,
    utførSideeffekter: Boolean = true,
    attestant: NavIdentBruker.Attestant = NavIdentBruker.Attestant("AttestantLokal"),
    saksversjon: Long,
    verifiserForhåndsvarselDokumenter: String,
    verifiserVurderinger: String,
    verifiserFritekst: String,
    tidligereAttesteringer: String? = null,
): Pair<String, Long> {
    val sakFørKallJson = hentSak(sakId, client)
    val appComponents = this
    return runBlocking {
        SharedRegressionTestData.defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/tilbakekreving/$tilbakekrevingsbehandlingId/iverksett",
            listOf(Brukerrolle.Attestant),
            client = client,
            navIdent = attestant.toString(),
        ) { setBody("""{"versjon":$saksversjon}""") }.apply {
            withClue("Kunne ikke iverksette tilbakekrevingsbehandling: ${this.bodyAsText()}") {
                status shouldBe expectedHttpStatusCode
            }
        }.bodyAsText().let {
            if (utførSideeffekter) {
                appComponents.kjøreAlleTilbakekrevingskonsumenter()
                appComponents.kjøreAlleVerifiseringer(
                    sakId = sakId,
                    antallOpprettetOppgaver = 1,
                    antallOppdatertOppgaveHendelser = 4,
                    antallLukketOppgaver = 1,
                    antallGenererteForhåndsvarsler = 1,
                    antallGenererteVedtaksbrev = 1,
                    antallJournalførteDokumenter = 2,
                    antallDistribuertDokumenter = 2,
                )
            }
            val sakEtterKallJson = hentSak(sakId, client)
            sakEtterKallJson.shouldBeSimilarJsonTo(sakFørKallJson, "versjon", "tilbakekrevinger", "vedtak")
            verifiserTilbakekrevingsVedtak(tilbakekrevingsbehandlingId, JSONObject(sakEtterKallJson).getJSONArray("vedtak"))
            val saksversjonEtter = JSONObject(sakEtterKallJson).getLong("versjon")

            if (verifiserRespons) {
                if (utførSideeffekter) {
                    saksversjonEtter shouldBe saksversjon + 5 // hendelse + lukket oppgave + generering av brev + journalført + distribuert
                } else {
                    saksversjonEtter shouldBe saksversjon + 1 // kun hendelsen
                }
                verifiserIverksattTilbakekrevingsbehandlingRespons(
                    actual = it,
                    sakId = sakId,
                    tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                    expectedVersjon = saksversjon + 1,
                    forhåndsvarselDokumenter = verifiserForhåndsvarselDokumenter,
                    vurderinger = verifiserVurderinger,
                    fritekst = verifiserFritekst,
                    tidligereAttesteringer = tidligereAttesteringer,
                )

                val tilbakekreving =
                    JSONObject(sakEtterKallJson).getJSONArray("tilbakekrevinger").getJSONObject(0).toString()
                verifiserIverksattTilbakekrevingsbehandlingRespons(
                    actual = tilbakekreving,
                    sakId = sakId,
                    tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                    expectedVersjon = saksversjon + 1,
                    forhåndsvarselDokumenter = verifiserForhåndsvarselDokumenter,
                    vurderinger = verifiserVurderinger,
                    fritekst = verifiserFritekst,
                    tidligereAttesteringer = tidligereAttesteringer,
                )
            }
            it to saksversjonEtter
        }
    }
}

private fun verifiserTilbakekrevingsVedtak(tilbakekrevingsbehandlingId: String, vedtakJson: JSONArray) {
    vedtakJson.length() shouldBe 3
    val tilbakekrevingsvedtak = vedtakJson.getJSONObject(2)
    val expected = """
                {
                    "id":"ignore-me",
                    "opprettet":"2021-02-01T01:04:11.456789Z",
                    "beregning":null,
                    "simulering":null,
                    "attestant":"AttestantLokal",
                    "saksbehandler":"Z990Lokal",
                    "utbetalingId":null,
                    "behandlingId":$tilbakekrevingsbehandlingId,
                    "periode":null,
                    "type":"TILBAKEKREVING",
                    "dokumenttilstand":"SENDT"
                }
    """.trimIndent()
    tilbakekrevingsvedtak.toString().shouldBeSimilarJsonTo(expected, "id")
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
      "opprettet": "ignore-me"
    }
  ],
  "erKravgrunnlagUtdatert": false,
  "avsluttetTidspunkt": null,
  "notat": "notatet"
}"""
    actual.shouldBeSimilarJsonTo(expected, "kravgrunnlag.hendelseId", "opprettet", "attesteringer[*].opprettet")
}

fun String.removeFirstAndLastCharacter(): String {
    return if (this.length >= 2) {
        this.substring(1, this.length - 1)
    } else {
        // Handle the case where the input string has fewer than 2 characters (e.g., it's empty or has only one character).
        ""
    }
}
