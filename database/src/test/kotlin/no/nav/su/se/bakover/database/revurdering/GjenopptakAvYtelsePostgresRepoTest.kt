package no.nav.su.se.bakover.database.revurdering

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
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

internal class GjenopptakAvYtelsePostgresRepoTest {
    @Test
    fun `lagrer og henter revurdering for gjenopptak av ytelse`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val vedtak =
                testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling().second

            val simulertRevurdering = GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = år(2021),
                grunnlagsdata = grunnlagsdataEnsligUtenFradrag(),
                vilkårsvurderinger = vilkårsvurderingerRevurderingInnvilget(),
                tilRevurdering = vedtak.id,
                saksbehandler = saksbehandler,
                simulering = simulering(),
                revurderingsårsak = Revurderingsårsak.create(
                    årsak = Revurderingsårsak.Årsak.MOTTATT_KONTROLLERKLÆRING.toString(),
                    begrunnelse = "huffa",
                ),
                sakinfo = vedtak.sakinfo(),
            )

            testDataHelper.revurderingRepo.lagre(simulertRevurdering)

            testDataHelper.revurderingRepo.hent(simulertRevurdering.id) shouldBe simulertRevurdering

            val persistertSimulert = testDataHelper.revurderingRepo.hent(simulertRevurdering.id)!! as GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse

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

            val nyInformasjon = simulertRevurdering.copy(
                periode = mai(2021),
                grunnlagsdata = grunnlagsdataEnsligUtenFradrag(mai(2021)),
                vilkårsvurderinger = vilkårsvurderingerRevurderingInnvilget(periode = mai(2021)),
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

            val persistertSimulert = testDataHelper.revurderingRepo.hent(simulertRevurdering.id)!! as GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse

            val avsluttetGjenopptaAvYtelse = persistertSimulert.avslutt(
                begrunnelse = "Avslutter denne her",
                tidspunktAvsluttet = fixedTidspunkt,
            ).getOrFail("Her skulle vi ha avsluttet en gjenopptaAvYtelse revurdering")

            testDataHelper.revurderingRepo.lagre(avsluttetGjenopptaAvYtelse)

            testDataHelper.revurderingRepo.hent(avsluttetGjenopptaAvYtelse.id) shouldBe avsluttetGjenopptaAvYtelse
        }
    }
}
