package no.nav.su.se.bakover.web.tilbakekreving

import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.kravgrunnlag.emulerViMottarKravgrunnlagDetaljer
import no.nav.su.se.bakover.web.revurdering.fradrag.leggTilFradrag
import no.nav.su.se.bakover.web.revurdering.opprettIverksattRevurdering
import no.nav.su.se.bakover.web.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.RevurderingJson
import no.nav.su.se.bakover.web.søknadsbehandling.opprettInnvilgetSøknadsbehandling
import org.json.JSONArray
import org.junit.jupiter.api.Test

internal class TilbakekrevingsbehandlingIT {

    @Test
    fun `kjører gjennom en tilbakekrevingsbehandling til iverksetting, med underkjenning`() {
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
            appComponents.emulerViMottarKravgrunnlagDetaljer()
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
                antallDistribuerteDokumenter = 0,
            )
            appComponents.verifiserGenererDokumentForForhåndsvarselKonsument()
            val versjonEtterJournalføringAvForhåndsvarsel =
                appComponents.journalførDokumenter(versjonEtterGenereringAvForhåndsvarselsDokument)
            appComponents.verifiserJournalførDokumenterKonsument(1)

            val versjonEtterDistribusjonAvForhåndsvarsel =
                appComponents.distribuerDokumenter(versjonEtterJournalføringAvForhåndsvarsel)
            appComponents.verifiserDistribuerteDokumenterKonsument(1)

            val (vurderinger, versjonEtterVurdering) = vurderTilbakekrevingsbehandling(
                sakId = sakId,
                tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                saksversjon = versjonEtterDistribusjonAvForhåndsvarsel,
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
                dokumentId = JSONArray(forhåndsvarselDokumenter).getJSONObject(0).getString("id"),
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
            appComponents.runAllConsumers(versjonEtterLukking)
            appComponents.runAllVerifiseringer(
                sakId = sakId,
                antallOpprettetOppgaver = 1,
                antallOppdatertOppgaveHendelser = 4,
                antallLukketOppgaver = 1,
                antallGenererteForhåndsvarsler = 1,
                antallJournalførteDokumenter = 1,
                antallDistribuertDokumenter = 1,
            )
        }
    }

    @Test
    fun `oppdaterer kravgrunnlag på en tilbakekreving, også avslutter behandling`() {
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

            opprettIverksattRevurdering(
                sakid = sakId,
                fraogmed = 1.januar(2021).toString(),
                tilogmed = 31.januar(2021).toString(),
                client = this.client,
                appComponents = appComponents,
                skalUtsetteTilbakekreving = true,
            )
            appComponents.emulerViMottarKravgrunnlagDetaljer()
            verifiserKravgrunnlagPåSak(sakId, client, true, 2)
            val (tilbakekrevingsbehandlingId, saksversjonEtterOpprettelseAvBehandling) = opprettTilbakekrevingsbehandling(
                sakId = sakId,
                saksversjon = 2,
                client = this.client,
            ).let {
                hentTilbakekrevingsbehandlingId(it.first) to it.second
            }
            val versjonEtterOpprettelseAvOppgave = appComponents.opprettOppgave(saksversjonEtterOpprettelseAvBehandling)
            appComponents.verifiserOpprettetOppgaveKonsument()

            opprettIverksattRevurdering(
                sakid = sakId,
                fraogmed = 1.januar(2021).toString(),
                tilogmed = 31.januar(2021).toString(),
                client = this.client,
                appComponents = appComponents,
                skalUtsetteTilbakekreving = true,
                leggTilFradrag = { fradragSakId, behandlingId, fraOgMed, tilOgMed ->
                    leggTilFradrag(
                        sakId = fradragSakId,
                        behandlingId = behandlingId,
                        fraOgMed = fraOgMed,
                        tilOgMed = tilOgMed,
                        body = { """{"fradrag": [{"periode": {"fraOgMed": "$fraOgMed", "tilOgMed": "$tilOgMed"}, "type": "PrivatPensjon", "beløp": 35000.0, "utenlandskInntekt": null, "tilhører": "EPS"}]}""" },
                        client = this.client,
                    )
                },
            )
            appComponents.emulerViMottarKravgrunnlagDetaljer()
            val versjonEtterNyttKravgrunnlag = versjonEtterOpprettelseAvOppgave + 1
            verifiserKravgrunnlagPåSak(sakId, client, true, versjonEtterNyttKravgrunnlag.toInt())
            val (_, versjonEtterOppdateringAvKravgrunnlag) = oppdaterKravgrunnlag(
                sakId = sakId,
                tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                saksversjon = versjonEtterNyttKravgrunnlag,
                client = this.client,
            )

            val (_, versjonEtterAvbrytelse) = avbrytTilbakekrevingsbehandling(
                sakId = sakId,
                tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                client = this.client,
                saksversjon = versjonEtterOppdateringAvKravgrunnlag,
            )

            val versjonEtterLukking = appComponents.lukkOppgave(versjonEtterAvbrytelse)
            appComponents.verifiserLukketOppgaveKonsument()

            // kjører konsumenter en gang til på slutten for å verifisere at dette ikke vil føre til flere hendelser
            appComponents.runAllConsumers(versjonEtterLukking)
            appComponents.runAllVerifiseringer(
                sakId = sakId,
                antallOpprettetOppgaver = 1,
                antallOppdatertOppgaveHendelser = 0,
                antallLukketOppgaver = 1,
                antallGenererteForhåndsvarsler = 0,
                antallJournalførteDokumenter = 0,
                antallDistribuertDokumenter = 0,
            )
        }
    }
}
