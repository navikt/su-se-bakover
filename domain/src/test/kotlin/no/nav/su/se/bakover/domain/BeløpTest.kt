package no.nav.su.se.bakover.domain

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.application.Beløp
import org.junit.jupiter.api.Test

internal class BeløpTest {
    @Test
    fun `negative tall blir positive`() {
        Beløp(-10000) shouldBe Beløp(10000)
    }

    @Test
    fun `legger sammen to positive beløp`() {
        Beløp(1000) + Beløp(1500) shouldBe Beløp(2500)
    }
}
