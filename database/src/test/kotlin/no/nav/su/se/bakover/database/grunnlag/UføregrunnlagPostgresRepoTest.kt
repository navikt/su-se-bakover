package no.nav.su.se.bakover.database.grunnlag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.extensions.april
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.mai
import no.nav.su.se.bakover.common.infrastructure.persistence.antall
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.nySøknadsbehandlingMedStønadsperiode
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.persistence.withSession
import org.junit.jupiter.api.Test
import vilkår.uføre.domain.Uføregrad
import vilkår.uføre.domain.Uføregrunnlag

internal class UføregrunnlagPostgresRepoTest {

    @Test
    fun `lagrer uføregrunnlag, kobler til behandling og sletter`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val grunnlagRepo = UføregrunnlagPostgresRepo(testDataHelper.dbMetrics)
            val behandling = testDataHelper.persisterSøknadsbehandlingVilkårsvurdert { (sak, søknad) ->
                nySøknadsbehandlingMedStønadsperiode(
                    sakOgSøknad = sak to søknad,
                ).let {
                    it.first to it.second
                }
            }.second

            val uføregrunnlag1 = Uføregrunnlag(
                periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 30.april(2021)),
                uføregrad = Uføregrad.parse(100),
                forventetInntekt = 0,
                opprettet = fixedTidspunkt,
            )

            val uføregrunnlag2 = Uføregrunnlag(
                periode = Periode.create(fraOgMed = 1.mai(2021), tilOgMed = 31.desember(2021)),
                uføregrad = Uføregrad.parse(80),
                forventetInntekt = 14000,
                opprettet = fixedTidspunkt,
            )

            dataSource.withSession { session ->
                session.transaction { tx ->
                    grunnlagRepo.lagre(behandling.id, listOf(uføregrunnlag1, uføregrunnlag2), tx)
                }
                grunnlagRepo.hentUføregrunnlagForBehandlingId(behandling.id, session) shouldBe listOf(
                    uføregrunnlag1,
                    uføregrunnlag2,
                )
                """
                    select count(*) from grunnlag_uføre
                """.antall(emptyMap(), session) shouldBe 2
            }
        }
    }
}
