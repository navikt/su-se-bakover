package no.nav.su.se.bakover.web.tilbakekreving

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.kravgrunnlag.emulerViMottarKravgrunnlag
import no.nav.su.se.bakover.web.revurdering.opprettIverksattRevurdering
import no.nav.su.se.bakover.web.sak.hent.hentSak
import no.nav.su.se.bakover.web.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.RevurderingJson
import no.nav.su.se.bakover.web.søknadsbehandling.opprettInnvilgetSøknadsbehandling
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Test

internal class TilbakekrevingsbehandlingIT {

    @Test
    fun `kan opprette tilbakekrevingsbehandling`() {
        val clock = TikkendeKlokke(fixedClockAt(1.februar(2021)))
        SharedRegressionTestData.withTestApplicationAndEmbeddedDb(
            clock = clock,
        ) { appComponents ->
            val stønadStart = 1.januar(2021)
            val stønadSlutt = 31.januar(2021)
            val fnr = Fnr.generer().toString()

            val søknadsbehandlingJson = opprettInnvilgetSøknadsbehandling(
                fnr = fnr,
                fraOgMed = stønadStart.toString(),
                tilOgMed = stønadSlutt.toString(),
                client = this.client,
                appComponents = appComponents,
            )

            val sakId = BehandlingJson.hentSakId(søknadsbehandlingJson)

            @Suppress("UNUSED_VARIABLE")
            val revurderingId = opprettIverksattRevurdering(
                sakid = sakId,
                fraogmed = 1.januar(2021).toString(),
                tilogmed = 31.januar(2021).toString(),
                client = this.client,
                appComponents = appComponents,
                skalUtsetteTilbakekreving = true,
            ).let {
                RevurderingJson.hentRevurderingId(it)
            }
            appComponents.emulerViMottarKravgrunnlag()
            verifiserKravgrunnlagPåSak(sakId, client, true, 2)
            val (tilbakekrevingsbehandlingId, saksversjonEtterOpprettelseAvBehandling) = opprettTilbakekrevingsbehandling(
                sakId = sakId,
                // Må økes etter hvert som vi får flere hendelser.
                saksversjon = 2,
                client = this.client,
            ).let {
                hentTilbakekrevingsbehandlingId(it.first) to it.second
            }
            val versjonEtterOpprettelseAvOppgave = appComponents.opprettOppgave(saksversjonEtterOpprettelseAvBehandling)
            appComponents.verifiserOpprettetOppgaveKonsument()
            forhåndsvisForhåndsvarselTilbakekreving(
                sakId = sakId,
                tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                saksversjon = versjonEtterOpprettelseAvOppgave,
                client = this.client,
            )
            val (forhåndsvarselDokumenter, versjonEtterForhåndsvarsel) = forhåndsvarsleTilbakekrevingsbehandling(
                sakId = sakId,
                tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                saksversjon = versjonEtterOpprettelseAvOppgave,
                client = this.client,
            ).let {
                hentForhåndsvarselDokumenter(it.first) to it.second
            }
            val versjonEtterOppdateringAvForhåndsvarselsOppgave =
                appComponents.oppdaterOppgave(versjonEtterForhåndsvarsel)
            appComponents.verifiserOppdatertOppgaveKonsument(1)
            val versjonEtterGenereringAvForhåndsvarselsDokument =
                appComponents.genererDokumenterForForhåndsvarsel(versjonEtterOppdateringAvForhåndsvarselsOppgave)
            appComponents.verifiserDokumentHendelser(
                sakId = sakId,
                antallGenererteDokumenter = 1,
                antallJournalførteDokumenter = 0,
            )
            appComponents.verifiserGenererDokumentForForhåndsvarselKonsument()
            val versjonEtterJournalføringAvForhåndsvarsel =
                appComponents.journalførDokmenter(versjonEtterGenereringAvForhåndsvarselsDokument)
            appComponents.verifiserJournalførDokumenterKonsument(1)

            // Saksversjon 6 vil være en synkron oppgave (TODO: skal bli asynkront)
            // Saksversjon 7 vil være et synkront dokument (TODO: skal bli asynkront)
            val (vurderinger, versjonEtterVurdering) = vurderTilbakekrevingsbehandling(
                sakId = sakId,
                tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                saksversjon = versjonEtterJournalføringAvForhåndsvarsel,
                client = this.client,
                verifiserForhåndsvarselDokumenter = forhåndsvarselDokumenter,
            ).let {
                hentVurderinger(it.first) to it.second
            }

            val (fritekst, versjonEtterOppdateringAvVedtaksbrev) = oppdaterVedtaksbrevTilbakekrevingsbehandling(
                sakId = sakId,
                tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                saksversjon = versjonEtterVurdering,
                client = this.client,
                verifiserForhåndsvarselDokumenter = forhåndsvarselDokumenter,
                verifiserVurderinger = vurderinger,
            ).let {
                hentFritekst(it.first) to it.second
            }
            forhåndsvisVedtaksbrevTilbakekrevingsbehandling(
                sakId = sakId,
                tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                client = this.client,
            )

            val (_, versjonEtterFørsteSendingTilAttestering) = sendTilbakekrevingsbehandlingTilAttestering(
                sakId = sakId,
                tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                saksversjon = versjonEtterOppdateringAvVedtaksbrev,
                client = this.client,
                verifiserForhåndsvarselDokumenter = forhåndsvarselDokumenter,
                verifiserVurderinger = vurderinger,
                verifiserFritekst = fritekst,
            )
            val versjonEtterOppdateringAvOppgaveFørsteAttestering =
                appComponents.oppdaterOppgave(versjonEtterFørsteSendingTilAttestering)
            appComponents.verifiserOppdatertOppgaveKonsument(2)

            visUtsendtForhåndsvarselDokument(
                sakId = sakId,
                tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                dokumentId = JSONArray(forhåndsvarselDokumenter).getString(0),
                client = this.client,
            )

            val (underkjentAttestering, versjonEtterUnderkjenning) = underkjennTilbakekrevingsbehandling(
                sakId = sakId,
                tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                saksversjon = versjonEtterOppdateringAvOppgaveFørsteAttestering,
                client = this.client,
                verifiserForhåndsvarselDokumenter = forhåndsvarselDokumenter,
                verifiserVurderinger = vurderinger,
                brevtekst = fritekst,
            ).let {
                hentAttesteringer(it.first) to it.second
            }
            val versjonEtterOppdateringAvOppgaveUnderkjenning = appComponents.oppdaterOppgave(versjonEtterUnderkjenning)
            appComponents.verifiserOppdatertOppgaveKonsument(3)

            val (vurderingerEtterUnderkjenning, versjonEtterVurderingEtterUnderkjenning) = vurderTilbakekrevingsbehandling(
                sakId = sakId,
                tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                saksversjon = versjonEtterOppdateringAvOppgaveUnderkjenning,
                client = this.client,
                verifiserForhåndsvarselDokumenter = forhåndsvarselDokumenter,
                vurderinger = """
                    [
                        {
                            "måned": "2021-01",
                            "vurdering": "SkalIkkeTilbakekreve"
                        }
                    ]
                """.trimIndent(),
                tilstand = "VEDTAKSBREV",
                expectedFritekst = "Regresjonstest: Fritekst til vedtaksbrev under tilbakekrevingsbehandling.",
                expectedAttesteringer = underkjentAttestering,
            ).let {
                hentVurderinger(it.first) to it.second
            }
            val (_, versjonEtterAndreSendingTilAttestering) = sendTilbakekrevingsbehandlingTilAttestering(
                sakId = sakId,
                tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                saksversjon = versjonEtterVurderingEtterUnderkjenning,
                client = this.client,
                verifiserForhåndsvarselDokumenter = forhåndsvarselDokumenter,
                verifiserVurderinger = vurderingerEtterUnderkjenning,
                verifiserFritekst = fritekst,
                expectedAttesteringer = underkjentAttestering,
            )
            val versjonEtterOppdateringAvOppgaveAndreAttestering =
                appComponents.oppdaterOppgave(versjonEtterAndreSendingTilAttestering)
            appComponents.verifiserOppdatertOppgaveKonsument(4)
            val (_, versjonEtterIverksetting) = iverksettTilbakekrevingsbehandling(
                sakId = sakId,
                tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                saksversjon = versjonEtterOppdateringAvOppgaveAndreAttestering,
                client = this.client,
                verifiserForhåndsvarselDokumenter = forhåndsvarselDokumenter,
                verifiserVurderinger = vurderingerEtterUnderkjenning,
                verifiserFritekst = fritekst,
                tidligereAttesteringer = underkjentAttestering,
            )
            val versjonEtterLukking = appComponents.lukkOppgave(versjonEtterIverksetting)
            appComponents.verifiserLukketOppgaveKonsument()
            // TODO jah: sende tilbakekrevingsvedtaket til oppdrag + sende brev hvis det er valgt.
            verifiserKravgrunnlagPåSak(sakId, client, true, versjonEtterLukking.toInt())

            // kjører konsumenter en gang til på slutten for å verifisere at dette ikke vil føre til flere hendelser
            appComponents.opprettOppgave(versjonEtterLukking)
            appComponents.oppdaterOppgave(versjonEtterLukking)
            appComponents.lukkOppgave(versjonEtterLukking)
            appComponents.genererDokumenterForForhåndsvarsel(versjonEtterLukking)
            appComponents.journalførDokmenter(versjonEtterLukking)

            appComponents.verifiserOpprettetOppgaveKonsument()
            appComponents.verifiserOppdatertOppgaveKonsument(4)
            appComponents.verifiserLukketOppgaveKonsument()
            appComponents.verifiserGenererDokumentForForhåndsvarselKonsument()
            appComponents.verifiserJournalførDokumenterKonsument(1)

            appComponents.verifiserOppgaveHendelser(
                sakId = sakId,
                antallOppdaterteOppgaver = 4,
                antallLukketOppgaver = 1,
            )
            appComponents.verifiserDokumentHendelser(
                sakId = sakId,
                antallGenererteDokumenter = 1,
                antallJournalførteDokumenter = 1,
            )
        }
    }
}

private fun verifiserKravgrunnlagPåSak(
    sakId: String,
    client: HttpClient,
    forventerKravgrunnlag: Boolean,
    versjon: Int,
) {
    hentSak(sakId, client = client).also { sakJson ->
        // Kravgrunnlaget vil være utestående så lenge vi ikke har iverksatt tilbakekrevingsbehandlingen.
        JSONObject(sakJson).isNull("uteståendeKravgrunnlag") shouldBe !forventerKravgrunnlag
        JSONObject(sakJson).getInt("versjon") shouldBe versjon
    }
}
