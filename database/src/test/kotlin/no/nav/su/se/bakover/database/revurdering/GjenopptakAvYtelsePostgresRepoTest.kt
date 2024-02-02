package no.nav.su.se.bakover.database.revurdering

import behandling.revurdering.domain.GrunnlagsdataOgVilkårsvurderingerRevurdering
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.attestering.Attestering
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.revurdering.revurderes.VedtakSomRevurderesMånedsvis
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.test.enUkeEtterFixedTidspunkt
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.grunnlagsdataEnsligUtenFradrag
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.simulering.simulering
import no.nav.su.se.bakover.test.simulertGjenopptakAvYtelseFraVedtakStansAvYtelse
import no.nav.su.se.bakover.test.vilkårsvurderingerRevurderingInnvilget
import org.junit.jupiter.api.Test
import java.util.UUID

internal class GjenopptakAvYtelsePostgresRepoTest {
    @Test
    fun `lagrer og henter revurdering for gjenopptak av ytelse`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)

            val (sakEtterStans, stansVedtak) = testDataHelper.persisterIverksattStansOgVedtak()

            val (_, simulertRevurdering) = simulertGjenopptakAvYtelseFraVedtakStansAvYtelse(
                sakOgVedtakSomKanRevurderes = sakEtterStans to stansVedtak,
                clock = testDataHelper.clock,
            )

            testDataHelper.revurderingRepo.lagre(simulertRevurdering)

            testDataHelper.revurderingRepo.hent(simulertRevurdering.id) shouldBe simulertRevurdering

            val persistertSimulert =
                testDataHelper.revurderingRepo.hent(simulertRevurdering.id)!! as GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse

            val iverksattRevurdering = persistertSimulert.iverksett(
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
            val vedtak = testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second

            val simulertRevurdering = GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse(
                id = RevurderingId.generer(),
                opprettet = fixedTidspunkt,
                oppdatert = fixedTidspunkt,
                periode = år(2021),
                grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerRevurdering(
                    grunnlagsdata = grunnlagsdataEnsligUtenFradrag(),
                    vilkårsvurderinger = vilkårsvurderingerRevurderingInnvilget(),
                ),
                tilRevurdering = vedtak.id,
                vedtakSomRevurderesMånedsvis = VedtakSomRevurderesMånedsvis(
                    mapOf(
                        januar(2021) to UUID.randomUUID(),
                        mars(2021) to UUID.randomUUID(),
                    ),
                ),
                saksbehandler = saksbehandler,
                simulering = simulering(),
                revurderingsårsak = Revurderingsårsak.create(
                    årsak = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING.toString(),
                    begrunnelse = "huffa",
                ),
                sakinfo = vedtak.sakinfo(),
            )

            testDataHelper.revurderingRepo.lagre(simulertRevurdering)
            testDataHelper.revurderingRepo.hent(simulertRevurdering.id) shouldBe simulertRevurdering

            val nyInformasjon = simulertRevurdering.copy(
                oppdatert = enUkeEtterFixedTidspunkt,
                periode = mai(2021),
                grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerRevurdering(
                    grunnlagsdata = grunnlagsdataEnsligUtenFradrag(mai(2021)),
                    vilkårsvurderinger = vilkårsvurderingerRevurderingInnvilget(periode = mai(2021)),
                ),
                tilRevurdering = vedtak.id,
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

    @Test
    fun `lagrer og henter en avsluttet gjenopptakAvYtelse`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val vedtak = testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second
            val simulertRevurdering = GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse(
                id = RevurderingId.generer(),
                opprettet = fixedTidspunkt,
                oppdatert = fixedTidspunkt,
                periode = år(2021),
                grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerRevurdering(
                    grunnlagsdata = grunnlagsdataEnsligUtenFradrag(),
                    vilkårsvurderinger = vilkårsvurderingerRevurderingInnvilget(),
                ),
                tilRevurdering = vedtak.id,
                vedtakSomRevurderesMånedsvis = VedtakSomRevurderesMånedsvis(
                    mapOf(
                        januar(2021) to UUID.randomUUID(),
                        mars(2021) to UUID.randomUUID(),
                    ),
                ),
                saksbehandler = saksbehandler,
                simulering = simulering(),
                revurderingsårsak = Revurderingsårsak.create(
                    årsak = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING.toString(),
                    begrunnelse = "huffa",
                ),
                sakinfo = vedtak.sakinfo(),
            )

            testDataHelper.revurderingRepo.lagre(simulertRevurdering)

            val persistertSimulert =
                testDataHelper.revurderingRepo.hent(simulertRevurdering.id)!! as GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse

            val avsluttetGjenopptaAvYtelse = persistertSimulert.avslutt(
                begrunnelse = "Avslutter denne her",
                tidspunktAvsluttet = fixedTidspunkt,
                avsluttetAv = saksbehandler,
            ).getOrFail("Her skulle vi ha avsluttet en gjenopptaAvYtelse revurdering")

            testDataHelper.revurderingRepo.lagre(avsluttetGjenopptaAvYtelse)

            testDataHelper.revurderingRepo.hent(avsluttetGjenopptaAvYtelse.id) shouldBe avsluttetGjenopptaAvYtelse
        }
    }
}
