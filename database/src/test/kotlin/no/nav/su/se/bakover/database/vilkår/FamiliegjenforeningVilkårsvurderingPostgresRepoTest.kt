package no.nav.su.se.bakover.database.vilkår

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withTransaction
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.vilkår.familiegjenforeningVilkårInnvilget
import no.nav.su.se.bakover.test.vilkårsvurderingSøknadsbehandlingVurdertInnvilgetAlder
import org.junit.jupiter.api.Test

internal class FamiliegjenforeningVilkårsvurderingPostgresRepoTest {

    @Test
    fun `lagrer og henter familiegjenforeningvilkår`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val søknadsbehandling = testDataHelper.persisterSøknadsbehandlingVilkårsvurdertUavklart().second
            val familiegjenforeningVilkår = familiegjenforeningVilkårInnvilget()

            dataSource.withTransaction { session ->
                testDataHelper.familiegjenforeningVilkårsvurderingPostgresRepo.lagre(
                    søknadsbehandling.id,
                    familiegjenforeningVilkår,
                    session,
                )
                testDataHelper.familiegjenforeningVilkårsvurderingPostgresRepo.hent(
                    søknadsbehandling.id,
                    session,
                ) shouldBe familiegjenforeningVilkår
            }
        }
    }

    @Test
    fun `erstatter gammel vilkår med ny`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val søknadsbehandling =
                testDataHelper.persisterSøknadsbehandlingVilkårsvurdertInnvilget(vilkårsvurderinger = vilkårsvurderingSøknadsbehandlingVurdertInnvilgetAlder()).second
            val familiegjenforeningVilkår = familiegjenforeningVilkårInnvilget()

            dataSource.withTransaction { session ->
                testDataHelper.familiegjenforeningVilkårsvurderingPostgresRepo.lagre(
                    søknadsbehandling.id,
                    søknadsbehandling.vilkårsvurderinger.familiegjenforening().getOrFail(),
                    session,
                )
                testDataHelper.familiegjenforeningVilkårsvurderingPostgresRepo.hent(
                    søknadsbehandling.id,
                    session,
                ) shouldBe søknadsbehandling.vilkårsvurderinger.familiegjenforening().getOrFail()

                testDataHelper.familiegjenforeningVilkårsvurderingPostgresRepo.lagre(
                    søknadsbehandling.id,
                    familiegjenforeningVilkår,
                    session,
                )
                testDataHelper.familiegjenforeningVilkårsvurderingPostgresRepo.hent(
                    søknadsbehandling.id,
                    session,
                ) shouldBe familiegjenforeningVilkår
            }
        }
    }
}
