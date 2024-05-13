package no.nav.su.se.bakover.common.domain.extensions

import arrow.core.left
import arrow.core.right
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

    @Test
    fun `filtrerer ut alle lefts i en liste`() {
        listOf(1.left(), 2.right(), 3.left()).filterLefts() shouldBe listOf(1, 3)
    }

    @Test
    fun `filtrerer ut alle rights i en liste`() {
        listOf(1.left(), 2.right(), 3.left()).filterRights() shouldBe listOf(2)
    }

    @Test
    fun `splitter lefts og rights i hver sine par`() {
        listOf(1.left(), 2.right(), 3.left()).split() shouldBe Pair(listOf(1, 3), listOf(2))
    }
}
