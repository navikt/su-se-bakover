package no.nav.su.se.bakover.web.avkorting

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.juli
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.juli
import no.nav.su.se.bakover.domain.vedtak.VedtakOpphørMedUtbetaling
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.revurdering.opprettIverksattRevurdering
import no.nav.su.se.bakover.web.revurdering.utenlandsopphold.leggTilUtenlandsoppholdRevurdering
import no.nav.su.se.bakover.web.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.SKIP_STEP
import no.nav.su.se.bakover.web.søknadsbehandling.opprettInnvilgetSøknadsbehandling
import org.json.JSONObject
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Motstykket til [HeleRevurderingsperiodenFørerTilAvkortingIT].
 * Deler av revurderingsperioden fører til avkorting, mens deler fører til opphør fram i tid.
 */
internal class DelerAvRevurderingsperiodenFørerTilAvkortingIT {
    @Test
    fun `deler av revurderingsperioden fører til avkorting med opphør fram i tid`() {
        // Vi starter 1 juli 2021 for å emulere at til og med juni er utbetalt (etterbetaling) og revurderingen sin simulering fører til feilutbetalinger til og med juni.
        val tikkendeKlokke = TikkendeKlokke(fixedClockAt(1.juli(2021)))
        SharedRegressionTestData.withTestApplicationAndEmbeddedDb(
            clock = tikkendeKlokke,
        ) { appComponents ->
            val stønadStart = 1.januar(2021)
            val stønadSlutt = 31.desember(2021)
            val fnr = Fnr.generer().toString()
            opprettInnvilgetSøknadsbehandling(
                fnr = fnr,
                fraOgMed = stønadStart.toString(),
                tilOgMed = stønadSlutt.toString(),
                client = this.client,
                appComponents = appComponents,
            ).let { søknadsbehandlingJson ->
                val sakId = BehandlingJson.hentSakId(søknadsbehandlingJson)

                opprettIverksattRevurdering(
                    sakid = sakId,
                    fraogmed = stønadStart.toString(),
                    tilogmed = stønadSlutt.toString(),
                    client = this.client,
                    appComponents = appComponents,

                    leggTilUføregrunnlag = { _, _, _, _, _, _, _ -> SKIP_STEP },
                    leggTilBosituasjon = { _, _, _, _ -> SKIP_STEP },
                    leggTilFormue = { _, _, _, _ -> SKIP_STEP },
                    informasjonSomRevurderes = "[\"Utenlandsopphold\"]",
                    leggTilUtenlandsoppholdRevurdering = { _, behandlingId, _, _, _ ->
                        leggTilUtenlandsoppholdRevurdering(
                            sakId = sakId,
                            behandlingId = behandlingId,
                            fraOgMed = stønadStart.toString(),
                            tilOgMed = stønadSlutt.toString(),
                            client = client,
                            vurdering = "SkalVæreMerEnn90DagerIUtlandet",
                        )
                    },
                    leggTilFlyktningVilkår = { _, _, _, _, _, _ -> SKIP_STEP },
                    leggTilFradrag = { _, _, _, _ -> SKIP_STEP },
                ).also {
                    JSONObject(it).let {
                        it.get("tilbakekrevingsbehandling") shouldBe null
                        it.getJSONObject("simuleringForAvkortingsvarsel").getJSONObject("totalOppsummering")
                            .toString() shouldEqualJson """
                        {
                          "fraOgMed": "2021-01-01",
                          "tilOgMed": "2021-06-30",
                          "sumEtterbetaling": 0,
                          "sumFeilutbetaling": 127762,
                          "sumFramtidigUtbetaling": 0,
                          "sumTidligereUtbetalt": 127762,
                          "sumTotalUtbetaling": 0,
                          "sumTilUtbetaling": 0,
                          "sumReduksjonFeilkonto": 0
                        }       
                        """.trimIndent()
                    }
                    appComponents.databaseRepos.sak.hentSak(UUID.fromString(sakId))!!.let {
                        it.utbetalinger.size shouldBe 2
                        it.utbetalinger[1].utbetalingslinjer.size shouldBe 1
                        it.utbetalinger[1].utbetalingslinjer[0].periode shouldBe juli(2021)..desember(2021)
                        it.vedtakListe.size shouldBe 2
                        it.vedtakListe[1] shouldBe beOfType<VedtakOpphørMedUtbetaling>()
                    }
                }
            }
        }
    }
}
