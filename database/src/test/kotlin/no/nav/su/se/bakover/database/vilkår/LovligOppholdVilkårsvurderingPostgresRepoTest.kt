package no.nav.su.se.bakover.database.vilkår

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withTransaction
import no.nav.su.se.bakover.test.vilkår.lovligOppholdVilkårInnvilget
import org.junit.jupiter.api.Test

internal class LovligOppholdVilkårsvurderingPostgresRepoTest {
    @Test
    fun `lagrer og henter lovligoppholdVilkår`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val søknadsbehandling = testDataHelper.persisterSøknadsbehandlingVilkårsvurdertUavklart().second
            val lovligOppholdVilkår = lovligOppholdVilkårInnvilget()

            dataSource.withTransaction { session ->
                testDataHelper.lovligOppholdVilkårsvurderingPostgresRepo.lagre(
                    søknadsbehandling.id,
                    lovligOppholdVilkår,
                    session,
                )
                testDataHelper.lovligOppholdVilkårsvurderingPostgresRepo.hent(
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
            val søknadsbehandling = testDataHelper.persisterSøknadsbehandlingVilkårsvurdertInnvilget().second
            val lovligOppholdVilkår = lovligOppholdVilkårInnvilget()

            dataSource.withTransaction { session ->
                testDataHelper.lovligOppholdVilkårsvurderingPostgresRepo.lagre(
                    søknadsbehandling.id,
                    lovligOppholdVilkår,
                    session,
                )
                testDataHelper.lovligOppholdVilkårsvurderingPostgresRepo.hent(
                    søknadsbehandling.id,
                    session,
                ) shouldBe lovligOppholdVilkår

                testDataHelper.lovligOppholdVilkårsvurderingPostgresRepo.lagre(
                    søknadsbehandling.id,
                    lovligOppholdVilkår,
                    session,
                )
                testDataHelper.lovligOppholdVilkårsvurderingPostgresRepo.hent(
                    søknadsbehandling.id,
                    session,
                ) shouldBe lovligOppholdVilkår
            }
        }
    }
}
