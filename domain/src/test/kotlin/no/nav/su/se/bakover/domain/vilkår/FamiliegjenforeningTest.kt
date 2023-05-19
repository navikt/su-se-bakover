package no.nav.su.se.bakover.domain.vilkår

import arrow.core.nonEmptyListOf
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.vilkår.familiegjenforeningVilkårInnvilget
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import vurderingsperiode.vurderingsperiodeFamiliegjenforeningInnvilget

private class FamiliegjenforeningTest {

    @Nested
    inner class IkkeVurdert {
        @Test
        fun `resultat skal være uavklart`() {
            FamiliegjenforeningVilkår.IkkeVurdert.vurdering shouldBe Vurdering.Uavklart
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
            familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(
                    vurderingsperiodeFamiliegjenforeningInnvilget(),
                    vurderingsperiodeFamiliegjenforeningInnvilget(periode = år(2023)),
                ),
            ).erInnvilget shouldBe true
        }

        @Test
        fun `er avslag dersom minst en periode er avslag`() {
            familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(
                    vurderingsperiodeFamiliegjenforeningInnvilget(),
                    vurderingsperiodeFamiliegjenforeningInnvilget(vurdering = Vurdering.Avslag, periode = år(2023)),
                ),
            ).erAvslag shouldBe true
        }

        @Test
        fun `er lik dersom begge er vurdert & vurderingsperiodene er lik`() {
            familiegjenforeningVilkårInnvilget().erLik(familiegjenforeningVilkårInnvilget()) shouldBe true
        }

        @Test
        fun `slår sammen like perioder`() {
            familiegjenforeningVilkårInnvilget(
                vurderingsperioder = nonEmptyListOf(
                    vurderingsperiodeFamiliegjenforeningInnvilget(),
                    vurderingsperiodeFamiliegjenforeningInnvilget(periode = år(2022)),
                ),
            ).slåSammenLikePerioder().vurderingsperioder.let {
                it.size shouldBe 1
                it.first().shouldBeEqualToIgnoringFields(
                    vurderingsperiodeFamiliegjenforeningInnvilget(
                        periode = Periode.create(1.januar(2021), 31.desember(2022)),
                    ),
                    Vurderingsperiode::id,
                )
            }
        }

        @Test
        fun `henter tidligste dato for avslag`() {
            familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(
                    vurderingsperiodeFamiliegjenforeningInnvilget(vurdering = Vurdering.Avslag),
                    vurderingsperiodeFamiliegjenforeningInnvilget(vurdering = Vurdering.Avslag, periode = år(2023)),
                ),
            ).hentTidligesteDatoForAvslag() shouldBe 1.januar(2021)
        }

        @Test
        fun `tidligste dato for avslag skal være null for innvilget`() {
            familiegjenforeningVilkårInnvilget(
                nonEmptyListOf(
                    vurderingsperiodeFamiliegjenforeningInnvilget(vurdering = Vurdering.Innvilget),
                    vurderingsperiodeFamiliegjenforeningInnvilget(vurdering = Vurdering.Innvilget, periode = år(2023)),
                ),
            ).hentTidligesteDatoForAvslag() shouldBe null
        }
    }

    @Nested
    inner class Hybrid {
        @Test
        fun `vurdert er ikke lik ikkeVurdert`() {
            familiegjenforeningVilkårInnvilget(nonEmptyListOf(vurderingsperiodeFamiliegjenforeningInnvilget())).erLik(
                FamiliegjenforeningVilkår.IkkeVurdert,
            )
        }
    }
}
