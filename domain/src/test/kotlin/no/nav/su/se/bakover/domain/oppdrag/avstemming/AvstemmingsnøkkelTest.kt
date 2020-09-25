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
import no.nav.su.se.bakover.common.toMicroInstant
import org.junit.jupiter.api.Test

internal class AvstemmingsnøkkelTest {

    private val sept15 = Avstemmingsnøkkel.generer(15.september(2020).atStartOfDay().toMicroInstant())
    private val okt1 = Avstemmingsnøkkel.generer(1.oktober(2020).atStartOfDay().toMicroInstant())
    private val nov29 = Avstemmingsnøkkel.generer(29.november(2020).atStartOfDay().toMicroInstant())
    private val jan12200 = Avstemmingsnøkkel.generer(1.januar(2200).atStartOfDay().toMicroInstant())

    @Test
    fun `sammenligning av nøkler`() {
        sept15 shouldBe sept15
        sept15 shouldBeLessThan okt1
        okt1 shouldBeLessThan nov29
        nov29 shouldBeLessThan jan12200
    }

    @Test
    fun `lager intervall mellom to datoer`() {
        val sept1okt31 = Avstemmingsnøkkel.periode(1.september(2020), 31.oktober(2020))
        sept15 shouldBeInRange sept1okt31
        okt1 shouldBeInRange sept1okt31
        nov29 shouldNotBeInRange sept1okt31
        jan12200 shouldNotBeInRange sept1okt31
    }

    @Test
    fun `start og end inclusive i periode`() {
        val periode = Avstemmingsnøkkel.periode(1.januar(2020), 31.januar(2020))
        val start = Avstemmingsnøkkel.generer(1.januar(2020).atStartOfDay().toMicroInstant())
        val slutt = Avstemmingsnøkkel.generer(1.februar(2020).atStartOfDay().minusNanos(1).toMicroInstant())
        start shouldBeInRange periode
        slutt shouldBeInRange periode
        start shouldBe periode.first
        slutt shouldBe periode.last
    }
}
