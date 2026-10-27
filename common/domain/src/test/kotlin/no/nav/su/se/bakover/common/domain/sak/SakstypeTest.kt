package no.nav.su.se.bakover.common.domain.sak

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SakstypeTest {
    @Test
    fun `begge parserne støtter alle sakstyper`() {
        Sakstype.entries.forEach { sakstype ->
            Sakstype.tryFrom(sakstype.value) shouldBe sakstype
            Sakstype.from(sakstype.value) shouldBe sakstype
        }
    }

    @Test
    fun `tryFrom returnerer null for ugyldig input mens from fortsatt kaster`() {
        listOf("", "ukjent", "ALDER", " alder ").forEach { ugyldigSakstype ->
            Sakstype.tryFrom(ugyldigSakstype) shouldBe null
            assertThrows<IllegalArgumentException> {
                Sakstype.from(ugyldigSakstype)
            }
        }
    }
}
