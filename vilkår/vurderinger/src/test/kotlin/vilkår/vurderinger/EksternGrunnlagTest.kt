package vilkår.vurderinger

import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import no.nav.su.se.bakover.test.eksterneGrunnlag.eksternGrunnlagHentet
import org.junit.jupiter.api.Test
import vilkår.skatt.domain.Skattegrunnlag
import vilkår.vurderinger.domain.EksterneGrunnlagSkatt

class EksternGrunnlagTest {

    @Test
    fun `kopierer innholdet med ny id`() {
        val eksternGrunnlag = eksternGrunnlagHentet()
        eksternGrunnlag.copyWithNewIds().let {
            (it.skatt as EksterneGrunnlagSkatt.Hentet).søkers.shouldBeEqualToIgnoringFields(
                (eksternGrunnlag.skatt as EksterneGrunnlagSkatt.Hentet).søkers,
                Skattegrunnlag::id,
            )
        }
    }
}
