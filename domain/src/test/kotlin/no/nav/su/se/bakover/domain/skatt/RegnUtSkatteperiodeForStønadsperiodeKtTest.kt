package no.nav.su.se.bakover.domain.skatt

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.tid.YearRange
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.juli
import no.nav.su.se.bakover.common.tid.periode.juni
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.test.fixedClockAt
import org.junit.jupiter.api.Test
import java.time.Year

internal class RegnUtSkatteperiodeForStønadsperiodeKtTest {
    private val jan24 = fixedClockAt(1.januar(2024))

    @Test
    fun `stønadsperiode null`() {
        null.regnUtSkatteperiodeForStønadsperiode(jan24).shouldBe(
            YearRange(Year.of(2022), Year.of(2024)),
        )
    }

    @Test
    fun `stønadsperiode 21`() {
        val stønadsperiode = Stønadsperiode.create(januar(2021)..desember(2021))
        stønadsperiode.regnUtSkatteperiodeForStønadsperiode(jan24).shouldBe(
            YearRange(Year.of(2021), Year.of(2021)),
        )
    }

    @Test
    fun `stønadsperiode 21 og 22`() {
        val stønadsperiode = Stønadsperiode.create(juli(2021)..juni(2022))
        stønadsperiode.regnUtSkatteperiodeForStønadsperiode(jan24).shouldBe(
            YearRange(Year.of(2021), Year.of(2022)),
        )
    }

    @Test
    fun `stønadsperiode 22`() {
        val stønadsperiode = Stønadsperiode.create(januar(2022)..desember(2022))
        stønadsperiode.regnUtSkatteperiodeForStønadsperiode(jan24).shouldBe(
            YearRange(Year.of(2022), Year.of(2022)),
        )
    }

    @Test
    fun `stønadsperiode 22 og 23`() {
        val stønadsperiode = Stønadsperiode.create(juli(2022)..juni(2023))
        stønadsperiode.regnUtSkatteperiodeForStønadsperiode(jan24).shouldBe(
            YearRange(Year.of(2022), Year.of(2023)),
        )
    }

    @Test
    fun `stønadsperiode 23`() {
        val stønadsperiode = Stønadsperiode.create(januar(2023)..desember(2023))
        stønadsperiode.regnUtSkatteperiodeForStønadsperiode(jan24).shouldBe(
            YearRange(Year.of(2022), Year.of(2023)),
        )
    }

    @Test
    fun `stønadsperiode 23 og 24`() {
        val stønadsperiode = Stønadsperiode.create(juli(2023)..juni(2024))
        stønadsperiode.regnUtSkatteperiodeForStønadsperiode(jan24).shouldBe(
            YearRange(Year.of(2022), Year.of(2024)),
        )
    }

    @Test
    fun `stønadsperiode 24`() {
        val stønadsperiode = Stønadsperiode.create(januar(2024)..desember(2024))
        stønadsperiode.regnUtSkatteperiodeForStønadsperiode(jan24).shouldBe(
            YearRange(Year.of(2022), Year.of(2024)),
        )
    }

    @Test
    fun `stønadsperiode 25`() {
        // Kun tenkt eksempel fram i tid (merk at dette skjer i jan 24). Dette skal ikke skje i praksis.
        val stønadsperiode = Stønadsperiode.create(januar(2025)..desember(2025))
        stønadsperiode.regnUtSkatteperiodeForStønadsperiode(jan24).shouldBe(
            YearRange(Year.of(2022), Year.of(2024)),
        )
    }
}
