package no.nav.su.se.bakover.database.revurdering

import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.grunnlagsdataEnsligUtenFradrag
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.simulering
import no.nav.su.se.bakover.test.vilkårsvurderingerRevurderingInnvilget
import org.junit.jupiter.api.Test
import java.util.UUID

internal class StansAvYtelsePostgresRepoTest {
    @Test
    fun `lagrer og henter revurdering for stans av ytelse`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val vedtak =
                testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second

            val simulertRevurdering = StansAvYtelseRevurdering.SimulertStansAvYtelse(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = år(2021),
                grunnlagsdata = grunnlagsdataEnsligUtenFradrag(),
                vilkårsvurderinger = vilkårsvurderingerRevurderingInnvilget(),
                tilRevurdering = vedtak.id,
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

            val iverksattRevurdering = simulertRevurdering.iverksett(
                Attestering.Iverksatt(NavIdentBruker.Attestant("atte"), fixedTidspunkt),
            ).getOrFail("Feil i oppsett av testdata")
            testDataHelper.revurderingRepo.lagre(iverksattRevurdering)

            testDataHelper.revurderingRepo.hent(iverksattRevurdering.id) shouldBe iverksattRevurdering
        }
    }

    @Test
    fun `kan oppdatere revurdering for stans av ytelse`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val vedtak =
                testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second

            val simulertRevurdering = StansAvYtelseRevurdering.SimulertStansAvYtelse(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = år(2021),
                grunnlagsdata = grunnlagsdataEnsligUtenFradrag(),
                vilkårsvurderinger = vilkårsvurderingerRevurderingInnvilget(),
                tilRevurdering = vedtak.id,
                saksbehandler = saksbehandler,
                simulering = simulering(),
                revurderingsårsak = Revurderingsårsak.create(
                    årsak = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING.toString(),
                    begrunnelse = "huffa",
                ),
                sakinfo = vedtak.sakinfo(),
            )

            testDataHelper.revurderingRepo.lagre(simulertRevurdering)
            testDataHelper.revurderingRepo.hent(simulertRevurdering.id)!!.shouldBeEqualToIgnoringFields(
                simulertRevurdering,
                StansAvYtelseRevurdering.SimulertStansAvYtelse::tilRevurdering,
            )

            val nyInformasjon = simulertRevurdering.copy(
                id = simulertRevurdering.id,
                opprettet = simulertRevurdering.opprettet,
                periode = mai(2021),
                grunnlagsdata = grunnlagsdataEnsligUtenFradrag(mai(2021)),
                vilkårsvurderinger = vilkårsvurderingerRevurderingInnvilget(periode = mai(2021)),
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
            testDataHelper.revurderingRepo.hent(simulertRevurdering.id)!!.shouldBeEqualToIgnoringFields(
                nyInformasjon,
                StansAvYtelseRevurdering.SimulertStansAvYtelse::tilRevurdering,
            )
        }
    }

    @Test
    fun `lagrer og henter en avsluttet stansAvYtelse revurdering`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val vedtak =
                testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second

            val simulertRevurdering = StansAvYtelseRevurdering.SimulertStansAvYtelse(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = år(2021),
                grunnlagsdata = grunnlagsdataEnsligUtenFradrag(),
                vilkårsvurderinger = vilkårsvurderingerRevurderingInnvilget(),
                tilRevurdering = vedtak.id,
                saksbehandler = saksbehandler,
                simulering = simulering(),
                revurderingsårsak = Revurderingsårsak.create(
                    årsak = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING.toString(),
                    begrunnelse = "huffa",
                ),
                sakinfo = vedtak.sakinfo(),
            )

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
