package no.nav.su.se.bakover.web.tilbakekreving

import no.nav.su.se.bakover.common.domain.tid.februar
import no.nav.su.se.bakover.common.domain.tid.januar
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
import no.nav.su.se.bakover.web.tilbakekreving.LeggTilNotatTilbakekrevingsbehandling.leggTilNotatTilbakekrevingsbehandling
import no.nav.su.se.bakover.web.tilbakekreving.OppdaterKravgrunnlagTilbakekrevingsbehandling.oppdaterKravgrunnlag
import no.nav.su.se.bakover.web.tilbakekreving.OppdaterVedtaksbrevTilbakekrevingsbehandling.oppdaterVedtaksbrevTilbakekrevingsbehandling
import no.nav.su.se.bakover.web.tilbakekreving.VurderTilbakekrevingsbehandling.vurderTilbakekrevingsbehandling
import org.json.JSONArray
import org.junit.jupiter.api.Test

internal class TilbakekrevingsbehandlingIT {

    /*
    Denne er til for å teste at hentUteståendeSakOgHendelsesIderForKonsumentOgTypeTilbakekreving ikke prøver å generere dokument for en tilbakekreving  som er avbrutt AVBRUTT_TILBAKEKREVINGSBEHANDLING
     */
    @Test
    fun `Opprettet tilbakekrevingsbehandling med forhåndsvarsling også avbrutt og verifiser at dokumenter ikke blir sendt`() {
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
            // 1. reservert, 2. kvittering søknadsbehandling 3. kvittering revurdering 4. kravgrunnlag
            verifiserKravgrunnlagPåSak(sakId, client, true, 4)
            val (tilbakekrevingsbehandlingId, saksversjonEtterOpprettelseAvBehandling) = appComponents.opprettTilbakekrevingsbehandling(
                sakId = sakId,
                // Må økes etter hvert som vi får flere hendelser.
                hendelsesversjon = 4,
                client = this.client,
            )

            forhåndsvisForhåndsvarselTilbakekreving(
                sakId = sakId,
                tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                saksversjon = saksversjonEtterOpprettelseAvBehandling,
                client = this.client,
            )
            appComponents.avbrytTilbakekrevingsbehandling(sakId, tilbakekrevingsbehandlingId, client = this.client, saksversjon = saksversjonEtterOpprettelseAvBehandling)

            appComponents.verifiserGenererDokumentForForhåndsvarselKonsument(0)
            appComponents.verifiserDokumentHendelser(sakId, 0, 0, 0)
        }
    }

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
            )
            appComponents.emulerViMottarKravgrunnlagDetaljer()
            // 1. reservert, 2. kvittering søknadsbehandling 3. kvittering revurdering 4. kravgrunnlag
            verifiserKravgrunnlagPåSak(sakId, client, true, 4)
            val (tilbakekrevingsbehandlingId, saksversjonEtterOpprettelseAvBehandling) = appComponents.opprettTilbakekrevingsbehandling(
                sakId = sakId,
                // Må økes etter hvert som vi får flere hendelser.
                hendelsesversjon = 4,
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
            )
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
            )
            val (_, versjonEtterFørsteSendingTilAttestering) = appComponents.sendTilbakekrevingsbehandlingTilAttestering(
                sakId = sakId,
                tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                saksversjon = versjonEtterNotat,
                client = this.client,
                verifiserForhåndsvarselDokumenter = forhåndsvarselDokumenter,
                verifiserVurderinger = vurderinger,
                verifiserFritekst = fritekst,
            )
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

            val (underkjentAttestering, versjonEtterUnderkjenning) = appComponents.underkjennTilbakekrevingsbehandling(
                sakId = sakId,
                tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                saksversjon = versjonEtterFørsteSendingTilAttestering,
                client = this.client,
                verifiserForhåndsvarselDokumenter = forhåndsvarselDokumenter,
                verifiserVurderinger = vurderinger,
                brevtekst = fritekst,
            )
            val (vurderingerEtterUnderkjenning, versjonEtterVurderingEtterUnderkjenning) = vurderTilbakekrevingsbehandling(
                sakId = sakId,
                tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                saksversjon = versjonEtterUnderkjenning,
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
                expectedVurderinger = lagVurderingerMedKravJson(
                    vurdering = "SkalIkkeTilbakekreve",
                    bruttoSkalTilbakekreve = 0,
                    nettoSkalTilbakekreve = 0,
                    bruttoSkalIkkeTilbakekreve = 2383,
                ),
                expectedNotat = notat,
            )
            val (_, versjonEtterAndreSendingTilAttestering) = appComponents.sendTilbakekrevingsbehandlingTilAttestering(
                sakId = sakId,
                tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                saksversjon = versjonEtterVurderingEtterUnderkjenning,
                client = this.client,
                verifiserForhåndsvarselDokumenter = forhåndsvarselDokumenter,
                verifiserVurderinger = lagVurderingerMedKravJson(
                    vurdering = "SkalIkkeTilbakekreve",
                    bruttoSkalTilbakekreve = 0,
                    nettoSkalTilbakekreve = 0,
                    bruttoSkalIkkeTilbakekreve = 2383,
                ),
                verifiserFritekst = fritekst,
                expectedAttesteringer = underkjentAttestering,
            )
            val (_, versjonEtterIverksetting) = appComponents.iverksettTilbakekrevingsbehandling(
                sakId = sakId,
                tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                fnr = fnr,
                saksversjon = versjonEtterAndreSendingTilAttestering,
                client = this.client,
                verifiserForhåndsvarselDokumenter = forhåndsvarselDokumenter,
                verifiserVurderinger = vurderingerEtterUnderkjenning,
                verifiserFritekst = fritekst,
                tidligereAttesteringer = underkjentAttestering,
            )
            verifiserKravgrunnlagPåSak(sakId, client, false, versjonEtterIverksetting.toInt())
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
            )
            appComponents.emulerViMottarKravgrunnlagDetaljer()
            // 1. reservert, 2. kvittering søknadsbehandling 3. kvittering revurdering 4. kravgrunnlag
            verifiserKravgrunnlagPåSak(sakId, client, true, 4)
            val (tilbakekrevingsbehandlingId, saksversjonEtterOpprettelseAvBehandling) = appComponents.opprettTilbakekrevingsbehandling(
                sakId = sakId,
                hendelsesversjon = 4,
                client = this.client,
            )
            val (_, versjonEtterVurdering) = vurderTilbakekrevingsbehandling(
                sakId = sakId,
                tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                saksversjon = saksversjonEtterOpprettelseAvBehandling,
                client = this.client,
            )
            opprettIverksattRevurdering(
                sakid = sakId,
                fraogmed = 1.januar(2021).toString(),
                tilogmed = 31.januar(2021).toString(),
                client = this.client,
                appComponents = appComponents,
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
            // Kommet en ny kvittering + kravgrunnlag
            val versjonEtterNyttKravgrunnlag = versjonEtterVurdering + 2
            verifiserKravgrunnlagPåSak(sakId, client, true, versjonEtterNyttKravgrunnlag.toInt())
            val (_, versjonEtterOppdateringAvKravgrunnlag) = oppdaterKravgrunnlag(
                sakId = sakId,
                tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                saksversjon = versjonEtterNyttKravgrunnlag,
                client = this.client,
            )
            appComponents.avbrytTilbakekrevingsbehandling(
                sakId = sakId,
                tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                client = this.client,
                saksversjon = versjonEtterOppdateringAvKravgrunnlag,
            )
        }
    }

    // TODO tilbakekreving jah: Skriv en test som sjekker at vi ikke kan åpne en tilbakekreving på en sak som har en tilbakekreving som er til behandling. Men dersom behandlingen avsluttes, kan vi åpne en ny tilbakekreving.
}
