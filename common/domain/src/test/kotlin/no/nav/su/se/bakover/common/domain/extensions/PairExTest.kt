package no.nav.su.se.bakover.common.domain.extensions

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class PairExTest {
    @Test
    fun `sjekker for null i pairs første verdi`() {
        Pair(1, 2).isFirstNull() shouldBe false
        Pair(1, null).isFirstNull() shouldBe false
        Pair(null, 2).isFirstNull() shouldBe true
    }

    @Test
    fun `sjekker for null i pairs andre verdi`() {
        Pair(1, 2).isSecondNull() shouldBe false
        Pair(1, null).isSecondNull() shouldBe true
        Pair(null, 2).isSecondNull() shouldBe false
    }

    @Test
    fun `sjekker om minst en av verdiene er null`() {
        Pair(1, 2).isEitherNull() shouldBe false
        Pair(1, null).isEitherNull() shouldBe true
        Pair(null, 2).isEitherNull() shouldBe true
        Pair(null, null).isEitherNull() shouldBe true
    }

    @Test
    fun `mapBoth`() {
        Pair(1, 2).mapBoth { it + 1 } shouldBe Pair(2, 3)
    }

    @Test
    fun `mapSecond`() {
        Pair(1, 2).mapSecond { it + 1 } shouldBe Pair(1, 3)
    }
}
