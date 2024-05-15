package no.nav.su.se.bakover.web.tilbakekreving

import dokument.domain.Dokument
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.test.json.shouldBeSimilarJsonTo
import no.nav.su.se.bakover.test.jsonAssertEquals
import no.nav.su.se.bakover.web.SharedRegressionTestData.pdf
import no.nav.su.se.bakover.web.komponenttest.AppComponents
import no.nav.su.se.bakover.web.sak.hent.hentSak
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal fun AppComponents.iverksettTilbakekrevingsbehandling(
    sakId: String,
    tilbakekrevingsbehandlingId: String,
    fnr: String,
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
): IverksettTilbakekrevingsbehandlingRespons {
    val appComponents = this
    val sakFørKallJson = hentSak(sakId, client)
    val tidligereUtførteSideeffekter = hentUtførteSideeffekter(sakId)
    return runBlocking {
        no.nav.su.se.bakover.test.application.defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/tilbakekreving/$tilbakekrevingsbehandlingId/iverksett",
            listOf(Brukerrolle.Attestant),
            client = client,
            navIdent = attestant.toString(),
        ) { setBody("""{"versjon":$saksversjon}""") }.apply {
            withClue("Kunne ikke iverksette tilbakekrevingsbehandling: ${this.bodyAsText()}") {
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
                    antallLukketOppgaver = 1,
                    antallGenererteVedtaksbrev = 1,
                    antallJournalførteDokumenter = 1,
                    antallDistribuertDokumenter = 1,
                )
                // Vi sletter statusen på jobben, men ikke selve oppgavehendelsen for å verifisere at vi ikke oppretter duplikate oppgaver i disse tilfellene.
                appComponents.slettLukketOppgaveKonsumentJobb()
                appComponents.slettGenererDokumentForForhåndsvarselKonsumentJobb()
                // Ikke denne testen sitt ansvar og verifisere journalføring og distribusjon av dokumenter, så vi sletter ikke de.
                appComponents.kjørAlleTilbakekrevingskonsumenter()
                appComponents.kjørAlleVerifiseringer(
                    sakId = sakId,
                    tidligereUtførteSideeffekter = tidligereUtførteSideeffekter,
                    antallLukketOppgaver = 1,
                    antallGenererteVedtaksbrev = 1,
                    antallJournalførteDokumenter = 1,
                    antallDistribuertDokumenter = 1,
                )
            }
            val sakEtterKallJson = hentSak(sakId, client)
            val saksversjonEtter = JSONObject(sakEtterKallJson).getLong("versjon")
            if (verifiserRespons) {
                if (utførSideeffekter) {
                    // hendelse + lukket oppgave + generering av brev + journalført + distribuert
                    saksversjonEtter shouldBe saksversjon + 5
                    verifiserDokumenterPåSak(sakId, tilbakekrevingsbehandlingId, fnr)
                } else {
                    // kun hendelsen
                    saksversjonEtter shouldBe saksversjon + 1
                }
                sakEtterKallJson.shouldBeSimilarJsonTo(sakFørKallJson, "versjon", "tilbakekrevinger", "vedtak", "uteståendeKravgrunnlag")
                JSONObject(sakEtterKallJson).isNull("uteståendeKravgrunnlag")
                verifiserTilbakekrevingsVedtak(
                    tilbakekrevingsbehandlingId,
                    JSONObject(sakEtterKallJson).getJSONArray("vedtak"),
                )
                listOf(
                    responseJson,
                    JSONObject(sakEtterKallJson).getJSONArray("tilbakekrevinger").getJSONObject(0).toString(),
                ).forEach {
                    verifiserIverksattTilbakekrevingsbehandlingRespons(
                        actual = it,
                        sakId = sakId,
                        tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                        forhåndsvarselDokumenter = verifiserForhåndsvarselDokumenter,
                        vurderinger = verifiserVurderinger,
                        fritekst = verifiserFritekst,
                        expectedVersjon = saksversjon + 1,
                        tidligereAttesteringer = tidligereAttesteringer,
                    )
                }
            }
            IverksettTilbakekrevingsbehandlingRespons(
                saksversjon = saksversjonEtter,
                responseJson = responseJson,
            )
        }
    }
}

internal data class IverksettTilbakekrevingsbehandlingRespons(
    val responseJson: String,
    val saksversjon: Long,
)

private fun verifiserTilbakekrevingsVedtak(tilbakekrevingsbehandlingId: String, vedtakJson: JSONArray) {
    vedtakJson.length() shouldBe 3
    val tilbakekrevingsvedtak = vedtakJson.getJSONObject(2)
    val expected = """
                {
                    "id":"ignore-me",
                    "opprettet":"ignore-me",
                    "beregning":null,
                    "simulering":null,
                    "attestant":"AttestantLokal",
                    "saksbehandler":"Z990Lokal",
                    "utbetalingId":null,
                    "behandlingId":$tilbakekrevingsbehandlingId,
                    "periode":null,
                    "type":"TILBAKEKREVING",
                    "dokumenttilstand":"SENDT",
                    "kanStarteNyBehandling": false,
                    "skalSendeBrev": true
                }
    """.trimIndent()
    tilbakekrevingsvedtak.toString().shouldBeSimilarJsonTo(expected, "id", "opprettet")
    tilbakekrevingsvedtak.has("id") shouldBe true
    tilbakekrevingsvedtak.has("opprettet") shouldBe true
}

private fun AppComponents.verifiserDokumenterPåSak(
    sakId: String,
    tilbakekrevingsbehandlingId: String,
    fnr: String,
) {
    val sakIdSomUUID = UUID.fromString(sakId)
    this.databaseRepos.dokumentRepo.hentForSak(sakIdSomUUID).filterIsInstance<Dokument.MedMetadata.Vedtak>()
        .single { it.tittel == "Tilbakekreving av Supplerende stønad" }.let {
            jsonAssertEquals(
                expected = """
                        {
                          "personalia":{
                            "dato":"01.02.2021",
                            "fødselsnummer":"$fnr",
                            "fornavn":"Tore",
                            "etternavn":"Strømøy",
                            "saksnummer":2021
                          },
                          "saksbehandlerNavn":"Testbruker, Lokal",
                          "attestantNavn":"Testbruker, Lokal",
                          "fritekst":"Regresjonstest: Fritekst til vedtaksbrev under tilbakekrevingsbehandling.",
                          "dato":"1. februar 2021",
                          "månedsoversiktMedSum":{
                            "sorterteMåneder":[
                              {
                                "periode":"01.01.2021 - 31.01.2021",
                                "vurdering":"SkalIkkeTilbakekreve",
                                "bruttoSkalTilbakekreve":0,
                                "nettoSkalTilbakekreve":0
                              }
                            ],
                            "sumBruttoSkalTilbakekreve":0,
                            "sumNettoSkalTilbakekreve":0
                          },
                          "sakstype":"UFØRE",
                          "erAldersbrev":false
                        }
                """.trimIndent(),
                actual = it.generertDokumentJson,
            )
            it shouldBe Dokument.MedMetadata.Vedtak(
                utenMetadata = Dokument.UtenMetadata.Vedtak(
                    // Denne blir generert når vi lager IverksattHendelse
                    id = it.id,
                    opprettet = it.opprettet,
                    tittel = "Tilbakekreving av Supplerende stønad",
                    generertDokument = PdfA(pdf.toByteArray()),
                    // Verifiserer denne for seg selv
                    generertDokumentJson = it.generertDokumentJson,
                ),
                metadata = Dokument.Metadata(
                    sakId = sakIdSomUUID,
                    søknadId = null,
                    vedtakId = null,
                    revurderingId = null,
                    klageId = null,
                    tilbakekrevingsbehandlingId = UUID.fromString(tilbakekrevingsbehandlingId),
                    journalpostId = it.metadata.journalpostId!!,
                    brevbestillingId = it.metadata.brevbestillingId!!,
                ),
            )
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
  "opprettet":"dette-sjekkes-av-opprettet-verifikasjonen",
  "opprettetAv":"Z990Lokal",
  "kravgrunnlag":{
    "eksternKravgrunnlagsId":"123456",
    "eksternVedtakId":"654321",
    "kontrollfelt":"2021-02-01-02.03.42.456789",
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
    JSONObject(actual).has("opprettet") shouldBe true
    JSONObject(actual).getJSONObject("kravgrunnlag").has("hendelseId") shouldBe true
    JSONObject(actual).getJSONArray("attesteringer").all {
        (it as JSONObject).has("opprettet")
    } shouldBe true
}

fun String.removeFirstAndLastCharacter(): String {
    return if (this.length >= 2) {
        this.substring(1, this.length - 1)
    } else {
        // Handle the case where the input string has fewer than 2 characters (e.g., it's empty or has only one character).
        ""
    }
}
