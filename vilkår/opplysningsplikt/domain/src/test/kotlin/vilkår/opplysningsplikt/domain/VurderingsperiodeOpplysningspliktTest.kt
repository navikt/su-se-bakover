package vilkår.opplysningsplikt.domain

import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.test.vurderingsperiode.nyVurderingsperiodeOpplysningsplikt
import org.junit.jupiter.api.Test

class VurderingsperiodeOpplysningspliktTest {

    @Test
    fun `kopierer innholdet med ny id`() {
        val vurderingsperiode = nyVurderingsperiodeOpplysningsplikt()
        vurderingsperiode.copyWithNewId().let {
            it.shouldBeEqualToIgnoringFields(
                vurderingsperiode,
                VurderingsperiodeOpplysningsplikt::id,
                VurderingsperiodeOpplysningsplikt::grunnlag,
            )
            it.id shouldNotBe vurderingsperiode.id
            it.grunnlag.shouldBeEqualToIgnoringFields(vurderingsperiode.grunnlag, Opplysningspliktgrunnlag::id)
            it.grunnlag.id shouldNotBe vurderingsperiode.grunnlag.id
        }
    }
}
