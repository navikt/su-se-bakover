package vilkår.bosituasjon.domain.grunnlag

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.test.fullstendigMedEPSOver67
import no.nav.su.se.bakover.test.fullstendigMedEPSUnder67IkkeUførFlyktning
import no.nav.su.se.bakover.test.fullstendigMedEPSUnder67UførFlyktning
import no.nav.su.se.bakover.test.fullstendigMedVoksne
import no.nav.su.se.bakover.test.fullstendigUtenEPS
import no.nav.su.se.bakover.test.ufullstendigEnslig
import no.nav.su.se.bakover.test.ufullstendigMedEps
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class BosituasjonTest {

    @Nested
    inner class `kopierer grunnlaget med ny id` {

        @Test
        fun `ufullstendig enslig`() {
            val originalId = UUID.randomUUID()
            val ufullstendigEnslig = ufullstendigEnslig(id = originalId)

            ufullstendigEnslig.copyWithNewId().let {
                it.id shouldNotBe originalId
                it.opprettet shouldBe ufullstendigEnslig.opprettet
                it.periode shouldBe ufullstendigEnslig.periode
                it.satskategori shouldBe ufullstendigEnslig.satskategori
                it.eps shouldBe ufullstendigEnslig.eps
            }
        }

        @Test
        fun `ufullstendig med eps`() {
            val originalId = UUID.randomUUID()
            val ufullstendigEPS = ufullstendigMedEps(id = originalId)

            ufullstendigEPS.copyWithNewId().let {
                it.id shouldNotBe originalId
                it.opprettet shouldBe ufullstendigEPS.opprettet
                it.periode shouldBe ufullstendigEPS.periode
                it.satskategori shouldBe ufullstendigEPS.satskategori
                it.eps shouldBe ufullstendigEPS.eps
            }
        }

        @Test
        fun `fullstendig enslig`() {
            val originalId = UUID.randomUUID()
            val fullstendigEnslig = fullstendigUtenEPS(id = originalId)

            fullstendigEnslig.copyWithNewId().let {
                it.id shouldNotBe originalId
                it.opprettet shouldBe fullstendigEnslig.opprettet
                it.periode shouldBe fullstendigEnslig.periode
                it.satskategori shouldBe fullstendigEnslig.satskategori
                it.eps shouldBe fullstendigEnslig.eps
            }
        }

        @Test
        fun `fullstendig eps under 67 ufør flyktning`() {
            val originalId = UUID.randomUUID()
            val fullstendigEpsUnder67UførFlyktning = fullstendigMedEPSUnder67UførFlyktning(id = originalId)

            fullstendigEpsUnder67UførFlyktning.copyWithNewId().let {
                it.id shouldNotBe originalId
                it.opprettet shouldBe fullstendigEpsUnder67UførFlyktning.opprettet
                it.periode shouldBe fullstendigEpsUnder67UførFlyktning.periode
                it.satskategori shouldBe fullstendigEpsUnder67UførFlyktning.satskategori
                it.eps shouldBe fullstendigEpsUnder67UførFlyktning.eps
            }
        }

        @Test
        fun `fullstendig eps under 67 ikke ufør flyktning`() {
            val originalId = UUID.randomUUID()
            val fullstendigEpsUnder67IkkeUførFlyktning = fullstendigMedEPSUnder67IkkeUførFlyktning(id = originalId)

            fullstendigEpsUnder67IkkeUførFlyktning.copyWithNewId().let {
                it.id shouldNotBe originalId
                it.opprettet shouldBe fullstendigEpsUnder67IkkeUførFlyktning.opprettet
                it.periode shouldBe fullstendigEpsUnder67IkkeUførFlyktning.periode
                it.satskategori shouldBe fullstendigEpsUnder67IkkeUførFlyktning.satskategori
                it.eps shouldBe fullstendigEpsUnder67IkkeUførFlyktning.eps
            }
        }

        @Test
        fun `fullstendig eps over 67`() {
            val originalId = UUID.randomUUID()
            val fullstendigEpsOver67 = fullstendigMedEPSOver67(id = originalId)

            fullstendigEpsOver67.copyWithNewId().let {
                it.id shouldNotBe originalId
                it.opprettet shouldBe fullstendigEpsOver67.opprettet
                it.periode shouldBe fullstendigEpsOver67.periode
                it.satskategori shouldBe fullstendigEpsOver67.satskategori
                it.eps shouldBe fullstendigEpsOver67.eps
            }
        }

        @Test
        fun `fullstendig med voksne`() {
            val originalId = UUID.randomUUID()
            val fullstendigMedVoksne = fullstendigMedVoksne(id = originalId)

            fullstendigMedVoksne.copyWithNewId().let {
                it.id shouldNotBe originalId
                it.opprettet shouldBe fullstendigMedVoksne.opprettet
                it.periode shouldBe fullstendigMedVoksne.periode
                it.satskategori shouldBe fullstendigMedVoksne.satskategori
                it.eps shouldBe fullstendigMedVoksne.eps
            }
        }
    }
}
