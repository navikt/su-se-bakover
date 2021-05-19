package no.nav.su.se.bakover.database.grunnlag

import arrow.core.Nel
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.fixedClock
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import org.junit.jupiter.api.Test
import java.util.UUID

internal class VilkårsvurderingPostgresRepoTest {
    private val datasource = EmbeddedDatabase.instance()
    private val testDataHelper = TestDataHelper(datasource)

    @Test
    fun `lagrer og henter vilkårsvurdering uten grunnlag`() {
        withMigratedDb {
            val søknadsbehandling = testDataHelper.nySøknadsbehandling()
            val vurderingUførhet = Vilkår.Vurdert.Uførhet.create(
                vurderingsperioder = Nel.of(
                    Vurderingsperiode.Uføre.create(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(fixedClock),
                        resultat = Resultat.Avslag,
                        grunnlag = null,
                        periode = Periode.create(1.januar(2021), 31.desember(2021)),
                        begrunnelse = "fåkke lov",
                    ),
                ),
            )

            testDataHelper.vilkårsvurderingRepo.lagre(søknadsbehandling.id, vurderingUførhet)

            datasource.withSession { session ->
                testDataHelper.vilkårsvurderingRepo.hent(søknadsbehandling.id, session) shouldBe vurderingUførhet
            }
        }
    }

    @Test
    fun `lagrer og henter vilkårsvurdering med grunnlag`() {
        withMigratedDb {
            val søknadsbehandling = testDataHelper.nySøknadsbehandling()
            val uføregrunnlag = Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(fixedClock),
                periode = Periode.create(1.januar(2021), 31.desember(2021)),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 12000,
            )

            val vurderingUførhet = Vilkår.Vurdert.Uførhet.create(
                vurderingsperioder = Nel.of(
                    Vurderingsperiode.Uføre.create(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(fixedClock),
                        resultat = Resultat.Avslag,
                        grunnlag = uføregrunnlag,
                        periode = Periode.create(1.januar(2021), 31.desember(2021)),
                        begrunnelse = "fåkke lov",
                    ),
                ),
            )

            testDataHelper.vilkårsvurderingRepo.lagre(søknadsbehandling.id, vurderingUførhet)

            datasource.withSession { session ->
                testDataHelper.vilkårsvurderingRepo.hent(søknadsbehandling.id, session) shouldBe vurderingUførhet
            }
        }
    }

    @Test
    fun `kan erstatte eksisterende vilkårsvurderinger med grunnlag`() {
        withMigratedDb {
            val søknadsbehandling = testDataHelper.nySøknadsbehandling()
            val uføregrunnlag = Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(fixedClock),
                periode = Periode.create(1.januar(2021), 31.desember(2021)),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 12000,
            )

            val vurderingUførhet = Vilkår.Vurdert.Uførhet.create(
                vurderingsperioder = Nel.of(
                    Vurderingsperiode.Uføre.create(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(fixedClock),
                        resultat = Resultat.Avslag,
                        grunnlag = uføregrunnlag,
                        periode = Periode.create(1.januar(2021), 31.desember(2021)),
                        begrunnelse = "fåkke lov",
                    ),
                ),
            )

            testDataHelper.vilkårsvurderingRepo.lagre(søknadsbehandling.id, vurderingUførhet)

            datasource.withSession { session ->
                testDataHelper.vilkårsvurderingRepo.hent(søknadsbehandling.id, session) shouldBe vurderingUførhet
            }

            testDataHelper.vilkårsvurderingRepo.lagre(søknadsbehandling.id, vurderingUførhet)

            datasource.withSession { session ->
                testDataHelper.vilkårsvurderingRepo.hent(søknadsbehandling.id, session) shouldBe vurderingUførhet
            }
        }
    }
}
