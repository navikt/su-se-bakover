package no.nav.su.se.bakover.domain.satser

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Måned
import no.nav.su.se.bakover.common.periode.april
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.juli
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.september
import no.nav.su.se.bakover.common.september
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import org.junit.jupiter.api.Test
import java.math.BigDecimal

internal class GarantipensjonFactoryTest {
    @Test
    fun `ordinært garantipensjonsnivå før desember 2015 skal være udefinert`() {
        shouldThrow<RuntimeException> {
            satsFactoryTestPåDato().ordinærAlder(desember(2015))
        }
    }

    @Test
    fun `høyt garantipensjonsnivå før desember 2015 skal være udefinert`() {
        shouldThrow<RuntimeException> {
            satsFactoryTestPåDato().høyAlder(desember(2015))
        }
    }

    @Test
    fun `ordinært garantipensjonsnivå fra januar 2016 skal være definert`() {
        shouldNotThrow<Throwable> {
            satsFactoryTestPåDato().ordinærAlder(januar(2016))
        }
    }

    @Test
    fun `høyt garantipensjonsnivå fra januar 2016 skal være definert`() {
        shouldNotThrow<Throwable> {
            satsFactoryTestPåDato().høyAlder(januar(2016))
        }
    }

    @Test
    fun `ordinært garantipensjonsnivå mellom sept 2019 og mai 2020 skal være 176 099 kr`() {
        satsFactoryTestPåDato().ordinærAlder(september(2019)).satsPerÅr.intValueExact() shouldBe 176099
        satsFactoryTestPåDato().ordinærAlder(januar(2020)).satsPerÅr.intValueExact() shouldBe 176099
        satsFactoryTestPåDato().ordinærAlder(april(2020)).satsPerÅr.intValueExact() shouldBe 176099
    }

    @Test
    fun `høyt garantipensjonsnivå mellom sept 2019 og mai 2020 skal være 190 368 kr`() {
        satsFactoryTestPåDato().høyAlder(september(2019)).satsPerÅr.intValueExact() shouldBe 190368
        satsFactoryTestPåDato().høyAlder(januar(2020)).satsPerÅr.intValueExact() shouldBe 190368
        satsFactoryTestPåDato().høyAlder(april(2020)).satsPerÅr.intValueExact() shouldBe 190368
    }

    @Test
    fun `ordinært garantipensjonsnivå etter 4 september 2020 skal være 177 724 kr`() {
        fun expected(måned: Måned) = FullSupplerendeStønadForMåned.Alder(
            måned = måned,
            satskategori = Satskategori.ORDINÆR,
            garantipensjonForMåned = GarantipensjonForMåned(
                måned = måned,
                satsKategori = Satskategori.ORDINÆR,
                garantipensjonPerÅr = 177724,
                ikrafttredelse = 4.september(2020),
                virkningstidspunkt = 1.mai(2020),
            ),
            toProsentAvHøyForMåned = BigDecimal("16010.41666666666666666666666666667"),
        )
        satsFactoryTestPåDato().ordinærAlder(mai(2020)) shouldBe expected(mai(2020))
        satsFactoryTestPåDato().ordinærAlder(juli(2020)) shouldBe expected(juli(2020))
    }

    @Test
    fun `høyt garantipensjonsnivå etter mai 2020 skal være 192 125 kr`() {
        satsFactoryTestPåDato().høyAlder(mai(2020)).satsPerÅr.intValueExact() shouldBe 192125
        satsFactoryTestPåDato().høyAlder(juli(2020)).satsPerÅr.intValueExact() shouldBe 192125
    }

    @Test
    fun `ordinær garantipensjonsnivå etter 21 mai 2021 skal være 187 252 kr`() {
        satsFactoryTestPåDato(påDato = 21.mai(2021)).ordinærAlder(mai(2021)).satsPerÅr.intValueExact() shouldBe 187252
        satsFactoryTestPåDato(påDato = 21.mai(2021)).ordinærAlder(juli(2021)).satsPerÅr.intValueExact() shouldBe 187252
    }

    @Test
    fun `høyt garantipensjonsnivå etter 21 mai 2021 skal være 202 425 kr`() {
        satsFactoryTestPåDato(påDato = 21.mai(2021)).høyAlder(mai(2021)).satsPerÅr.intValueExact() shouldBe 202425
        satsFactoryTestPåDato(påDato = 21.mai(2021)).høyAlder(juli(2021)).satsPerÅr.intValueExact() shouldBe 202425
    }

    @Test
    fun `ordinær garantipensjonsnivå etter 20 mai 2022 skal være 193 862 kr`() {
        satsFactoryTestPåDato(påDato = 20.mai(2022)).ordinærAlder(mai(2022)).satsPerÅr.intValueExact() shouldBe 193862
        satsFactoryTestPåDato(påDato = 20.mai(2022)).ordinærAlder(juli(2022)).satsPerÅr.intValueExact() shouldBe 193862
    }

    @Test
    fun `høyt garantipensjonsnivå etter 20 mai 2022 skal være 209 571 kr`() {
        satsFactoryTestPåDato(påDato = 20.mai(2022)).høyAlder(mai(2022)).satsPerÅr.intValueExact() shouldBe 209571
        satsFactoryTestPåDato(påDato = 20.mai(2022)).høyAlder(juli(2022)).satsPerÅr.intValueExact() shouldBe 209571
    }

    @Test
    fun `høyt garantipensjonsnivå mai 2022 på dato 2021-01-01 skal gi mai 2020 satser`() {
        satsFactoryTestPåDato(påDato = 1.januar(2021)).høyAlder(mai(2022)).satsPerÅr.intValueExact() shouldBe 192125
    }
}
