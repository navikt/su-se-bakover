package vilkår.familiegjenforening.domain

import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.vurderingsperiode.vurderingsperiodeFlyktning
import org.junit.jupiter.api.Test
import vilkår.common.domain.Vurdering
import vilkår.common.domain.Vurderingsperiode
import vurderingsperiode.vurderingsperiodeFamiliegjenforeningInnvilget

class VurderingsperiodeFamiliegjenforeningTest {

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

    @Test
    fun `kopierer grunnlaget med ny id`() {
        val vurderingsperiode = vurderingsperiodeFamiliegjenforeningInnvilget()
        vurderingsperiode.copyWithNewId().let {
            it.id shouldNotBe vurderingsperiode.id
            // familiegjenforening har ikke noe grunnlag - denne kommer til å feile dersom det blir lagt til
            it.grunnlag?.id shouldBe vurderingsperiode.grunnlag?.id
        }
    }
}
