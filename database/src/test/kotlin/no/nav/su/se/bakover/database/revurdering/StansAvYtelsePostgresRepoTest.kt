package no.nav.su.se.bakover.database.revurdering

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.grunnlagsdataEnsligUtenFradrag
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.simulering.simulering
import no.nav.su.se.bakover.test.simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import no.nav.su.se.bakover.test.vilkårsvurderingerRevurderingInnvilget
import org.junit.jupiter.api.Test

internal class StansAvYtelsePostgresRepoTest {
    @Test
    fun `lagrer og henter revurdering for stans av ytelse`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val (sak, vedtak) = testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling()

            val simulertRevurdering = simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
                sakOgVedtakSomKanRevurderes = sak to vedtak,
                clock = testDataHelper.clock,
            ).second

            testDataHelper.revurderingRepo.lagre(simulertRevurdering)

            testDataHelper.revurderingRepo.hent(simulertRevurdering.id) shouldBe simulertRevurdering

            val iverksattRevurdering = simulertRevurdering.iverksett(
                Attestering.Iverksatt(NavIdentBruker.Attestant("atte"), fixedTidspunkt),
            ).getOrFail()

            testDataHelper.revurderingRepo.lagre(iverksattRevurdering)

            testDataHelper.revurderingRepo.hent(iverksattRevurdering.id) shouldBe iverksattRevurdering
        }
    }

    @Test
    fun `kan oppdatere revurdering for stans av ytelse`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val (sak, vedtak) = testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling()

            val simulertRevurdering = simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
                sakOgVedtakSomKanRevurderes = sak to vedtak,
                clock = testDataHelper.clock,
            ).second

            testDataHelper.revurderingRepo.lagre(simulertRevurdering)

            testDataHelper.revurderingRepo.hent(simulertRevurdering.id) shouldBe simulertRevurdering

            val nyInformasjon = simulertRevurdering.copy(
                id = simulertRevurdering.id,
                opprettet = simulertRevurdering.opprettet,
                periode = mai(2021),
                grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderinger.Revurdering(
                    grunnlagsdata = grunnlagsdataEnsligUtenFradrag(mai(2021)),
                    vilkårsvurderinger = vilkårsvurderingerRevurderingInnvilget(periode = mai(2021)),
                ),
                tilRevurdering = vedtak.id,
                saksbehandler = NavIdentBruker.Saksbehandler("saksern"),
                simulering = simulering().copy(
                    gjelderNavn = "et navn",
                ),
                revurderingsårsak = Revurderingsårsak.create(
                    årsak = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING.toString(),
                    begrunnelse = "en begrunnelse",
                ),
            )

            testDataHelper.revurderingRepo.lagre(nyInformasjon)
            testDataHelper.revurderingRepo.hent(simulertRevurdering.id) shouldBe nyInformasjon
        }
    }

    @Test
    fun `lagrer og henter en avsluttet stansAvYtelse revurdering`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val (sak, vedtak) = testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling()

            val simulertRevurdering = simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
                sakOgVedtakSomKanRevurderes = sak to vedtak,
                clock = testDataHelper.clock,
            ).second

            testDataHelper.revurderingRepo.lagre(simulertRevurdering)

            val persistertSimulert = testDataHelper.revurderingRepo.hent(simulertRevurdering.id) as StansAvYtelseRevurdering.SimulertStansAvYtelse

            val avsluttet = persistertSimulert.avslutt(
                begrunnelse = "jeg opprettet en stans av ytelse, så gjennom, og så teknte 'neh'",
                tidspunktAvsluttet = fixedTidspunkt,
            ).getOrFail("her skulle vi ha hatt en avsluttet stans av ytelse revurdering")

            testDataHelper.revurderingRepo.lagre(avsluttet)

            testDataHelper.revurderingRepo.hent(avsluttet.id) shouldBe avsluttet
        }
    }
}
