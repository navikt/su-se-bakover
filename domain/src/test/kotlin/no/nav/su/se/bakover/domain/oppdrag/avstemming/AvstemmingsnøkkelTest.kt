package no.nav.su.se.bakover.domain.oppdrag.avstemming

import io.kotest.matchers.longs.shouldBeInRange
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.longs.shouldNotBeInRange
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.november
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.common.september
import no.nav.su.se.bakover.common.toTidspunkt
import org.junit.jupiter.api.Test

internal class AvstemmingsnøkkelTest {

    private val sept15 = Avstemmingsnøkkel(15.september(2020).atStartOfDay().toTidspunkt())
    private val okt1 = Avstemmingsnøkkel(1.oktober(2020).atStartOfDay().toTidspunkt())
    private val nov29 = Avstemmingsnøkkel(29.november(2020).atStartOfDay().toTidspunkt())
    private val jan12200 = Avstemmingsnøkkel(1.januar(2200).atStartOfDay().toTidspunkt())

    @Test
    fun `sammenligning av nøkler`() {
        sept15 shouldBe sept15
        sept15.nøkkel shouldBeLessThan okt1.nøkkel
        okt1.nøkkel shouldBeLessThan nov29.nøkkel
        nov29.nøkkel shouldBeLessThan jan12200.nøkkel
    }

    @Test
    fun `lager intervall mellom to datoer`() {
        val sept1okt31 = Avstemmingsnøkkel.periode(1.september(2020), 31.oktober(2020))
        sept15.nøkkel shouldBeInRange sept1okt31
        okt1.nøkkel shouldBeInRange sept1okt31
        nov29.nøkkel shouldNotBeInRange sept1okt31
        jan12200.nøkkel shouldNotBeInRange sept1okt31
    }

    @Test
    fun `start og end inclusive i periode`() {
        val periode = Avstemmingsnøkkel.periode(1.januar(2020), 31.januar(2020))
        val start = Avstemmingsnøkkel(1.januar(2020).atStartOfDay().toTidspunkt())
        val slutt = Avstemmingsnøkkel(1.februar(2020).atStartOfDay().minusNanos(1).toTidspunkt())
        start.nøkkel shouldBeInRange periode
        slutt.nøkkel shouldBeInRange periode
        start.nøkkel shouldBe periode.first
        slutt.nøkkel shouldBe periode.last
    }
}
