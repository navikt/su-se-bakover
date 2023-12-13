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
            val (tilbakekrevingsbehandlingId, saksversjonEtterOpprettelseAvBehandling) = appComponents.opprettTilbakekrevingsbehandling(
                sakId = sakId,
                // Må økes etter hvert som vi får flere hendelser.
                saksversjon = 2,
                client = this.client,
            )
            forhåndsvisForhåndsvarselTilbakekreving(
                sakId = sakId,
                tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                saksversjon = saksversjonEtterOpprettelseAvBehandling,
                client = this.client,
            )
            val (forhåndsvarselDokumenter, versjonEtterForhåndsvarsel) = appComponents.forhåndsvarsleTilbakekrevingsbehandling(
                sakId = sakId,
                tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                saksversjon = saksversjonEtterOpprettelseAvBehandling,
                client = this.client,
            )
            val (vurderinger, versjonEtterVurdering) = vurderTilbakekrevingsbehandling(
                sakId = sakId,
                tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                saksversjon = versjonEtterForhåndsvarsel,
                client = this.client,
                verifiserForhåndsvarselDokumenter = forhåndsvarselDokumenter,
            )

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

            val (notat, versjonEtterNotat) = leggTilNotatTilbakekrevingsbehandling(
                sakId = sakId,
                tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                client = this.client,
                saksversjon = versjonEtterOppdateringAvVedtaksbrev,
                verifiserForhåndsvarselDokumenter = forhåndsvarselDokumenter,
                verifiserVurderinger = vurderinger,
                verifiserFritekst = fritekst,
            ).let {
                hentNotat(it.first) to it.second
            }

            val (_, versjonEtterFørsteSendingTilAttestering) = sendTilbakekrevingsbehandlingTilAttestering(
                sakId = sakId,
                tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                saksversjon = versjonEtterNotat,
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

            forhåndsvisVedtaksbrevTilbakekrevingsbehandling(
                sakId = sakId,
                tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
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
                vurderingerRequest = """
                    [
                        {
                            "periode": {
                              "fraOgMed": "2021-01-01",
                              "tilOgMed": "2021-01-31"
                            },
                            "vurdering": "SkalIkkeTilbakekreve"
                        }
                    ]
                """.trimIndent(),
                tilstand = "VEDTAKSBREV",
                expectedFritekst = "Regresjonstest: Fritekst til vedtaksbrev under tilbakekrevingsbehandling.",
                expectedAttesteringer = underkjentAttestering,
                expectedVurderinger = """
              {
                "perioder":[
                  {
                    "periode":{
                      "fraOgMed":"2021-01-01",
                      "tilOgMed":"2021-01-31"
                    },
                    "vurdering":"SkalIkkeTilbakekreve",
                    "betaltSkattForYtelsesgruppen":6192,
                    "bruttoTidligereUtbetalt":20946,
                    "bruttoNyUtbetaling":8563,
                    "bruttoSkalTilbakekreve":0,
                    "nettoSkalTilbakekreve":0,
                    "bruttoSkalIkkeTilbakekreve":12383,
                    "skatteProsent":"50"
                  }
                ],
                "eksternKravgrunnlagId":"123456",
                "eksternVedtakId":"654321",
                "eksternKontrollfelt":"2021-02-01-02.03.28.456789",
                "bruttoSkalTilbakekreveSummert":0,
                "nettoSkalTilbakekreveSummert":0,
                "bruttoSkalIkkeTilbakekreveSummert":12383,
                "betaltSkattForYtelsesgruppenSummert":6192,
                "bruttoNyUtbetalingSummert":8563,
                "bruttoTidligereUtbetaltSummert":20946
              }
                """.trimIndent(),
                expectedNotat = notat,
            )
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
            val (_, versjonEtterIverksetting) = appComponents.iverksettTilbakekrevingsbehandling(
                sakId = sakId,
                tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                saksversjon = versjonEtterOppdateringAvOppgaveAndreAttestering,
                client = this.client,
                verifiserForhåndsvarselDokumenter = forhåndsvarselDokumenter,
                verifiserVurderinger = vurderingerEtterUnderkjenning,
                verifiserFritekst = fritekst,
                tidligereAttesteringer = underkjentAttestering,
            )
            appComponents.lukkOppgave(versjonEtterIverksetting)
            appComponents.verifiserLukketOppgaveKonsument()
            verifiserKravgrunnlagPåSak(sakId, client, true, versjonEtterIverksetting.toInt())

            // TODO jah: sende tilbakekrevingsvedtaket til oppdrag + sende brev hvis det er valgt.

            // kjører konsumenter en gang til på slutten for å verifisere at dette ikke vil føre til flere hendelser
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
            val (tilbakekrevingsbehandlingId, saksversjonEtterOpprettelseAvBehandling) = appComponents.opprettTilbakekrevingsbehandling(
                sakId = sakId,
                saksversjon = 2,
                client = this.client,
            )

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
            val versjonEtterNyttKravgrunnlag = saksversjonEtterOpprettelseAvBehandling + 1
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

            appComponents.lukkOppgave(versjonEtterAvbrytelse)
            appComponents.verifiserLukketOppgaveKonsument()

            // kjører konsumenter en gang til på slutten for å verifisere at dette ikke vil føre til flere hendelser
            appComponents.kjøreAlleTilbakekrevingskonsumenter()
            appComponents.kjøreAlleVerifiseringer(
                sakId = sakId,
                antallOpprettetOppgaver = 1,
                antallOppdatertOppgaveHendelser = 0,
                antallLukketOppgaver = 1,
                antallGenererteVedtaksbrev = 0,
                antallGenererteForhåndsvarsler = 0,
                antallJournalførteDokumenter = 0,
                antallDistribuertDokumenter = 0,
            )
        }
    }
}
