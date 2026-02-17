package no.nav.su.se.bakover.web.tilbakekreving

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
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.json.shouldBeSimilarJsonTo
import no.nav.su.se.bakover.web.komponenttest.AppComponents
import no.nav.su.se.bakover.web.sak.hent.hentSak
import org.json.JSONObject
import tilbakekreving.presentation.api.common.ForhåndsvarselMetaInfoJson
import tilbakekreving.presentation.api.common.TilbakekrevingsbehandlingJson
import tilbakekreving.presentation.api.common.TilbakekrevingsbehandlingStatus

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
        no.nav.su.se.bakover.test.application.defaultRequest(
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
            val tilbakekrevingRespons = deserialize<TilbakekrevingsbehandlingJson>(responseJson)

            if (verifiserRespons) {
                if (utførSideeffekter) {
                    saksversjonEtter shouldBe saksversjon + 5 // hendelse + oppdatert oppgave + generering av brev + journalført + distribuert
                } else {
                    saksversjonEtter shouldBe saksversjon + 1 // kun hendelsen
                }
                sakEtterKallJson.shouldBeSimilarJsonTo(sakFørKallJson, "versjon", "tilbakekrevinger")
                listOf(
                    tilbakekrevingRespons,
                    deserialize(JSONObject(sakEtterKallJson).getJSONArray("tilbakekrevinger").getJSONObject(0).toString()),
                ).forEach {
                    it.shouldBeEqualToIgnoringFields(
                        lagOpprettTilbakekrevingRespons(
                            sakId,
                            Tidspunkt.now(fixedClock),
                            saksversjon + 1,
                            TilbakekrevingsbehandlingStatus.FORHÅNDSVARSLET,
                        ),
                        it::id,
                        it::opprettet,
                        it::kravgrunnlag,
                        it::forhåndsvarselsInfo,
                    )

                    it.kravgrunnlag!!.shouldBeEqualToIgnoringFields(
                        lagKravgrunnlagRespons(),
                        it.kravgrunnlag!!::hendelseId,
                        it.kravgrunnlag!!::kontrollfelt,
                    )
                    it.forhåndsvarselsInfo.size shouldBe 1
                }
            }
            ForhåndsvarsletTilbakekrevingRespons(
                forhåndsvarselInfo = tilbakekrevingRespons.forhåndsvarselsInfo,
                saksversjon = saksversjonEtter,
            )
        }
    }
}

internal data class ForhåndsvarsletTilbakekrevingRespons(
    val forhåndsvarselInfo: List<ForhåndsvarselMetaInfoJson>,
    val saksversjon: Long,
)
