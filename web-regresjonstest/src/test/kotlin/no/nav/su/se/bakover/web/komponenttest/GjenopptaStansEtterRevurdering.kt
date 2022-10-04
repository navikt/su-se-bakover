package no.nav.su.se.bakover.web.komponenttest

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.november
import no.nav.su.se.bakover.common.periode.august
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.november
import no.nav.su.se.bakover.common.periode.september
import no.nav.su.se.bakover.common.september
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.web.revurdering.gjenopptak.iverksettGjenopptak
import no.nav.su.se.bakover.web.revurdering.gjenopptak.opprettGjenopptak
import no.nav.su.se.bakover.web.revurdering.opprettIverksattRevurdering
import no.nav.su.se.bakover.web.stans.iverksettStans
import no.nav.su.se.bakover.web.stans.opprettStans
import no.nav.su.se.bakover.web.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.RevurderingJson
import no.nav.su.se.bakover.web.søknadsbehandling.opprettInnvilgetSøknadsbehandling
import org.json.JSONObject
import org.junit.jupiter.api.Test
import java.util.UUID

class GjenopptaStansEtterRevurdering {
    @Test
    fun `stans - revurdering - gjenopptak`() {
        val fnr = Fnr.generer()
        withKomptestApplication(
            clock = TikkendeKlokke(),
        ) { appComponents ->
            val sakId = opprettInnvilgetSøknadsbehandling(
                fnr = fnr.toString(),
                fraOgMed = 1.januar(2021).toString(),
                tilOgMed = 31.desember(2021).toString(),
            ).let { søknadsbehandlingJson ->
                val sakId = BehandlingJson.hentSakId(søknadsbehandlingJson)

                val stansId = opprettStans(
                    sakId = sakId,
                    fraOgMed = 1.januar(2021).toString(),
                ).let {
                    RevurderingJson.hentRevurderingId(it)
                }
                iverksettStans(
                    sakId = sakId,
                    behandlingId = stansId,
                )
                opprettIverksattRevurdering(
                    sakid = sakId,
                    fraogmed = 1.september(2021).toString(),
                    tilogmed = 30.november(2021).toString(),
                )
                sakId
            }
            val gjenopptakId = opprettGjenopptak(
                sakId = sakId,
            ).let {
                JSONObject(it).getJSONObject("periode")
                    .toString() shouldBe """{"fraOgMed":"2021-12-01","tilOgMed":"2021-12-31"}"""
                JSONObject(it).getString("status").toString() shouldBe "SIMULERT_GJENOPPTAK"
                JSONObject(it).getJSONObject("simulering").getJSONArray("perioder")
                    .toString() shouldBe """[{"fraOgMed":"2021-12-01","tilOgMed":"2021-12-31","bruttoYtelse":20946,"type":"ORDINÆR"}]"""
                RevurderingJson.hentRevurderingId(it)
            }
            iverksettGjenopptak(
                sakId = sakId,
                behandlingId = gjenopptakId,
            )
            appComponents.services.sak.hentSak(UUID.fromString(sakId)).getOrFail().vedtakstidslinje()
                .map { Pair(it.originaltVedtak::class, it.periode) } shouldBe listOf(
                Pair(
                    VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse::class,
                    januar(2021)..august(2021),
                ),
                Pair(
                    VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering::class,
                    september(2021)..november(2021),
                ),
                Pair(
                    VedtakSomKanRevurderes.EndringIYtelse.GjenopptakAvYtelse::class,
                    desember(2021),
                ),
            )
        }
    }
}
