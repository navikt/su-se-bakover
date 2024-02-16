package vilkår.pensjon.domain

import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.test.vilkår.pensjonsVilkårInnvilget
import org.junit.jupiter.api.Test

class PensjonsVilkårTest {

    @Test
    fun `kopierer innholdet med ny id`() {
        val vilkår = pensjonsVilkårInnvilget()

        vilkår.copyWithNewId().let {
            it.shouldBeEqualToIgnoringFields(
                vilkår,
                PensjonsVilkår.Vurdert::vurderingsperioder,
                PensjonsVilkår.Vurdert::grunnlag,
            )
            it.vurderingsperioder.size shouldBe 1
            it.vurderingsperioder.first().shouldBeEqualToIgnoringFields(
                vilkår.vurderingsperioder.first(),
                VurderingsperiodePensjon::id,
                VurderingsperiodePensjon::grunnlag,
            )
            it.vurderingsperioder.first().id shouldNotBe vilkår.vurderingsperioder.first().id
        }
    }
}
