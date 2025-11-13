package no.nav.su.se.bakover.web.tilbakekreving

import common.presentation.attestering.AttesteringJson
import dokument.domain.Dokument
import io.kotest.assertions.withClue
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.json.shouldBeSimilarJsonTo
import no.nav.su.se.bakover.test.jsonAssertEquals
import no.nav.su.se.bakover.test.jwt.DEFAULT_IDENT
import no.nav.su.se.bakover.web.SharedRegressionTestData.pdf
import no.nav.su.se.bakover.web.komponenttest.AppComponents
import no.nav.su.se.bakover.web.sak.hent.hentSak
import org.json.JSONArray
import org.json.JSONObject
import tilbakekreving.presentation.api.common.ForhåndsvarselMetaInfoJson
import tilbakekreving.presentation.api.common.TilbakekrevingsbehandlingJson
import tilbakekreving.presentation.api.common.TilbakekrevingsbehandlingStatus
import tilbakekreving.presentation.api.common.VurderingerMedKravJson
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
    verifiserForhåndsvarselDokumenter: List<ForhåndsvarselMetaInfoJson>,
    verifiserVurderinger: VurderingerMedKravJson,
    verifiserFritekst: String,
    tidligereAttesteringer: List<AttesteringJson> = emptyList(),
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
            val vedtakJsonEtter = JSONObject(sakEtterKallJson).getJSONArray("vedtak")
            val vedtakIdEtter = UUID.fromString(vedtakJsonEtter.getJSONObject(2).getString("id"))
            val tilbakekrevingRespons = deserialize<TilbakekrevingsbehandlingJson>(responseJson)
            if (verifiserRespons) {
                if (utførSideeffekter) {
                    // hendelse + lukket oppgave + generering av brev + journalført + distribuert
                    saksversjonEtter shouldBe saksversjon + 5
                    verifiserDokumenterPåSak(sakId, tilbakekrevingsbehandlingId, fnr, vedtakIdEtter)
                } else {
                    // kun hendelsen
                    saksversjonEtter shouldBe saksversjon + 1
                }
                sakEtterKallJson.shouldBeSimilarJsonTo(sakFørKallJson, "versjon", "tilbakekrevinger", "vedtak", "uteståendeKravgrunnlag")
                JSONObject(sakEtterKallJson).isNull("uteståendeKravgrunnlag")
                verifiserTilbakekrevingsVedtak(
                    tilbakekrevingsbehandlingId,
                    vedtakJsonEtter,
                )
                listOf(
                    tilbakekrevingRespons,
                    deserialize(JSONObject(sakEtterKallJson).getJSONArray("tilbakekrevinger").getJSONObject(0).toString()),
                ).forEach {
                    it.shouldBeEqualToIgnoringFields(
                        lagOpprettTilbakekrevingRespons(
                            sakId,
                            Tidspunkt.now(fixedClock),
                            saksversjon + 1,
                            status = TilbakekrevingsbehandlingStatus.IVERKSATT,
                            fritekst = verifiserFritekst,
                            notat = "notatet",
                            sendtTilAttesteringAv = DEFAULT_IDENT,
                        ),
                        it::id,
                        it::opprettet,
                        it::kravgrunnlag,
                        it::forhåndsvarselsInfo,
                        it::vurderinger,
                        it::attesteringer,
                    )
                    it.vurderinger shouldBe verifiserVurderinger
                    it.forhåndsvarselsInfo shouldBe verifiserForhåndsvarselDokumenter
                    it.kravgrunnlag!!.shouldBeEqualToIgnoringFields(
                        lagKravgrunnlagRespons(),
                        it.kravgrunnlag!!::hendelseId,
                        it.kravgrunnlag!!::kontrollfelt,
                    )
                    it.attesteringer.size shouldBe 2
                    it.attesteringer.first() shouldBe tidligereAttesteringer.single()
                    it.attesteringer.last().let {
                        it.shouldBeEqualToIgnoringFields(
                            AttesteringJson(
                                attestant = "AttestantLokal",
                                underkjennelse = null,
                                Tidspunkt.now(fixedClock),
                            ),
                            it::opprettet,
                        )
                    }
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
                    "saksbehandler":"$DEFAULT_IDENT",
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
    vedtakId: UUID?,
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
                          "skalTilbakekreve": false,
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
                          "bruttoSkalIkkeTilbakekreveSummert": 2383
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
                    vedtakId = vedtakId,
                    revurderingId = null,
                    klageId = null,
                    tilbakekrevingsbehandlingId = UUID.fromString(tilbakekrevingsbehandlingId),
                    journalpostId = it.metadata.journalpostId!!,
                    brevbestillingId = it.metadata.brevbestillingId!!,
                ),
                distribueringsadresse = null,
            )
        }
}
