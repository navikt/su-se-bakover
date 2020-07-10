package no.nav.su.se.bakover.domain

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.RoundingMode.HALF_UP
import java.time.LocalDate

internal class BeregningTest {

    @Test
    fun janApr2018ogmaiDes2019() {
        val beregning = Beregning(LocalDate.of(2018, 1, 1))
        val janApr = BigDecimal(2.48).multiply(BigDecimal(93634)).divide(BigDecimal(12)).setScale(0, HALF_UP).toInt()
        val maiDes = BigDecimal(2.48).multiply(BigDecimal(96883)).divide(BigDecimal(12)).setScale(0, HALF_UP).toInt()
        val beregn = beregning.beregn()
        beregn shouldBe listOf(
            janApr,
            janApr,
            janApr,
            janApr,
            maiDes,
            maiDes,
            maiDes,
            maiDes,
            maiDes,
            maiDes,
            maiDes,
            maiDes
        )
    }

    @Test
    fun des2018tilNov2019() {
        val beregning = Beregning(LocalDate.of(2018, 12, 1))
        val s2018 = BigDecimal(2.48).multiply(BigDecimal(96883)).divide(BigDecimal(12)).setScale(0, HALF_UP).toInt()
        val s2019 = BigDecimal(2.48).multiply(BigDecimal(99858)).divide(BigDecimal(12)).setScale(0, HALF_UP).toInt()
        val beregn = beregning.beregn()
        beregn shouldBe listOf(
            s2018,
            s2018,
            s2018,
            s2018,
            s2018,
            s2019,
            s2019,
            s2019,
            s2019,
            s2019,
            s2019,
            s2019
        )
    }

    @Test
    fun janDes2019() {
        val beregning = Beregning(LocalDate.of(2019, 5, 1))
        beregning.beregn() shouldBe listOf(
            20637,
            20637,
            20637,
            20637,
            20637,
            20637,
            20637,
            20637,
            20637,
            20637,
            20637,
            20637
        )
    }
}
