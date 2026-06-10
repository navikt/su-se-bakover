package no.nav.su.se.bakover.database.vilkår

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.grunnlag.LovligOppholdVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.LovligOppholdgrunnlagPostgresRepo
import no.nav.su.se.bakover.test.persistence.DbExtension
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.dbMetricsStub
import no.nav.su.se.bakover.test.persistence.withTransaction
import no.nav.su.se.bakover.test.vilkår.lovligOppholdVilkårInnvilget
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import javax.sql.DataSource

@ExtendWith(DbExtension::class)
internal class LovligOppholdVilkårsvurderingPostgresRepoTest(private val dataSource: DataSource) {
    @Test
    fun `lagrer og henter lovligoppholdVilkår`() {
        val lovligOppholdVilkårsvurderingPostgresRepo = LovligOppholdVilkårsvurderingPostgresRepo(
            dbMetrics = dbMetricsStub,
            lovligOppholdGrunnlagPostgresRepo = LovligOppholdgrunnlagPostgresRepo(
                dbMetrics = dbMetricsStub,
            ),
        )
        val testDataHelper = TestDataHelper(dataSource)
        val søknadsbehandling = testDataHelper.persisterSøknadsbehandlingVilkårsvurdert().second
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

    @Test
    fun `erstatter gammel vilkår med ny`() {
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
