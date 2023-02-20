package no.nav.su.se.bakover.common

import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import java.time.Year

internal class YearRangeTest {

    @Test
    fun `four years`() {
        (Year.of(2020)..Year.of(2023)).let {
            it.size == 4
        }
    }
}
