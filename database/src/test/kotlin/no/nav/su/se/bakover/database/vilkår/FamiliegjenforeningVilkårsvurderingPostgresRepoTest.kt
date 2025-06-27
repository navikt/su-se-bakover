package no.nav.su.se.bakover.database.vilkår

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.database.grunnlag.FamiliegjenforeningVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.FnrWrapper
import no.nav.su.se.bakover.domain.vilkår.familiegjenforening
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.dbMetricsStub
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.persistence.withTransaction
import no.nav.su.se.bakover.test.søknad.søknadsinnholdAlder
import no.nav.su.se.bakover.test.vilkår.familiegjenforeningVilkårInnvilget
import no.nav.su.se.bakover.test.vilkårsvurdertSøknadsbehandling
import org.junit.jupiter.api.Test

internal class FamiliegjenforeningVilkårsvurderingPostgresRepoTest {

    @Test
    fun `lagrer og henter familiegjenforeningvilkår`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val familiegjenforeningVilkårsvurderingPostgresRepo = FamiliegjenforeningVilkårsvurderingPostgresRepo(
                dbMetrics = dbMetricsStub,
            )
            val søknadsbehandling = testDataHelper.persisternySøknadsbehandlingMedStønadsperiode().second
            val familiegjenforeningVilkår = familiegjenforeningVilkårInnvilget()

            dataSource.withTransaction { session ->
                familiegjenforeningVilkårsvurderingPostgresRepo.lagre(
                    søknadsbehandling.id,
                    familiegjenforeningVilkår,
                    session,
                )
                familiegjenforeningVilkårsvurderingPostgresRepo.hent(
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
            val familiegjenforeningVilkårsvurderingPostgresRepo = FamiliegjenforeningVilkårsvurderingPostgresRepo(
                dbMetrics = dbMetricsStub,
            )
            val fnr = Fnr.generer()
            val (_, søknadsbehandling) = testDataHelper.persisterSøknadsbehandlingVilkårsvurdert(
                sakOgSøknad = testDataHelper.persisterJournalførtSøknadMedOppgave(
                    fnr = fnr,
                    søknadInnhold = søknadsinnholdAlder(fnrWrapper = FnrWrapper(fnr)),
                ),
            ) { (sak, søknad) ->
                vilkårsvurdertSøknadsbehandling(
                    sakOgSøknad = sak to søknad,
                )
            }

            val familiegjenforeningVilkår = familiegjenforeningVilkårInnvilget()

            dataSource.withTransaction { session ->
                familiegjenforeningVilkårsvurderingPostgresRepo.lagre(
                    søknadsbehandling.id,
                    søknadsbehandling.vilkårsvurderinger.familiegjenforening().getOrFail(),
                    session,
                )
                familiegjenforeningVilkårsvurderingPostgresRepo.hent(
                    søknadsbehandling.id,
                    session,
                ) shouldBe søknadsbehandling.vilkårsvurderinger.familiegjenforening().getOrFail()

                familiegjenforeningVilkårsvurderingPostgresRepo.lagre(
                    søknadsbehandling.id,
                    familiegjenforeningVilkår,
                    session,
                )
                familiegjenforeningVilkårsvurderingPostgresRepo.hent(
                    søknadsbehandling.id,
                    session,
                ) shouldBe familiegjenforeningVilkår
            }
        }
    }
}
