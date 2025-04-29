package no.nav.su.se.bakover.common.infrastructure.persistence

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import org.junit.jupiter.api.Test

internal class StønadsperiodeDbJsonTest {
    @Test
    fun `serialisering av StønadsperiodeDbJson`() {
        serialize(StønadsperiodeDbJson(PeriodeDbJson("2021-01-01", "2021-12-31"))) shouldBe """{"periode":{"fraOgMed":"2021-01-01","tilOgMed":"2021-12-31"}}"""
    }

    @Test
    fun `deserialisering av StønadsperiodeDbJson`() {
        val serialized = """{"periode":{"fraOgMed":"2021-01-01","tilOgMed":"2021-12-31"}}"""

        val deserialized = deserialize<StønadsperiodeDbJson>(serialized)

        deserialized shouldBe StønadsperiodeDbJson(PeriodeDbJson("2021-01-01", "2021-12-31"))
    }
}
