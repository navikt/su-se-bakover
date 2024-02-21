package vilkår.pensjon.domain

import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.test.grunnlag.nyPensjonsgrunnlag
import org.junit.jupiter.api.Test

class PensjonsgrunnlagTest {

    @Test
    fun `kopierer innholdet med ny id`() {
        val grunnlag = nyPensjonsgrunnlag()
        grunnlag.copyWithNewId().let {
            it.shouldBeEqualToIgnoringFields(grunnlag, Pensjonsgrunnlag::id)
            it.id shouldNotBe grunnlag.id
        }
    }
}
