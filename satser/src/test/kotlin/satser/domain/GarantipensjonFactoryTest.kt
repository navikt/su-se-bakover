package satser.domain

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifiseringer
import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifsering
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.domain.tid.september
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.juli
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import org.junit.jupiter.api.Test
import satser.domain.garantipensjon.GarantipensjonForMåned
import satser.domain.supplerendestønad.FullSupplerendeStønadForMåned
import satser.domain.supplerendestønad.ToProsentAvHøyForMåned
import java.math.BigDecimal

internal class GarantipensjonFactoryTest {
    @Test
    fun `ordinært garantipensjonsnivå før desember 2019 skal være udefinert`() {
        shouldThrow<RuntimeException> {
            satsFactoryTestPåDato().ordinærAlder(desember(2019))
        }
    }

    @Test
    fun `høyt garantipensjonsnivå før desember 2019 skal være udefinert`() {
        shouldThrow<RuntimeException> {
            satsFactoryTestPåDato().høyAlder(desember(2019))
        }
    }

    @Test
    fun `ordinært garantipensjonsnivå fra januar 2020 skal være definert`() {
        shouldNotThrow<Throwable> {
            satsFactoryTestPåDato().ordinærAlder(januar(2020))
        }
    }

    @Test
    fun `høyt garantipensjonsnivå fra januar 2020 skal være definert`() {
        shouldNotThrow<Throwable> {
            satsFactoryTestPåDato().høyAlder(januar(2020))
        }
    }

    @Test
    fun `ordinært garantipensjonsnivå mellom januar 2020 og mai 2020 skal være 176 099 kr`() {
        satsFactoryTestPåDato().ordinærAlder(januar(2020)).satsPerÅr.intValueExact() shouldBe 176099
        satsFactoryTestPåDato().ordinærAlder(april(2020)).satsPerÅr.intValueExact() shouldBe 176099
    }

    @Test
    fun `høyt garantipensjonsnivå mellom januar 2020 og mai 2020 skal være 190 368 kr`() {
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
            toProsentAvHøyForMåned = createToProsentAvHøyForMåned(BigDecimal("320.2083333333333333333333333333333")),
        )

        val mai = satsFactoryTestPåDato().ordinærAlder(mai(2020))
        mai.shouldBeEqualToIgnoringFields(
            expected(mai(2020)),
            FullSupplerendeStønadForMåned.Alder::toProsentAvHøyForMåned,
            FullSupplerendeStønadForMåned.Alder::sats,
        )
        mai.toProsentAvHøyForMåned.shouldBeEqualToIgnoringFields(
            expected(mai(2020)).toProsentAvHøyForMåned,
            ToProsentAvHøyForMåned::benyttetRegel,
        )

        val juli = satsFactoryTestPåDato().ordinærAlder(juli(2020))
        juli.shouldBeEqualToIgnoringFields(
            expected(juli(2020)),
            FullSupplerendeStønadForMåned.Alder::toProsentAvHøyForMåned,
            FullSupplerendeStønadForMåned.Alder::sats,
        )
        juli.toProsentAvHøyForMåned.shouldBeEqualToIgnoringFields(
            expected(juli(2020)).toProsentAvHøyForMåned,
            ToProsentAvHøyForMåned::benyttetRegel,
        )
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

fun createToProsentAvHøyForMåned(
    verdi: BigDecimal,
    tidspunkt: Tidspunkt = fixedTidspunkt,
    regel: Regelspesifiseringer = Regelspesifiseringer.REGEL_TO_PROSENT_AV_HØY_SATS_UFØRE,
) =
    ToProsentAvHøyForMåned.Uføre(
        verdi = verdi,
        benyttetRegel = mutableListOf(
            Regelspesifsering(
                kode = regel.kode,
                versjon = regel.versjon,
                benyttetTidspunkt = tidspunkt,
            ),
        ),
    )
