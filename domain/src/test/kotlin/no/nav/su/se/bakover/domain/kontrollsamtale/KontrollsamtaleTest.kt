package no.nav.su.se.bakover.domain.kontrollsamtale

import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.november
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.september
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.ZoneOffset
import java.util.UUID

class KontrollsamtaleTest {
    private val todayClock = Clock.fixed(20.desember(2021).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

    @Test
    fun `innkallingsdato skal være 4 måneder etter stønadsstart`() {
        val vedtaksdato = 15.desember(2021)
        val periode = Periode.create(1.desember(2021), 30.november(2022))

        regnUtInnkallingsdato(periode, vedtaksdato, todayClock) shouldBe 1.april(2022)
    }

    @Test
    fun `innkallingsdato skal være frem i tid`() {
        val vedtaksdato = 1.desember(2021)
        val periode = Periode.create(1.juni(2021), 30.juni(2022))

        regnUtInnkallingsdato(periode, vedtaksdato, todayClock) shouldBe 1.februar(2022)
    }

    @Test
    fun `innkallingsdato skal ikke være 1mnd eller kortere etter opprettelse av vedtak`() {
        val vedtaksdato = 1.desember(2021)
        val periode = Periode.create(1.september(2021), 31.august(2022))

        regnUtInnkallingsdato(periode, vedtaksdato, todayClock) shouldBe 1.februar(2022)
    }

    @Test
    fun `innkallingsdato skal ikke være 1mnd før stønadsslutt`() {
        val vedtaksdato = 1.desember(2021)
        val periode = Periode.create(1.september(2021), 31.januar(2022))

        regnUtInnkallingsdato(periode, vedtaksdato, todayClock) shouldBe null
    }

    @Test
    fun `innkallingsdato skal være etter dagens dato`() {
        val vedtaksdato = 1.august(2021)
        val periode = Periode.create(1.september(2021), 31.januar(2022))

        regnUtInnkallingsdato(periode, vedtaksdato, todayClock) shouldBe null
    }

    @Test
    fun `innkallingsdato skal fungere for perioder som starter 3 mnd før`() {
        val vedtaksdato = 1.desember(2021)
        val periode = Periode.create(1.september(2021), 31.august(2022))

        regnUtInnkallingsdato(periode, vedtaksdato, todayClock) shouldBe 1.februar(2022)
    }

    @Test
    fun `innkallingsdato skal gi null om perioden er for kort til å kalle inn`() {
        val vedtaksdato = 1.desember(2021)
        val periode = Periode.create(1.september(2021), 28.februar(2022))

        regnUtInnkallingsdato(periode, vedtaksdato, todayClock) shouldBe null
    }

    @Test
    fun `annullering av en planlagt kontrollsamtale er mulig`() {
        val kontrollsamtale = Kontrollsamtale.opprettNyKontrollsamtale(
            sakId = UUID.randomUUID(),
            innkallingsdato = 1.januar(2022),
            clock = fixedClock,
        )
        kontrollsamtale.annuller() shouldBeRight kontrollsamtale.copy(status = Kontrollsamtalestatus.ANNULLERT)
    }

    @Test
    fun `endre innkallingsdato på kontrollsamtale skal også endre frist`() {
        val innkallingsdato = 1.februar(2022)
        val kontrollsamtale = Kontrollsamtale.opprettNyKontrollsamtale(
            sakId = UUID.randomUUID(),
            innkallingsdato = 1.januar(2022),
            clock = fixedClock,
        )
        kontrollsamtale.endreDato(innkallingsdato).getOrFail() shouldBe kontrollsamtale.copy(innkallingsdato = innkallingsdato, frist = regnUtFristFraInnkallingsdato(innkallingsdato))
    }
}
