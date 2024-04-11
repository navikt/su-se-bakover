package no.nav.su.se.bakover.web.tilbakekreving

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.tid.februar
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.kravgrunnlag.emulerViMottarKravgrunnlagDetaljer
import no.nav.su.se.bakover.web.kravgrunnlag.emulerViMottarKravgrunnlagstatusendring
import no.nav.su.se.bakover.web.revurdering.opprettIverksattRevurdering
import no.nav.su.se.bakover.web.sak.hent.hentSak
import no.nav.su.se.bakover.web.sak.hent.hentSaksnummer
import no.nav.su.se.bakover.web.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.RevurderingJson
import no.nav.su.se.bakover.web.søknadsbehandling.opprettInnvilgetSøknadsbehandling
import org.json.JSONObject
import org.junit.jupiter.api.Test

internal class TilbakekrevingsbehandlingStatusendringIT {

    @Test
    fun `Endrer tilstand basert på statusendring`() {
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
            val saksnummer = hentSaksnummer(hentSak(sakId, client))

            opprettIverksattRevurdering(
                sakid = sakId,
                fraogmed = 1.januar(2021).toString(),
                tilogmed = 31.januar(2021).toString(),
                client = this.client,
                appComponents = appComponents,
            ).let {
                RevurderingJson.hentRevurderingId(it)
            }
            appComponents.emulerViMottarKravgrunnlagDetaljer()
            verifiserKravgrunnlagPåSak(sakId, client, true, 2)
            val eksternVedtakId = appComponents.opprettTilbakekrevingsbehandling(
                sakId = sakId,
                // Må økes etter hvert som vi får flere hendelser.
                saksversjon = 2,
                client = this.client,
                expectedKontrollfelt = "2021-02-01-02.03.28.456789",
            ).let {
                JSONObject(it.responseJson).getJSONObject("kravgrunnlag").getString("eksternVedtakId")
            }
            appComponents.emulerViMottarKravgrunnlagstatusendring(
                saksnummer = saksnummer,
                fnr = fnr,
                eksternVedtakId = eksternVedtakId,
            )
            JSONObject(hentSak(sakId, client)).also {
                it.getJSONObject("uteståendeKravgrunnlag").getString("status").shouldBe("SPER")
                it.getJSONArray("tilbakekrevinger").getJSONObject(0).getJSONObject("kravgrunnlag").getString("status").shouldBe("SPER")
            }
        }
    }
}
