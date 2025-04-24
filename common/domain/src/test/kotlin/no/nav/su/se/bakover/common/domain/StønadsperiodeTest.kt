package no.nav.su.se.bakover.common.domain

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.Stønadsperiode.UgyldigStønadsperiode
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Test

internal class StønadsperiodeTest {
    @Test
    fun `serialisering av stønadsperiode`() {
        serialize(StønadsperiodeUnderTest.create(år(2021))) shouldBe """{"periode":{"fraOgMed":"2021-01-01","tilOgMed":"2021-12-31"}}"""
    }

    @Test
    fun `deserialisering av stønadsperiode`() {
        val serialized = """{"periode":{"fraOgMed":"2021-01-01","tilOgMed":"2021-12-31"}}"""

        val deserialized = deserialize<StønadsperiodeUnderTest>(serialized)

        deserialized shouldBe StønadsperiodeUnderTest.create(år(2021))
    }
}

private data class StønadsperiodeUnderTest private constructor(
    val periode: Periode,
) {
    companion object {

        @TestOnly
        fun create(periode: Periode): StønadsperiodeUnderTest {
            return tryCreate(periode).getOrElse { throw IllegalArgumentException(it.toString()) }
        }

        fun tryCreate(periode: Periode): Either<UgyldigStønadsperiode, StønadsperiodeUnderTest> {
            if (periode.fraOgMed.year < 2021) {
                return UgyldigStønadsperiode.FraOgMedDatoKanIkkeVæreFør2021.left()
            }
            if (periode.getAntallMåneder() > 12) {
                return UgyldigStønadsperiode.PeriodeKanIkkeVæreLengreEnn12Måneder.left()
            }

            return StønadsperiodeUnderTest(periode).right()
        }
    }
}
