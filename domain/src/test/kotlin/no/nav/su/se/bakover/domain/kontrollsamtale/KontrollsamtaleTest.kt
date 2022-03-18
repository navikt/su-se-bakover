package no.nav.su.se.bakover.domain.kontrollsamtale

import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.test.getOrFail
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class KontrollsamtaleTest {
    private val todayClock = Clock.fixed(20.desember(2021).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

    @Test
    fun `innkallingsdato skal være 4 måneder etter stønadsstart`() {
        val vedtaksdato = LocalDate.of(2021, 12, 15)
        val periode = Periode.create(LocalDate.of(2021, 12, 1), LocalDate.of(2022, 11, 30))

        regnUtInnkallingsdato(periode, vedtaksdato, todayClock) shouldBe LocalDate.of(2022, 4, 1)
    }

    @Test
    fun `innkallingsdato skal være frem i tid`() {
        val vedtaksdato = LocalDate.of(2021, 12, 1)
        val periode = Periode.create(LocalDate.of(2021, 6, 1), LocalDate.of(2022, 6, 30))

        regnUtInnkallingsdato(periode, vedtaksdato, todayClock) shouldBe LocalDate.of(2022, 2, 1)
    }

    @Test
    fun `innkallingsdato skal ikke være 1mnd eller kortere etter opprettelse av vedtak`() {
        val vedtaksdato = LocalDate.of(2021, 12, 1)
        val periode = Periode.create(LocalDate.of(2021, 9, 1), LocalDate.of(2022, 8, 31))

        regnUtInnkallingsdato(periode, vedtaksdato, todayClock) shouldBe LocalDate.of(2022, 2, 1)
    }

    @Test
    fun `innkallingsdato skal ikke være 1mnd før stønadsslutt`() {
        val vedtaksdato = LocalDate.of(2021, 12, 1)
        val periode = Periode.create(LocalDate.of(2021, 9, 1), LocalDate.of(2022, 1, 31))

        regnUtInnkallingsdato(periode, vedtaksdato, todayClock) shouldBe null
    }

    @Test
    fun `innkallingsdato skal være etter dagens dato`() {
        val vedtaksdato = LocalDate.of(2021, 8, 1)
        val periode = Periode.create(LocalDate.of(2021, 9, 1), LocalDate.of(2022, 1, 31))

        regnUtInnkallingsdato(periode, vedtaksdato, todayClock) shouldBe null
    }

    @Test
    fun `innkallingsdato skal fungere for perioder som starter 3 mnd før`() {
        val vedtaksdato = LocalDate.of(2021, 12, 1)
        val periode = Periode.create(LocalDate.of(2021, 9, 1), LocalDate.of(2022, 8, 31))

        regnUtInnkallingsdato(periode, vedtaksdato, todayClock) shouldBe LocalDate.of(2022, 2, 1)
    }

    @Test
    fun `innkallingsdato skal gi null om perioden er for kort til å kalle inn`() {
        val vedtaksdato = LocalDate.of(2021, 12, 1)
        val periode = Periode.create(LocalDate.of(2021, 9, 1), LocalDate.of(2022, 2, 28))

        regnUtInnkallingsdato(periode, vedtaksdato, todayClock) shouldBe null
    }

    @Test
    fun `annullering av en planlagt kontrollsamtale er mulig`() {
        val kontrollsamtale = Kontrollsamtale.opprettNyKontrollsamtale(UUID.randomUUID(), LocalDate.of(2022, 1, 1))
        kontrollsamtale.annuller() shouldBeRight kontrollsamtale.copy(status = Kontrollsamtalestatus.ANNULLERT)
    }

    @Test
    fun `endre innkallingsdato på kontrollsamtale skal også endre frist`() {
        val innkallingsdato = LocalDate.of(2022, 2, 1)
        val kontrollsamtale = Kontrollsamtale.opprettNyKontrollsamtale(UUID.randomUUID(), LocalDate.of(2022, 1, 1))
        kontrollsamtale.endreDato(innkallingsdato).getOrFail() shouldBe kontrollsamtale.copy(innkallingsdato = innkallingsdato, frist = regnUtFristFraInnkallingsdato(innkallingsdato))
    }
}
