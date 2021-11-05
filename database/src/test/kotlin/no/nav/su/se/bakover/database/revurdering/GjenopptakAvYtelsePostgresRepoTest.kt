package no.nav.su.se.bakover.database.revurdering

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.grunnlagsdataEnsligUtenFradrag
import no.nav.su.se.bakover.test.periode2021
import no.nav.su.se.bakover.test.periodeMai2021
import no.nav.su.se.bakover.test.revurderingsårsak
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.simulering
import no.nav.su.se.bakover.test.vilkårsvurderingerInnvilgetRevurdering
import org.junit.jupiter.api.Test
import java.util.UUID

internal class GjenopptakAvYtelsePostgresRepoTest {
    @Test
    fun `lagrer og henter revurdering for gjenopptak av ytelse`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val søknadsbehandling = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first

            val simulertRevurdering = GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = periode2021,
                grunnlagsdata = grunnlagsdataEnsligUtenFradrag(),
                vilkårsvurderinger = vilkårsvurderingerInnvilgetRevurdering(),
                tilRevurdering = søknadsbehandling,
                saksbehandler = saksbehandler,
                simulering = simulering(),
                revurderingsårsak = Revurderingsårsak.create(
                    årsak = Revurderingsårsak.Årsak.MOTTATT_KONTROLLERKLÆRING.toString(),
                    begrunnelse = "huffa",
                ),
            )

            testDataHelper.revurderingRepo.lagre(simulertRevurdering)

            testDataHelper.revurderingRepo.hent(simulertRevurdering.id) shouldBe simulertRevurdering

            val iverksattRevurdering = simulertRevurdering.iverksett(
                Attestering.Iverksatt(NavIdentBruker.Attestant("atte"), fixedTidspunkt),
            ).getOrFail("Feil i oppsett av testdata")

            testDataHelper.revurderingRepo.lagre(iverksattRevurdering)

            testDataHelper.revurderingRepo.hent(iverksattRevurdering.id) shouldBe iverksattRevurdering
        }
    }

    @Test
    fun `kan oppdatere revurdering for gjenopptak av ytelse`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val søknadsbehandling = testDataHelper.vedtakMedInnvilgetSøknadsbehandling().first

            val simulertRevurdering = GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = periode2021,
                grunnlagsdata = grunnlagsdataEnsligUtenFradrag(),
                vilkårsvurderinger = vilkårsvurderingerInnvilgetRevurdering(),
                tilRevurdering = søknadsbehandling,
                saksbehandler = saksbehandler,
                simulering = simulering(),
                revurderingsårsak = Revurderingsårsak.create(
                    årsak = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING.toString(),
                    begrunnelse = "huffa",
                ),
            )

            testDataHelper.revurderingRepo.lagre(simulertRevurdering)
            testDataHelper.revurderingRepo.hent(simulertRevurdering.id) shouldBe simulertRevurdering

            val nyInformasjon = simulertRevurdering.copy(
                periode = periodeMai2021,
                grunnlagsdata = grunnlagsdataEnsligUtenFradrag(periodeMai2021),
                vilkårsvurderinger = vilkårsvurderingerInnvilgetRevurdering(periode = periodeMai2021),
                tilRevurdering = søknadsbehandling,
                saksbehandler = NavIdentBruker.Saksbehandler("saksern"),
                simulering = simulering().copy(
                    gjelderNavn = "et navn",
                ),
                revurderingsårsak = Revurderingsårsak.create(
                    årsak = Revurderingsårsak.Årsak.MOTTATT_KONTROLLERKLÆRING.toString(),
                    begrunnelse = "en begrunnelse",
                ),
            )

            testDataHelper.revurderingRepo.lagre(nyInformasjon)
            testDataHelper.revurderingRepo.hent(simulertRevurdering.id) shouldBe nyInformasjon
        }
    }
}
