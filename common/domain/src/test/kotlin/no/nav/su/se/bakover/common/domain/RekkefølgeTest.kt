package no.nav.su.se.bakover.common.domain

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.Rekkefølge
import org.junit.jupiter.api.Test

internal class RekkefølgeTest {

    @Test
    fun `rekkefølge start`() {
        Rekkefølge.start() shouldBe Rekkefølge(0)
    }

    @Test
    fun `rekkefølge neste`() {
        Rekkefølge.start().neste() shouldBe Rekkefølge(1)
        Rekkefølge(1).neste() shouldBe Rekkefølge(2)
        Rekkefølge(2).neste() shouldBe Rekkefølge(3)
    }

    @Test
    fun `rekkefølge skip`() {
        Rekkefølge.skip(0) shouldBe Rekkefølge(1)
        Rekkefølge.skip(1) shouldBe Rekkefølge(2)
        Rekkefølge.skip(2) shouldBe Rekkefølge(3)
        Rekkefølge.skip(3) shouldBe Rekkefølge(4)

        Rekkefølge.skip(0) shouldBe Rekkefølge(1)
        Rekkefølge.skip(1) shouldBe Rekkefølge(2)
        Rekkefølge.skip(2) shouldBe Rekkefølge(3)
        Rekkefølge.skip(3) shouldBe Rekkefølge(4)
    }

    @Test
    fun `rekkefølge generator`() {
        val generator1 = Rekkefølge.generator()
        val generator2 = Rekkefølge.generator()
        generator1.neste() shouldBe Rekkefølge(0)
        generator2.neste() shouldBe Rekkefølge(0)
        generator1.neste() shouldBe Rekkefølge(1)
        generator2.neste() shouldBe Rekkefølge(1)
        generator1.neste() shouldBe Rekkefølge(2)
        generator2.neste() shouldBe Rekkefølge(2)
        generator1.neste() shouldBe Rekkefølge(3)
        generator2.neste() shouldBe Rekkefølge(3)
    }

    @Test
    fun `rekkefølge compareTo`() {
        Rekkefølge(0) shouldBe Rekkefølge(0)
        Rekkefølge(0) shouldNotBe Rekkefølge(1)
        Rekkefølge(1) shouldBe Rekkefølge(1)
        Rekkefølge(1) shouldNotBe Rekkefølge(2)
        Rekkefølge(2) shouldBe Rekkefølge(2)
        Rekkefølge(2) shouldNotBe Rekkefølge(3)
    }
}
