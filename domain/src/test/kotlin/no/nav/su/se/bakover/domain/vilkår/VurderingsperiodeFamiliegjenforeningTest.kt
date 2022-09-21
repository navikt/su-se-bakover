package no.nav.su.se.bakover.domain.vilkår

import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.test.vurderingsperiode.vurderingsperiodeFlyktning
import org.junit.jupiter.api.Test
import vurderingsperiode.vurderingsperiodeFamiliegjenforeningInnvilget

private class VurderingsperiodeFamiliegjenforeningTest {

    @Test
    fun `er lik`() {
        vurderingsperiodeFamiliegjenforeningInnvilget().erLik(vurderingsperiodeFamiliegjenforeningInnvilget()) shouldBe true
        vurderingsperiodeFamiliegjenforeningInnvilget().erLik(vurderingsperiodeFamiliegjenforeningInnvilget(vurdering = Vurdering.Avslag)) shouldBe false
        vurderingsperiodeFamiliegjenforeningInnvilget().erLik(vurderingsperiodeFlyktning()) shouldBe false
    }

    @Test
    fun `copy full periode`() {
        vurderingsperiodeFamiliegjenforeningInnvilget().copy(CopyArgs.Tidslinje.Full)
            .shouldBeEqualToIgnoringFields(vurderingsperiodeFamiliegjenforeningInnvilget(), Vurderingsperiode::id)
    }

    @Test
    fun `copy ny periode`() {
        vurderingsperiodeFamiliegjenforeningInnvilget().copy(CopyArgs.Tidslinje.NyPeriode(år(2025)))
            .shouldBeEqualToIgnoringFields(
                vurderingsperiodeFamiliegjenforeningInnvilget(periode = år(2025)),
                Vurderingsperiode::id,
            )
    }
}
