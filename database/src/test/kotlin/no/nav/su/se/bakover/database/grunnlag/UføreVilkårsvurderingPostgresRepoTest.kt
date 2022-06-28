package no.nav.su.se.bakover.database.grunnlag

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withTransaction
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.vilkår.UføreVilkår
import no.nav.su.se.bakover.domain.vilkår.Vurdering
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeUføre
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.vilkårsvurderinger.innvilgetUførevilkår
import org.junit.jupiter.api.Test
import java.util.UUID

internal class UføreVilkårsvurderingPostgresRepoTest {

    @Test
    fun `lagrer og henter vilkårsvurdering uten grunnlag`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val søknadsbehandling = testDataHelper.persisterSøknadsbehandlingVilkårsvurdertUavklart().second
            val vurderingUførhet = UføreVilkår.Vurdert.create(
                vurderingsperioder = nonEmptyListOf(
                    VurderingsperiodeUføre.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        vurdering = Vurdering.Avslag,
                        grunnlag = null,
                        periode = år(2021),
                    ),
                ),
            )

            dataSource.withTransaction { session ->
                testDataHelper.uføreVilkårsvurderingRepo.lagre(søknadsbehandling.id, vurderingUførhet, session)
                testDataHelper.uføreVilkårsvurderingRepo.hent(søknadsbehandling.id, session) shouldBe vurderingUførhet
            }
        }
    }

    @Test
    fun `lagrer og henter vilkårsvurdering med grunnlag`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val søknadsbehandling = testDataHelper.persisterSøknadsbehandlingVilkårsvurdertUavklart().second
            val uføregrunnlag = Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = år(2021),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 12000,
            )

            val vurderingUførhet = UføreVilkår.Vurdert.create(
                vurderingsperioder = nonEmptyListOf(
                    VurderingsperiodeUføre.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        vurdering = Vurdering.Avslag,
                        grunnlag = uføregrunnlag,
                        periode = år(2021),
                    ),
                ),
            )

            dataSource.withTransaction { session ->
                testDataHelper.uføreVilkårsvurderingRepo.lagre(søknadsbehandling.id, vurderingUførhet, session)
                testDataHelper.uføreVilkårsvurderingRepo.hent(søknadsbehandling.id, session) shouldBe vurderingUførhet
            }
        }
    }

    @Test
    fun `kan erstatte eksisterende vilkårsvurderinger med grunnlag`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val søknadsbehandling = testDataHelper.persisterSøknadsbehandlingVilkårsvurdertUavklart().second
            val uføregrunnlag = Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = år(2021),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 12000,
            )

            val vurderingUførhet = UføreVilkår.Vurdert.create(
                vurderingsperioder = nonEmptyListOf(
                    VurderingsperiodeUføre.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        vurdering = Vurdering.Avslag,
                        grunnlag = uføregrunnlag,
                        periode = år(2021),
                    ),
                ),
            )

            dataSource.withTransaction { session ->
                testDataHelper.uføreVilkårsvurderingRepo.lagre(søknadsbehandling.id, vurderingUførhet, session)
                testDataHelper.uføreVilkårsvurderingRepo.hent(søknadsbehandling.id, session) shouldBe vurderingUførhet
                testDataHelper.uføreVilkårsvurderingRepo.lagre(søknadsbehandling.id, vurderingUførhet, session)
                testDataHelper.uføreVilkårsvurderingRepo.hent(søknadsbehandling.id, session) shouldBe vurderingUførhet
            }
        }
    }

    @Test
    fun `sletter grunnlag hvis vurdering går fra vurdert til ikke vurdert`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val søknadsbehandling = testDataHelper.persisterSøknadsbehandlingVilkårsvurdertUavklart().second
            val (vilkår, grunnlag) = innvilgetUførevilkår().let { it to it.grunnlag }

            dataSource.withTransaction { session ->
                testDataHelper.uføreVilkårsvurderingRepo.lagre(søknadsbehandling.id, vilkår, session)
                testDataHelper.uføreVilkårsvurderingRepo.hent(søknadsbehandling.id, session) shouldBe vilkår
                testDataHelper.uføreVilkårsvurderingRepo.lagre(
                    behandlingId = søknadsbehandling.id,
                    vilkår = UføreVilkår.IkkeVurdert,
                    tx = session,
                )
                testDataHelper.uføreVilkårsvurderingRepo.hent(
                    behandlingId = søknadsbehandling.id,
                    session = session,
                ) shouldBe UføreVilkår.IkkeVurdert

                testDataHelper.uføregrunnlagPostgresRepo.hentForUføregrunnlagForId(
                    uføregrunnlagId = grunnlag.first().id,
                    session = session,
                ) shouldBe null
            }
        }
    }
}
