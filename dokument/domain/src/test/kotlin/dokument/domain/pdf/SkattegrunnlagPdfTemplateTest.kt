package dokument.domain.pdf

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.test.nySkattegrunnlagsPdfInnholdForFrioppslag
import no.nav.su.se.bakover.test.skatt.nySkattegrunnlag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SkattegrunnlagPdfTemplateTest {

    @Test
    fun `bruker tittel som sendes med, ellers default`() {
        nySkattegrunnlagsPdfInnholdForFrioppslag().pdfTemplate.tittel() shouldBe "Skattegrunnlag"
        nySkattegrunnlagsPdfInnholdForFrioppslag(
            skattegrunnlagSøker = null,
            skattegrunnlagEps = nySkattegrunnlag(),
        ).pdfTemplate.tittel() shouldBe "Skattegrunnlag - EPS"
    }

    @Test
    fun `kaster exception dersom søker og eps er null`() {
        assertThrows<IllegalArgumentException> {
            nySkattegrunnlagsPdfInnholdForFrioppslag(skattegrunnlagSøker = null)
        }
    }
}
