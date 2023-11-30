package no.nav.su.se.bakover.domain.oppdrag.avstemming

import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.november
import no.nav.su.se.bakover.common.extensions.oktober
import no.nav.su.se.bakover.common.extensions.september
import no.nav.su.se.bakover.common.extensions.startOfDay
import no.nav.su.se.bakover.common.tid.toTidspunkt
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Test
import økonomi.domain.avstemming.Avstemmingsnøkkel
import java.time.Instant

internal class AvstemmingsnøkkelTest {

    private val sept15 = Avstemmingsnøkkel(15.september(2020).startOfDay())
    private val okt1 = Avstemmingsnøkkel(1.oktober(2020).startOfDay())
    private val nov29 = Avstemmingsnøkkel(29.november(2020).startOfDay())
    private val jan12200 = Avstemmingsnøkkel(1.januar(2200).startOfDay())

    @Test
    fun `sammenligning av nøkler`() {
        sept15 shouldBe sept15
        sept15 shouldBeLessThan okt1
        okt1 shouldBeLessThan nov29
        nov29 shouldBeLessThan jan12200
    }

    @Test
    fun `Parse nøkkel from specific time`() {
        val seconds = 1601975988L
        val nanos = 123456789L
        val avstemmingsnøkkel = Avstemmingsnøkkel(Instant.ofEpochSecond(seconds, nanos).toTidspunkt())
        // Forventer at den har trunkert de 3 siste (til micros istedenfor nanos)
        avstemmingsnøkkel.toString() shouldBe "1601975988123456000"
        avstemmingsnøkkel shouldBe Avstemmingsnøkkel.fromString(avstemmingsnøkkel.toString())
    }

    @Test
    fun `Parse nøkkel from now`() {
        Avstemmingsnøkkel(opprettet = fixedTidspunkt).also {
            it shouldBe Avstemmingsnøkkel.fromString(it.toString())
        }
    }
}
