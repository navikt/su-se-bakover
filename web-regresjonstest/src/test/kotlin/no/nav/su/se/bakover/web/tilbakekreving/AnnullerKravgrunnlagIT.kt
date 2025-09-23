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
import no.nav.su.se.bakover.web.revurdering.opprettIverksattRevurdering
import no.nav.su.se.bakover.web.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.RevurderingJson
import no.nav.su.se.bakover.web.søknadsbehandling.opprettInnvilgetSøknadsbehandling
import org.junit.jupiter.api.Test

internal class AnnullerKravgrunnlagIT {

    @Test
    fun `annullerer kravgrunnlag uten tilbakekrevingsbehandling`() {
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
            ).let {
                RevurderingJson.hentRevurderingId(it)
            }
            val kravgrunnlagHendelser = appComponents.emulerViMottarKravgrunnlagDetaljer().also {
                it.size shouldBe 1
            }
            // 1. reservert, 2. kvittering søknadsbehandling 3. kvittering revurdering 4. kravgrunnlag
            appComponents.annullerKravgrunnlag(
                sakId = sakId,
                kravgrunnlagHendelseId = kravgrunnlagHendelser.first().toString(),
                saksversjon = 5,
                client = this.client,
            )
            hentKravgrunnlagPåSak(sakId, client) shouldBe null
        }
    }

    @Test
    fun `annullerer kravgrunnlag som har en aktiv tilbakekrevingsbehandling`() {
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
            ).let {
                RevurderingJson.hentRevurderingId(it)
            }
            val kravgrunnlagHendelser = appComponents.emulerViMottarKravgrunnlagDetaljer().also {
                it.size shouldBe 1
            }
            // 1. reservert, 2. kvittering søknadsbehandling 3. kvittering revurdering 4. kravgrunnlag
            verifiserKravgrunnlagPåSak(sakId, client, true, 4)
            val (tilbakekrevingsbehandlingId, saksversjonEtterOpprettelseAvBehandling) = appComponents.opprettTilbakekrevingsbehandling(
                sakId = sakId,
                // Må økes etter hvert som vi får flere hendelser.
                saksversjon = 4,
                client = this.client,
            )
            appComponents.annullerKravgrunnlag(
                sakId = sakId,
                kravgrunnlagHendelseId = kravgrunnlagHendelser.first().toString(),
                saksversjon = saksversjonEtterOpprettelseAvBehandling.inc(),
                client = this.client,
                verifiserBehandling = AnnullerKravgrunnlagTilbakekrevingsbehandlingVerifikasjon(
                    behandlingsId = tilbakekrevingsbehandlingId,
                    sakId = sakId,
                    kravgrunnlagHendelseId = kravgrunnlagHendelser.first().toString(),
                ),
            )
            hentKravgrunnlagPåSak(sakId, client) shouldBe null
        }
    }
}
