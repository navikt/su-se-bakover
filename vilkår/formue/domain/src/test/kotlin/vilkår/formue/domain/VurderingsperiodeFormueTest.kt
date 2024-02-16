package vilkår.formue.domain

import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.test.vurderingsperiode.nyVurderingsperiodeFormue
import org.junit.jupiter.api.Test

class VurderingsperiodeFormueTest {

    @Test
    fun `kopierer innholdet med ny id`() {
        val vurderingsperiode = nyVurderingsperiodeFormue()

        vurderingsperiode.copyWithNewId().let {
            it.id shouldNotBe vurderingsperiode.id
            it.periode shouldBe vurderingsperiode.periode
            it.opprettet shouldBe vurderingsperiode.opprettet
            it.vurdering shouldBe vurderingsperiode.vurdering
            it.grunnlag.shouldBeEqualToIgnoringFields(vurderingsperiode.grunnlag, Formuegrunnlag::id)
            it.grunnlag.id shouldNotBe vurderingsperiode.grunnlag.id
        }
    }
}
