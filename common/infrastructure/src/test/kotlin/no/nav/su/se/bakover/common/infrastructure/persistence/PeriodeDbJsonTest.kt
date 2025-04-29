package no.nav.su.se.bakover.common.infrastructure.persistence

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import org.junit.jupiter.api.Test

internal class PeriodeDbJsonTest {
    @Test
    fun `serialisering av PeriodeDbJson`() {
        serialize(PeriodeDbJson("2021-01-01", "2021-12-31")) shouldBe """{"fraOgMed":"2021-01-01","tilOgMed":"2021-12-31"}"""
    }

    @Test
    fun `deserialisering av PeriodeDbJson`() {
        val serialized = """{"fraOgMed":"2021-01-01","tilOgMed":"2021-12-31"}"""

        val deserialized = deserialize<PeriodeDbJson>(serialized)

        deserialized shouldBe PeriodeDbJson("2021-01-01", "2021-12-31")
    }
}
