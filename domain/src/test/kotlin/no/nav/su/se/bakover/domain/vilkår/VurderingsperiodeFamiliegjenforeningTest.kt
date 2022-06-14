package no.nav.su.se.bakover.domain.vilkår

import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.CopyArgs
import org.junit.jupiter.api.Test
import vurderingsperiodeFamiliegjenforening
import vurderingsperiodeFlyktning

private class VurderingsperiodeFamiliegjenforeningTest {

    @Test
    fun `er lik`() {
        vurderingsperiodeFamiliegjenforening().erLik(vurderingsperiodeFamiliegjenforening()) shouldBe true
        vurderingsperiodeFamiliegjenforening().erLik(vurderingsperiodeFamiliegjenforening(resultat = Resultat.Avslag)) shouldBe false
        vurderingsperiodeFamiliegjenforening().erLik(vurderingsperiodeFlyktning()) shouldBe false
    }

    @Test
    fun `copy full periode`() {
        vurderingsperiodeFamiliegjenforening().copy(CopyArgs.Tidslinje.Full)
            .shouldBeEqualToIgnoringFields(vurderingsperiodeFamiliegjenforening(), Vurderingsperiode::id)
    }

    @Test
    fun `copy ny periode`() {
        vurderingsperiodeFamiliegjenforening().copy(CopyArgs.Tidslinje.NyPeriode(år(2025)))
            .shouldBeEqualToIgnoringFields(
                vurderingsperiodeFamiliegjenforening(periode = år(2025)), Vurderingsperiode::id,
            )
    }
}
