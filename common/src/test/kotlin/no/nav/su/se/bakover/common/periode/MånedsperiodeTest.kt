package no.nav.su.se.bakover.common.periode

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.test.fixedClock
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class MånedsperiodeTest {

    @Nested
    inner class `until()` {

        @Test
        fun `februar til januar gir tom liste`() {
            februar(2021).until(januar(2021)) shouldBe emptyList()
        }

        @Test
        fun `januar til januar gir tom liste`() {
            januar(2021).until(januar(2021)) shouldBe emptyList()
        }

        @Test
        fun `januar til februar gir januar`() {
            januar(2021).until(februar(2021)) shouldBe listOf(januar(2021))
        }

        @Test
        fun `januar until april gir januar, februar og mars`() {
            januar(2021).until(april(2021)) shouldBe listOf(
                januar(2021),
                februar(2021),
                mars(2021),
            )
        }
    }

    @Nested
    inner class `plusMonths()` {
        @Test
        fun `januar minus 1 gir januar`() {
            januar(2021).plusMonths(-1) shouldBe desember(2020)
        }

        @Test
        fun `januar pluss 0 gir januar`() {
            januar(2021).plusMonths(0) shouldBe januar(2021)
        }

        @Test
        fun `januar pluss 1 gir februar`() {
            januar(2021).plusMonths(1) shouldBe februar(2021)
        }

        @Test
        fun `januar pluss 12 gir neste januar`() {
            januar(2021).plusMonths(12) shouldBe januar(2022)
        }
    }

    @Test
    fun `equals`() {
        januar(2021) shouldBe januar(2021)
        januar(2021) shouldNotBe februar(2021)

        Månedsperiode(1.januar(2021), 31.januar(2021)) shouldBe Månedsperiode(1.januar(2021), 31.januar(2021))
        Månedsperiode(1.januar(2021), 31.januar(2021)) shouldNotBe Månedsperiode(1.februar(2021), 28.februar(2021))

        januar(2021) shouldBe Månedsperiode(1.januar(2021), 31.januar(2021))
        januar(2021) shouldBe Periode.create(1.januar(2021), 31.januar(2021))

        februar(2021) shouldNotBe Månedsperiode(1.januar(2021), 31.januar(2021))
        februar(2021) shouldNotBe Periode.create(1.januar(2021), 31.januar(2021))
    }

    @Test
    fun `hashCodes`() {
        januar(2021).hashCode() shouldBe Periode.create(1.januar(2021), 31.januar(2021)).hashCode()
        februar(2021).hashCode() shouldNotBe Periode.create(1.januar(2021), 31.januar(2021)).hashCode()
    }

    @Test
    fun now() {
        Månedsperiode.now(fixedClock) shouldBe januar(2021)
    }
}
