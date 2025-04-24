package no.nav.su.se.bakover.common.domain

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.periode.år
import org.junit.jupiter.api.Test

internal class StønadsperiodeTest {
    @Test
    fun `serialisering av stønadsperiode`() {
        serialize(Stønadsperiode.create(år(2021))) shouldBe """{"periode":{"fraOgMed":"2021-01-01","tilOgMed":"2021-12-31"}}"""
    }

    @Test
    fun `deserialisering av stønadsperiode`() {
        val serialized = """{"periode":{"fraOgMed":"2021-01-01","tilOgMed":"2021-12-31"}}"""

        val deserialized = deserialize<Stønadsperiode>(serialized)

        deserialized shouldBe Stønadsperiode.create(år(2021))
    }
}
