package no.nav.su.se.bakover.common.domain

import io.kotest.assertions.fail
import org.junit.jupiter.api.Test

internal class WheneverTest {
    @Test
    fun `whenever utfører false block dersom den er false`() {
        { false }.whenever(isFalse = { }, isTrue = { io.kotest.assertions.fail("false skal utføre isFalse blokken") })
        false.whenever(isFalse = { }, isTrue = { io.kotest.assertions.fail("false skal utføre isFalse blokken") })
    }

    @Test
    fun `whenever utfører true block dersom den er true`() {
        { true }.whenever(isFalse = { io.kotest.assertions.fail("true skal utføre isTrue blokken") }, isTrue = { })
        true.whenever(isFalse = { io.kotest.assertions.fail("true skal utføre isTrue blokken") }, isTrue = { })
    }

    @Test
    fun `whenever på pair eksekverer riktig lambda funksjon`() {
        Pair(1, 2).wheneverEitherIsNull(
            { fail("Ingen av pair verdiene er null") },
            { },
        )
        Pair(null, 2).wheneverEitherIsNull(
            { },
            { fail("minst en av pair verdiene er null") },
        )
        Pair(1, null).wheneverEitherIsNull(
            { },
            { fail("minst en av pair verdiene er null") },
        )
        Pair(null, null).wheneverEitherIsNull(
            { },
            { fail("minst en av pair verdiene er null") },
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
    fun `whenever utfører isNotEmpty dersom den ikke er tom`() {
        listOf("").whenever(
            isEmpty = { fail("isEmpty skal ikke kjøre dersom listen er tom") },
            isNotEmpty = { },
        )
    }
}
