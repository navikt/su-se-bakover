package no.nav.su.se.bakover.database.grunnlag

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.november
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.grunnlag.OppholdIUtlandetGrunnlag
import no.nav.su.se.bakover.domain.vilkår.OppholdIUtlandetVilkår
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeOppholdIUtlandet
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import org.junit.jupiter.api.Test
import java.util.UUID

internal class UtlandsoppholdVilkårsvurderingPostgresRepoTest {

    @Test
    fun `lagrer og henter vilkårsvurdering uten grunnlag`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val søknadsbehandling = testDataHelper.nySøknadsbehandling()
            val vilkår = OppholdIUtlandetVilkår.Vurdert.tryCreate(
                vurderingsperioder = nonEmptyListOf(
                    VurderingsperiodeOppholdIUtlandet.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        resultat = Resultat.Avslag,
                        grunnlag = null,
                        periode = Periode.create(1.januar(2021), 31.desember(2021)),
                        begrunnelse = "fåkke lov",
                    ),
                ),
            ).getOrFail()

            testDataHelper.sessionFactory.withTransaction { tx ->
                testDataHelper.utlandsoppholdVilkårsvurderingRepo.lagre(søknadsbehandling.id, vilkår, tx)
            }

            testDataHelper.sessionFactory.withSession { session ->
                testDataHelper.utlandsoppholdVilkårsvurderingRepo.hent(søknadsbehandling.id, session) shouldBe vilkår
            }
        }
    }

    @Test
    fun `lagrer og henter vilkårsvurdering med grunnlag`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val søknadsbehandling = testDataHelper.nySøknadsbehandling()
            val vilkår = OppholdIUtlandetVilkår.Vurdert.tryCreate(
                vurderingsperioder = nonEmptyListOf(
                    VurderingsperiodeOppholdIUtlandet.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        resultat = Resultat.Innvilget,
                        grunnlag = OppholdIUtlandetGrunnlag(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            periode = Periode.create(1.januar(2021), 30.november(2021)),
                        ),
                        periode = Periode.create(1.januar(2021), 30.november(2021)),
                        begrunnelse = "fåkke lov",
                    ),
                    VurderingsperiodeOppholdIUtlandet.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        resultat = Resultat.Avslag,
                        grunnlag = OppholdIUtlandetGrunnlag(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            periode = Periode.create(1.desember(2021), 31.desember(2021)),
                        ),
                        periode = Periode.create(1.desember(2021), 31.desember(2021)),
                        begrunnelse = "fåkke lov",
                    ),
                ),
            ).getOrFail()

            testDataHelper.sessionFactory.withTransaction { tx ->
                testDataHelper.utlandsoppholdVilkårsvurderingRepo.lagre(søknadsbehandling.id, vilkår, tx)
            }

            testDataHelper.sessionFactory.withSession { session ->
                testDataHelper.utlandsoppholdVilkårsvurderingRepo.hent(søknadsbehandling.id, session) shouldBe vilkår
            }
        }
    }

    @Test
    fun `kan erstatte eksisterende vilkårsvurderinger med grunnlag`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val søknadsbehandling = testDataHelper.nySøknadsbehandling()
            val gammel = OppholdIUtlandetVilkår.Vurdert.tryCreate(
                vurderingsperioder = nonEmptyListOf(
                    VurderingsperiodeOppholdIUtlandet.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        resultat = Resultat.Avslag,
                        grunnlag = OppholdIUtlandetGrunnlag(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            periode = Periode.create(1.desember(2021), 31.desember(2021)),
                        ),
                        periode = Periode.create(1.desember(2021), 31.desember(2021)),
                        begrunnelse = "fåkke lov",
                    ),
                ),
            ).getOrFail()

            val ny = OppholdIUtlandetVilkår.Vurdert.tryCreate(
                vurderingsperioder = nonEmptyListOf(
                    VurderingsperiodeOppholdIUtlandet.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        resultat = Resultat.Innvilget,
                        grunnlag = OppholdIUtlandetGrunnlag(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            periode = Periode.create(1.januar(2021), 30.november(2021)),
                        ),
                        periode = Periode.create(1.januar(2021), 30.november(2021)),
                        begrunnelse = "fåkke lov",
                    ),
                ),
            ).getOrFail()

            testDataHelper.sessionFactory.withTransaction { tx ->
                testDataHelper.utlandsoppholdVilkårsvurderingRepo.lagre(søknadsbehandling.id, gammel, tx)
            }

            testDataHelper.sessionFactory.withSession { session ->
                testDataHelper.utlandsoppholdVilkårsvurderingRepo.hent(søknadsbehandling.id, session) shouldBe gammel
            }

            testDataHelper.sessionFactory.withTransaction { tx ->
                testDataHelper.utlandsoppholdVilkårsvurderingRepo.lagre(søknadsbehandling.id, ny, tx)
            }

            testDataHelper.sessionFactory.withSession { session ->
                testDataHelper.utlandsoppholdVilkårsvurderingRepo.hent(søknadsbehandling.id, session) shouldBe ny
            }
        }
    }
}
