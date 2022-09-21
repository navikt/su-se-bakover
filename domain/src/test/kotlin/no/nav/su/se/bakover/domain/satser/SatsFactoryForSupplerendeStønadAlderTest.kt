package no.nav.su.se.bakover.domain.satser

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.scaleTo4
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import org.junit.jupiter.api.Test
import java.math.BigDecimal

internal class SatsFactoryForSupplerendeStønadAlderTest {

    @Test
    fun `ordinær - mai 2022`() {
        satsFactoryTestPåDato(påDato = 1.juni(2022)).ordinærAlder(mai(2022)).let {
            it shouldBe FullSupplerendeStønadForMåned.Alder(
                måned = mai(2022),
                satskategori = Satskategori.ORDINÆR,
                garantipensjonForMåned = GarantipensjonForMåned(
                    måned = mai(2022),
                    satsKategori = Satskategori.ORDINÆR,
                    garantipensjonPerÅr = 193862,
                    ikrafttredelse = 20.mai(2022),
                    virkningstidspunkt = 1.mai(2022),
                ),
                toProsentAvHøyForMåned = BigDecimal("349.285"),
            )
            it.satsPerÅr shouldBe BigDecimal("193862")
            it.satsForMåned.scaleTo4() shouldBe BigDecimal("16155.1667")
            it.satsForMånedAvrundet shouldBe 16155
            it.satsForMånedAsDouble shouldBe 16155.166666666666
            it.ikrafttredelse shouldBe 20.mai(2022)
            it.toProsentAvHøyForMånedAsDouble shouldBe 349.285
        }
    }

    @Test
    fun `høy - mai 2022`() {
        satsFactoryTestPåDato(påDato = 1.juni(2022)).høyAlder(mai(2022)).let {
            it shouldBe FullSupplerendeStønadForMåned.Alder(
                måned = mai(2022),
                satskategori = Satskategori.HØY,
                garantipensjonForMåned = GarantipensjonForMåned(
                    måned = mai(2022),
                    satsKategori = Satskategori.HØY,
                    garantipensjonPerÅr = 209571,
                    ikrafttredelse = 20.mai(2022),
                    virkningstidspunkt = 1.mai(2022),
                ),
                toProsentAvHøyForMåned = BigDecimal("349.285"),
            )
            it.satsPerÅr shouldBe BigDecimal("209571")
            it.satsForMåned.scaleTo4() shouldBe BigDecimal("17464.2500")
            it.satsForMånedAvrundet shouldBe 17464
            it.satsForMånedAsDouble shouldBe 17464.25
            it.ikrafttredelse shouldBe 20.mai(2022)
            it.toProsentAvHøyForMånedAsDouble shouldBe 349.285
        }
    }
}
