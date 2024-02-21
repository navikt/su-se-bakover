package vilkår.vurderinger

import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.test.eksterneGrunnlag.eksternGrunnlagHentet
import no.nav.su.se.bakover.test.shouldBeEqualToExceptId
import org.junit.jupiter.api.Test
import vilkår.vurderinger.domain.EksterneGrunnlagSkatt

class EksternGrunnlagTest {

    @Test
    fun `kopierer innholdet med ny id`() {
        val eksternGrunnlag = eksternGrunnlagHentet()
        eksternGrunnlag.copyWithNewIds().let {
            it.shouldBeEqualToExceptId(eksternGrunnlag)
            (it.skatt as EksterneGrunnlagSkatt.Hentet).søkers.id shouldNotBe (eksternGrunnlag.skatt as EksterneGrunnlagSkatt.Hentet).søkers.id
        }
    }
}
