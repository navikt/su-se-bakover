package no.nav.su.se.bakover.database.vilkår

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.grunnlag.LovligOppholdVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.LovligOppholdgrunnlagPostgresRepo
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.dbMetricsStub
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.persistence.withTransaction
import no.nav.su.se.bakover.test.vilkår.lovligOppholdVilkårInnvilget
import org.junit.jupiter.api.Test

internal class LovligOppholdVilkårsvurderingPostgresRepoTest {
    @Test
    fun `lagrer og henter lovligoppholdVilkår`() {
        val lovligOppholdVilkårsvurderingPostgresRepo = LovligOppholdVilkårsvurderingPostgresRepo(
            dbMetrics = dbMetricsStub,
            lovligOppholdGrunnlagPostgresRepo = LovligOppholdgrunnlagPostgresRepo(
                dbMetrics = dbMetricsStub,
            ),
        )
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val søknadsbehandling = testDataHelper.persisterSøknadsbehandlingVilkårsvurdertUavklart().second
            val lovligOppholdVilkår = lovligOppholdVilkårInnvilget()

            dataSource.withTransaction { session ->
                lovligOppholdVilkårsvurderingPostgresRepo.lagre(
                    søknadsbehandling.id,
                    lovligOppholdVilkår,
                    session,
                )
                lovligOppholdVilkårsvurderingPostgresRepo.hent(
                    søknadsbehandling.id,
                    session,
                ) shouldBe lovligOppholdVilkår
            }
        }
    }

    @Test
    fun `erstatter gammel vilkår med ny`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val lovligOppholdVilkårsvurderingPostgresRepo = LovligOppholdVilkårsvurderingPostgresRepo(
                dbMetrics = dbMetricsStub,
                lovligOppholdGrunnlagPostgresRepo = LovligOppholdgrunnlagPostgresRepo(
                    dbMetrics = dbMetricsStub,
                ),
            )
            val søknadsbehandling = testDataHelper.persisterSøknadsbehandlingVilkårsvurdertInnvilget().second
            val lovligOppholdVilkår = lovligOppholdVilkårInnvilget()

            dataSource.withTransaction { session ->
                lovligOppholdVilkårsvurderingPostgresRepo.lagre(
                    søknadsbehandling.id,
                    lovligOppholdVilkår,
                    session,
                )
                lovligOppholdVilkårsvurderingPostgresRepo.hent(
                    søknadsbehandling.id,
                    session,
                ) shouldBe lovligOppholdVilkår

                lovligOppholdVilkårsvurderingPostgresRepo.lagre(
                    søknadsbehandling.id,
                    lovligOppholdVilkår,
                    session,
                )
                lovligOppholdVilkårsvurderingPostgresRepo.hent(
                    søknadsbehandling.id,
                    session,
                ) shouldBe lovligOppholdVilkår
            }
        }
    }
}
