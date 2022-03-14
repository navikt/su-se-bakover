package no.nav.su.se.bakover.database.grunnlag

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.november
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.grunnlag.Utenlandsoppholdgrunnlag
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeUtenlandsopphold
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.periode2021
import no.nav.su.se.bakover.test.utlandsoppholdInnvilget
import org.junit.jupiter.api.Test
import java.util.UUID

internal class UtenlandsoppholdVilkårsvurderingPostgresRepoTest {

    @Test
    fun `lagrer og henter vilkårsvurdering uten grunnlag`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val søknadsbehandling = testDataHelper.persisterSøknadsbehandlingVilkårsvurdertUavklart().second
            val vilkår = UtenlandsoppholdVilkår.Vurdert.tryCreate(
                vurderingsperioder = nonEmptyListOf(
                    VurderingsperiodeUtenlandsopphold.create(
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
                testDataHelper.utenlandsoppholdVilkårsvurderingRepo.lagre(søknadsbehandling.id, vilkår, tx)
            }

            testDataHelper.sessionFactory.withSession { session ->
                testDataHelper.utenlandsoppholdVilkårsvurderingRepo.hent(søknadsbehandling.id, session) shouldBe vilkår
            }
        }
    }

    @Test
    fun `lagrer og henter vilkårsvurdering med grunnlag`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val søknadsbehandling = testDataHelper.persisterSøknadsbehandlingVilkårsvurdertUavklart().second
            val vilkår = UtenlandsoppholdVilkår.Vurdert.tryCreate(
                vurderingsperioder = nonEmptyListOf(
                    VurderingsperiodeUtenlandsopphold.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        resultat = Resultat.Innvilget,
                        grunnlag = Utenlandsoppholdgrunnlag(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            periode = Periode.create(1.januar(2021), 30.november(2021)),
                        ),
                        periode = Periode.create(1.januar(2021), 30.november(2021)),
                        begrunnelse = "fåkke lov",
                    ),
                    VurderingsperiodeUtenlandsopphold.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        resultat = Resultat.Avslag,
                        grunnlag = Utenlandsoppholdgrunnlag(
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
                testDataHelper.utenlandsoppholdVilkårsvurderingRepo.lagre(søknadsbehandling.id, vilkår, tx)
            }

            testDataHelper.sessionFactory.withSession { session ->
                testDataHelper.utenlandsoppholdVilkårsvurderingRepo.hent(søknadsbehandling.id, session) shouldBe vilkår
            }
        }
    }

    @Test
    fun `kan erstatte eksisterende vilkårsvurderinger med grunnlag`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val søknadsbehandling = testDataHelper.persisterSøknadsbehandlingVilkårsvurdertUavklart().second
            val gammel = UtenlandsoppholdVilkår.Vurdert.tryCreate(
                vurderingsperioder = nonEmptyListOf(
                    VurderingsperiodeUtenlandsopphold.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        resultat = Resultat.Avslag,
                        grunnlag = Utenlandsoppholdgrunnlag(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            periode = Periode.create(1.desember(2021), 31.desember(2021)),
                        ),
                        periode = Periode.create(1.desember(2021), 31.desember(2021)),
                        begrunnelse = "fåkke lov",
                    ),
                ),
            ).getOrFail()

            val ny = UtenlandsoppholdVilkår.Vurdert.tryCreate(
                vurderingsperioder = nonEmptyListOf(
                    VurderingsperiodeUtenlandsopphold.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        resultat = Resultat.Innvilget,
                        grunnlag = Utenlandsoppholdgrunnlag(
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
                testDataHelper.utenlandsoppholdVilkårsvurderingRepo.lagre(søknadsbehandling.id, gammel, tx)
            }

            testDataHelper.sessionFactory.withSession { session ->
                testDataHelper.utenlandsoppholdVilkårsvurderingRepo.hent(søknadsbehandling.id, session) shouldBe gammel
            }

            testDataHelper.sessionFactory.withTransaction { tx ->
                testDataHelper.utenlandsoppholdVilkårsvurderingRepo.lagre(søknadsbehandling.id, ny, tx)
            }

            testDataHelper.sessionFactory.withSession { session ->
                testDataHelper.utenlandsoppholdVilkårsvurderingRepo.hent(søknadsbehandling.id, session) shouldBe ny
            }
        }
    }

    @Test
    fun `sletter grunnlag hvis vurdering går fra vurdert til ikke vurdert`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val søknadsbehandling = testDataHelper.persisterSøknadsbehandlingVilkårsvurdertUavklart().second
            val (vilkår, grunnlag) = utlandsoppholdInnvilget(
                grunnlag = Utenlandsoppholdgrunnlag(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    periode = periode2021,
                ),
            ).let { it to it.grunnlag }

            testDataHelper.sessionFactory.withTransaction { tx ->
                testDataHelper.utenlandsoppholdVilkårsvurderingRepo.lagre(søknadsbehandling.id, vilkår, tx)
            }

            testDataHelper.sessionFactory.withSession { session ->
                testDataHelper.utenlandsoppholdVilkårsvurderingRepo.hent(søknadsbehandling.id, session) shouldBe vilkår
            }

            testDataHelper.sessionFactory.withTransaction { tx ->
                testDataHelper.utenlandsoppholdVilkårsvurderingRepo.lagre(
                    søknadsbehandling.id,
                    UtenlandsoppholdVilkår.IkkeVurdert,
                    tx,
                )
            }
            dataSource.withSession { session ->
                testDataHelper.utenlandsoppholdVilkårsvurderingRepo.hent(
                    behandlingId = søknadsbehandling.id,
                    session = session,
                ) shouldBe UtenlandsoppholdVilkår.IkkeVurdert

                testDataHelper.utenlandsoppholdgrunnlagPostgresRepo.hent(
                    id = grunnlag.first().id,
                    session = session,
                ) shouldBe null
            }
        }
    }
}
