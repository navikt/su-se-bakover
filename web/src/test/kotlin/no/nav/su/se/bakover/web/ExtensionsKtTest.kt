package no.nav.su.se.bakover.web

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.extensions.mapOneIndexed
import no.nav.su.se.bakover.common.extensions.pickByCondition
import no.nav.su.se.bakover.common.extensions.whenever
import no.nav.su.se.bakover.common.infrastructure.web.toUUID
import org.junit.jupiter.api.Test
import java.util.UUID

internal class ExtensionsKtTest {

    @Test
    fun `String toUUID gir fin feilmelding`() {
        "heisann".toUUID() shouldBe "heisann er ikke en gyldig UUID".left()
    }

    @Test
    fun `String toUUID funker på gyldig UUID`() {
        UUID.randomUUID().let {
            it.toString().toUUID() shouldBe it.right()
        }
    }

    @Test
    fun `whenever utfører isNotEmpty dersom den ikke er tom`() {
        listOf("").whenever(
            isEmpty = { fail("isEmpty skal ikke kjøre dersom listen er tom") },
            isNotEmpty = { },
        )
    }

    @Test
    fun `whenever utfører isEmpty dersom den er tom`() {
        emptyList<String>().whenever(
            isEmpty = { },
            isNotEmpty = { fail("isNotEmpty skal ikke kjøre på en tom liste") },
        )
    }

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
