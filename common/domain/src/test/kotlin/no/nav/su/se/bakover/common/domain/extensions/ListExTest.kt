package no.nav.su.se.bakover.common.domain.extensions

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class ListExTest {
    @Test
    fun `filtrerer basert på en condition`() {
        val list = listOf(1, 2, 3, 4)
        val list2 = listOf(3, 4, 5)
        val res = list.pickByCondition(list2) { first, second -> first >= second }
        res shouldBe listOf(3, 4)
    }

    @Test
    fun `starter indeksering fra 1 `() {
        val list = listOf(1, 1, 1)
        list.mapOneIndexed { idx, _ -> idx } shouldBe listOf(1, 2, 3)
    }
}
