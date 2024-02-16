package vilkår.pensjon.domain

import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.test.vurderingsperiode.nyVurderingsperiodePensjon
import org.junit.jupiter.api.Test

class VurderingsperiodePensjonTest {

    @Test
    fun `kopierer innholdet med ny id`() {
        val vurderingsperiode = nyVurderingsperiodePensjon()
        vurderingsperiode.copyWithNewId().let {
            it.shouldBeEqualToIgnoringFields(
                vurderingsperiode,
                VurderingsperiodePensjon::id,
                VurderingsperiodePensjon::grunnlag,
            )
            it.id shouldNotBe vurderingsperiode.id
            it.grunnlag.shouldBeEqualToIgnoringFields(vurderingsperiode.grunnlag, Pensjonsgrunnlag::id)
            it.grunnlag.id shouldNotBe vurderingsperiode.grunnlag.id
        }
    }
}
