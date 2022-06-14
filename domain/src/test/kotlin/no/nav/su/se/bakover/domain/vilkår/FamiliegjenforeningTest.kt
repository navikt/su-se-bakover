package no.nav.su.se.bakover.domain.vilkår

import arrow.core.nonEmptyListOf
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.test.vilkår.familiegjenforeningVilkår
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import vurderingsperiodeFamiliegjenforening

private class FamiliegjenforeningTest {

    @Test
    fun `vurdert og ikke vurdert skal ikke være lik`() {
    }

    @Nested
    inner class IkkeVurdert {

        @Test
        fun `resultat skal være uavklart`() {
            FamiliegjenforeningVilkår.IkkeVurdert.resultat shouldBe Resultat.Uavklart
        }

        @Test
        fun `vilkår skal ikke være avslag`() {
            FamiliegjenforeningVilkår.IkkeVurdert.erAvslag shouldBe false
        }

        @Test
        fun `vilkår skal ikke være innvilget`() {
            FamiliegjenforeningVilkår.IkkeVurdert.erInnvilget shouldBe false
        }

        @Test
        fun `tidligstdato skal være null`() {
            FamiliegjenforeningVilkår.IkkeVurdert.hentTidligesteDatoForAvslag() shouldBe null
        }

        @Test
        fun `2 Ikkevurderte skal være lik`() {
            FamiliegjenforeningVilkår.IkkeVurdert.erLik(FamiliegjenforeningVilkår.IkkeVurdert) shouldBe true
        }

        @Test
        fun `lager tidslinje av seg selv`() {
            val familiegjenforeningVilkår = FamiliegjenforeningVilkår.IkkeVurdert
            familiegjenforeningVilkår.lagTidslinje(år(2021)) shouldBe familiegjenforeningVilkår
        }

        @Test
        fun `er seg selv når man prøver å slå sammen`() {
            val familiegjenforeningVilkår = FamiliegjenforeningVilkår.IkkeVurdert
            familiegjenforeningVilkår.slåSammenLikePerioder() shouldBe familiegjenforeningVilkår
        }
    }

    @Nested
    inner class Vurdert {

        @Test
        fun `er innvilget dersom alle perioder er innvilget`() {
            familiegjenforeningVilkår(
                nonEmptyListOf(
                    vurderingsperiodeFamiliegjenforening(),
                    vurderingsperiodeFamiliegjenforening(periode = år(2023)),
                ),
            ).erInnvilget shouldBe true
        }

        @Test
        fun `er avslag dersom minst en periode er avslag`() {
            familiegjenforeningVilkår(
                nonEmptyListOf(
                    vurderingsperiodeFamiliegjenforening(),
                    vurderingsperiodeFamiliegjenforening(resultat = Resultat.Avslag, periode = år(2023)),
                ),
            ).erAvslag shouldBe true
        }

        @Test
        fun `er lik dersom begge er vurdert & vurderingsperiodene er lik`() {
            familiegjenforeningVilkår().erLik(familiegjenforeningVilkår()) shouldBe true
        }

        @Test
        fun `slår sammen like perioder`() {
            familiegjenforeningVilkår(
                vurderingsperiode = nonEmptyListOf(
                    vurderingsperiodeFamiliegjenforening(),
                    vurderingsperiodeFamiliegjenforening(periode = år(2023)),
                ),
            ).slåSammenLikePerioder().vurderingsperioder.let {
                it.size shouldBe 1
                it.first().shouldBeEqualToIgnoringFields(
                    vurderingsperiodeFamiliegjenforening(
                        periode = Periode.create(1.januar(2022), 31.desember(2023)),
                    ),
                    Vurderingsperiode::id,
                )
            }
        }

        @Test
        fun `henter tidligste dato for avslag`() {
            familiegjenforeningVilkår(
                nonEmptyListOf(
                    vurderingsperiodeFamiliegjenforening(resultat = Resultat.Avslag),
                    vurderingsperiodeFamiliegjenforening(resultat = Resultat.Avslag, periode = år(2023)),
                ),
            ).hentTidligesteDatoForAvslag() shouldBe 1.januar(2022)
        }
    }

    @Nested
    inner class Hybrid {
        @Test
        fun `vurdert er ikke lik ikkeVurdert`() {
            familiegjenforeningVilkår(nonEmptyListOf(vurderingsperiodeFamiliegjenforening())).erLik(
                FamiliegjenforeningVilkår.IkkeVurdert,
            )
        }
    }
}
