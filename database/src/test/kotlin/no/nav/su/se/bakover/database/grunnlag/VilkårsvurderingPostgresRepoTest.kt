package no.nav.su.se.bakover.database.grunnlag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.fixedClock
import no.nav.su.se.bakover.database.withMigratedDb
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
            val søknadsbehandling = testDataHelper.nyUavklartVilkårsvurdering()
            val vurderingUførhet = Vilkår.Vurdert.Uførhet(
                vurdering = listOf(
                    Vurderingsperiode.Manuell(
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

            testDataHelper.vilkårsvurderingRepo.hent(søknadsbehandling.id) shouldBe vurderingUførhet
        }
    }

    @Test
    fun `lagrer og henter vilkårsvurdering med grunnlag`() {
        withMigratedDb {
            val søknadsbehandling = testDataHelper.nyUavklartVilkårsvurdering()
            val uføregrunnlag = Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(fixedClock),
                periode = Periode.create(1.januar(2021), 31.desember(2021)),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 12000,
            )
            testDataHelper.grunnlagRepo.lagre(søknadsbehandling.id, listOf(uføregrunnlag))

            val vurderingUførhet = Vilkår.Vurdert.Uførhet(
                vurdering = listOf(
                    Vurderingsperiode.Manuell(
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

            testDataHelper.vilkårsvurderingRepo.hent(søknadsbehandling.id) shouldBe vurderingUførhet
        }
    }

    @Test
    fun `sletter vilkårsvurderinger dersom tilknyttet grunnlag slettes`() {
        withMigratedDb {
            val søknadsbehandling = testDataHelper.nyUavklartVilkårsvurdering()
            val uføregrunnlag = Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(fixedClock),
                periode = Periode.create(1.januar(2021), 31.desember(2021)),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 12000,
            )
            testDataHelper.grunnlagRepo.lagre(søknadsbehandling.id, listOf(uføregrunnlag))

            val vurderingUførhet = Vilkår.Vurdert.Uførhet(
                vurdering = listOf(
                    Vurderingsperiode.Manuell(
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

            testDataHelper.vilkårsvurderingRepo.hent(søknadsbehandling.id) shouldBe vurderingUførhet

            testDataHelper.grunnlagRepo.slett(uføregrunnlag.id)

            testDataHelper.vilkårsvurderingRepo.hent(søknadsbehandling.id) shouldBe Vilkår.IkkeVurdertUføregrunnlag
        }
    }
}
