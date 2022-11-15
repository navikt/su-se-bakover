package no.nav.su.se.bakover.web.revurdering.brevvalg

import no.nav.su.se.bakover.test.tikkendeFixedClock
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.SharedRegressionTestData.fnr
import no.nav.su.se.bakover.web.revurdering.opprettIverksattRevurdering
import no.nav.su.se.bakover.web.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.RevurderingJson
import no.nav.su.se.bakover.web.søknadsbehandling.SKIP_STEP
import no.nav.su.se.bakover.web.søknadsbehandling.opprettInnvilgetSøknadsbehandling
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class LeggTilBrevvalgIT {
    @Test
    fun `oppdatering av brevvalg`() {
        SharedRegressionTestData.withTestApplicationAndEmbeddedDb(
            clock = tikkendeFixedClock,
        ) {
            opprettInnvilgetSøknadsbehandling(
                fnr = fnr,
                fraOgMed = "2022-01-01",
                tilOgMed = "2022-12-31",
            ).let { søknadsbehandlingJson ->

                val sakId = BehandlingJson.hentSakId(søknadsbehandlingJson)

                opprettIverksattRevurdering(
                    sakid = sakId,
                    fraogmed = "2022-05-01",
                    tilogmed = "2022-12-31",
                    leggTilBrevvalg = { _, _ -> SKIP_STEP },
                    sendTilAttestering = { _, _ -> SKIP_STEP },
                    iverksett = { _, _ -> SKIP_STEP },
                ).let { revurderingJson ->
                    val revurderingId = RevurderingJson.hentRevurderingId(revurderingJson)
                    // default satt til send brev av systemet
                    JSONAssert.assertEquals(
                        """
                    {
                        "valg":"SEND",
                        "fritekst":null,
                        "begrunnelse":null,
                        "bestemtAv":"srvsupstonad"
                    }
                    """,
                        RevurderingJson.hentBrevvalg(revurderingJson),
                        true,
                    )

                    velgSendBrev(
                        sakId = sakId,
                        behandlingId = revurderingId,
                        fritekst = "befriende tekst",
                        begrunnelse = "godt begrunnet",
                    ).let {
                        JSONAssert.assertEquals(
                            """
                    {
                        "valg":"SEND",
                        "fritekst":"befriende tekst",
                        "begrunnelse":"godt begrunnet",
                        "bestemtAv":"Z990Lokal"
                    }
                    """,
                            RevurderingJson.hentBrevvalg(it),
                            true,
                        )
                    }

                    velgIkkeSendBrev(
                        sakId = sakId,
                        behandlingId = revurderingId,
                        begrunnelse = "ikke behov likevel",
                    ).let {
                        JSONAssert.assertEquals(
                            """
                    {
                        "valg":"IKKE_SEND",
                        "fritekst": null,
                        "begrunnelse":"ikke behov likevel",
                        "bestemtAv":"Z990Lokal"
                    }
                    """,
                            RevurderingJson.hentBrevvalg(it),
                            true,
                        )
                    }
                }
            }
        }
    }
}
