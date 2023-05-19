package no.nav.su.se.bakover.database.grunnlag

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.november
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.grunnlag.Utenlandsoppholdgrunnlag
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vurdering
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeUtenlandsopphold
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.dbMetricsStub
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.persistence.withSession
import no.nav.su.se.bakover.test.vilkår.utenlandsoppholdInnvilget
import org.junit.jupiter.api.Test
import java.util.UUID

internal class UtenlandsoppholdVilkårsvurderingPostgresRepoTest {

    @Test
    fun `lagrer og henter vilkårsvurdering uten grunnlag`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val utenlandsoppholdVilkårsvurderingRepo = UtenlandsoppholdVilkårsvurderingPostgresRepo(
                utenlandsoppholdgrunnlagRepo = UtenlandsoppholdgrunnlagPostgresRepo(
                    dbMetrics = dbMetricsStub,
                ),
                dbMetrics = dbMetricsStub,
            )
            val søknadsbehandling = testDataHelper.persisterSøknadsbehandlingVilkårsvurdert().second
            val vilkår = UtenlandsoppholdVilkår.Vurdert.tryCreate(
                vurderingsperioder = nonEmptyListOf(
                    VurderingsperiodeUtenlandsopphold.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        vurdering = Vurdering.Avslag,
                        grunnlag = null,
                        periode = år(2021),
                    ),
                ),
            ).getOrFail()

            testDataHelper.sessionFactory.withTransaction { tx ->
                utenlandsoppholdVilkårsvurderingRepo.lagre(søknadsbehandling.id, vilkår, tx)
            }

            testDataHelper.sessionFactory.withSession { session ->
                utenlandsoppholdVilkårsvurderingRepo.hent(søknadsbehandling.id, session) shouldBe vilkår
            }
        }
    }

    @Test
    fun `lagrer og henter vilkårsvurdering med grunnlag`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val utenlandsoppholdVilkårsvurderingRepo = UtenlandsoppholdVilkårsvurderingPostgresRepo(
                utenlandsoppholdgrunnlagRepo = UtenlandsoppholdgrunnlagPostgresRepo(
                    dbMetrics = dbMetricsStub,
                ),
                dbMetrics = dbMetricsStub,
            )
            val søknadsbehandling = testDataHelper.persisterSøknadsbehandlingVilkårsvurdert().second
            val vilkår = UtenlandsoppholdVilkår.Vurdert.tryCreate(
                vurderingsperioder = nonEmptyListOf(
                    VurderingsperiodeUtenlandsopphold.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        vurdering = Vurdering.Innvilget,
                        grunnlag = Utenlandsoppholdgrunnlag(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            periode = Periode.create(1.januar(2021), 30.november(2021)),
                        ),
                        periode = Periode.create(1.januar(2021), 30.november(2021)),
                    ),
                    VurderingsperiodeUtenlandsopphold.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        vurdering = Vurdering.Avslag,
                        grunnlag = Utenlandsoppholdgrunnlag(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            periode = desember(2021),
                        ),
                        periode = desember(2021),
                    ),
                ),
            ).getOrFail()

            testDataHelper.sessionFactory.withTransaction { tx ->
                utenlandsoppholdVilkårsvurderingRepo.lagre(søknadsbehandling.id, vilkår, tx)
            }

            testDataHelper.sessionFactory.withSession { session ->
                utenlandsoppholdVilkårsvurderingRepo.hent(søknadsbehandling.id, session) shouldBe vilkår
            }
        }
    }

    @Test
    fun `kan erstatte eksisterende vilkårsvurderinger med grunnlag`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val utenlandsoppholdVilkårsvurderingRepo = UtenlandsoppholdVilkårsvurderingPostgresRepo(
                utenlandsoppholdgrunnlagRepo = UtenlandsoppholdgrunnlagPostgresRepo(
                    dbMetrics = dbMetricsStub,
                ),
                dbMetrics = dbMetricsStub,
            )
            val søknadsbehandling = testDataHelper.persisterSøknadsbehandlingVilkårsvurdert().second
            val gammel = UtenlandsoppholdVilkår.Vurdert.tryCreate(
                vurderingsperioder = nonEmptyListOf(
                    VurderingsperiodeUtenlandsopphold.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        vurdering = Vurdering.Avslag,
                        grunnlag = Utenlandsoppholdgrunnlag(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            periode = desember(2021),
                        ),
                        periode = desember(2021),
                    ),
                ),
            ).getOrFail()

            val ny = UtenlandsoppholdVilkår.Vurdert.tryCreate(
                vurderingsperioder = nonEmptyListOf(
                    VurderingsperiodeUtenlandsopphold.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        vurdering = Vurdering.Innvilget,
                        grunnlag = Utenlandsoppholdgrunnlag(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            periode = Periode.create(1.januar(2021), 30.november(2021)),
                        ),
                        periode = Periode.create(1.januar(2021), 30.november(2021)),
                    ),
                ),
            ).getOrFail()

            testDataHelper.sessionFactory.withTransaction { tx ->
                utenlandsoppholdVilkårsvurderingRepo.lagre(søknadsbehandling.id, gammel, tx)
            }

            testDataHelper.sessionFactory.withSession { session ->
                utenlandsoppholdVilkårsvurderingRepo.hent(søknadsbehandling.id, session) shouldBe gammel
            }

            testDataHelper.sessionFactory.withTransaction { tx ->
                utenlandsoppholdVilkårsvurderingRepo.lagre(søknadsbehandling.id, ny, tx)
            }

            testDataHelper.sessionFactory.withSession { session ->
                utenlandsoppholdVilkårsvurderingRepo.hent(søknadsbehandling.id, session) shouldBe ny
            }
        }
    }

    @Test
    fun `sletter grunnlag hvis vurdering går fra vurdert til ikke vurdert`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val utenlandsoppholdgrunnlagRepo = UtenlandsoppholdgrunnlagPostgresRepo(
                dbMetrics = dbMetricsStub,
            )
            val utenlandsoppholdVilkårsvurderingRepo = UtenlandsoppholdVilkårsvurderingPostgresRepo(
                utenlandsoppholdgrunnlagRepo = utenlandsoppholdgrunnlagRepo,
                dbMetrics = dbMetricsStub,
            )
            val søknadsbehandling = testDataHelper.persisterSøknadsbehandlingVilkårsvurdert().second
            val (vilkår, grunnlag) = utenlandsoppholdInnvilget(
                grunnlag = Utenlandsoppholdgrunnlag(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    periode = år(2021),
                ),
            ).let { it to it.grunnlag }

            testDataHelper.sessionFactory.withTransaction { tx ->
                utenlandsoppholdVilkårsvurderingRepo.lagre(søknadsbehandling.id, vilkår, tx)
            }

            testDataHelper.sessionFactory.withSession { session ->
                utenlandsoppholdVilkårsvurderingRepo.hent(søknadsbehandling.id, session) shouldBe vilkår
            }

            testDataHelper.sessionFactory.withTransaction { tx ->
                utenlandsoppholdVilkårsvurderingRepo.lagre(
                    søknadsbehandling.id,
                    UtenlandsoppholdVilkår.IkkeVurdert,
                    tx,
                )
            }
            dataSource.withSession { session ->
                utenlandsoppholdVilkårsvurderingRepo.hent(
                    behandlingId = søknadsbehandling.id,
                    session = session,
                ) shouldBe UtenlandsoppholdVilkår.IkkeVurdert

                utenlandsoppholdgrunnlagRepo.hent(
                    id = grunnlag.first().id,
                    session = session,
                ) shouldBe null
            }
        }
    }
}
