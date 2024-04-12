package no.nav.su.se.bakover.common.domain.tid.periode

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class EmptyPerioderTest {

    @Test
    fun `equals - to EmptyPerioder`() {
        EmptyPerioder shouldBe EmptyPerioder
    }
}
