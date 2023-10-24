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
            val tilbakekrevingsbehandlingId = opprettTilbakekrevingsbehandling(
                sakId = sakId,
                // Må økes etter hvert som vi får flere hendelser.
                saksversjon = 2,
                client = this.client,
                opprettOppgaveForTilbakekrevingshendelserKonsument = appComponents.tilbakekrevingskomponenter.services.opprettOppgaveForTilbakekrevingshendelserKonsument,
            ).let {
                hentTilbakekrevingsbehandlingId(it)
            }
            // Saksversjon 4 vil være en asynkron oppgave
            forhåndsvisForhåndsvarselTilbakekreving(
                sakId = sakId,
                tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                saksversjon = 4,
                client = this.client,
            )
            val forhåndsvarselDokumenter = forhåndsvarsleTilbakekrevingsbehandling(
                sakId = sakId,
                tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                saksversjon = 4,
                client = this.client,
            ).let {
                hentForhåndsvarselDokumenter(it)
            }
            // Saksversjon 6 vil være en synkron oppgave (TODO: skal bli asynkront)
            // Saksversjon 7 vil være et synkront dokument (TODO: skal bli asynkront)
            val vurderinger = vurderTilbakekrevingsbehandling(
                sakId = sakId,
                tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                saksversjon = 7,
                client = this.client,
                verifiserForhåndsvarselDokumenter = forhåndsvarselDokumenter,
            ).let {
                hentVurderinger(it)
            }
            val fritekst = oppdaterVedtaksbrevTilbakekrevingsbehandling(
                sakId = sakId,
                tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                saksversjon = 8,
                client = this.client,
                verifiserForhåndsvarselDokumenter = forhåndsvarselDokumenter,
                verifiserVurderinger = vurderinger,
            ).let {
                hentFritekst(it)
            }
            // hjelper med å teste [Utfylt] tilstanden
            val vurderinger2 = vurderTilbakekrevingsbehandling(
                sakId = sakId,
                tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                saksversjon = 9,
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
            ).let {
                hentVurderinger(it)
            }
            sendTilbakekrevingsbehandlingTilAttestering(
                sakId = sakId,
                tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                saksversjon = 10,
                client = this.client,
                verifiserForhåndsvarselDokumenter = forhåndsvarselDokumenter,
                verifiserVurderinger = vurderinger2,
                verifiserFritekst = fritekst,
            )
            // TODO jah: Her skal vi lukke+opprette ny oppgave.
            iverksettTilbakekrevingsbehandling(
                sakId = sakId,
                tilbakekrevingsbehandlingId = tilbakekrevingsbehandlingId,
                saksversjon = 11,
                client = this.client,
                verifiserForhåndsvarselDokumenter = forhåndsvarselDokumenter,
                verifiserVurderinger = vurderinger2,
                verifiserFritekst = fritekst,
            )
            // TODO jah: Her skal vi lukke oppgave + sende tilbakekrevingsvedtaket til oppdrag + sende brev hvis det er valgt.
            verifiserKravgrunnlagPåSak(sakId, client, true, 12)
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
