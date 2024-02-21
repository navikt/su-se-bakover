package vilkår.opplysningsplikt.domain

import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.test.grunnlag.nyOpplysningspliktGrunnlag
import org.junit.jupiter.api.Test

class OpplysningspliktgrunnlagTest {

    @Test
    fun `kopierer innholdet med ny id`() {
        val grunnlag = nyOpplysningspliktGrunnlag()
        grunnlag.copyWithNewId().let {
            it.shouldBeEqualToIgnoringFields(nyOpplysningspliktGrunnlag(), Opplysningspliktgrunnlag::id)
            it.id shouldNotBe grunnlag.id
        }
    }
}
