package no.nav.su.se.bakover.domain.oppdrag.avstemming

import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
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
        sept15 shouldBeLessThan okt1
        okt1 shouldBeLessThan nov29
        nov29 shouldBeLessThan jan12200
    }
}
