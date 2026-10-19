package no.nav.su.se.bakover.domain.jobcontext

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.domain.job.NameAndYearMonthId
import no.nav.su.se.bakover.common.domain.tid.desember
import no.nav.su.se.bakover.common.domain.tid.februar
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.november
import no.nav.su.se.bakover.common.domain.tid.oktober
import no.nav.su.se.bakover.common.domain.tid.september
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.jobcontext.SendPåminnelseNyStønadsperiodeContext.Påminnelsesvurdering
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import java.time.Month
import java.time.YearMonth

internal class SendPåminnelseNyStønadsperiodeContextTest {

    @Test
    fun `id er basert på jobbnavn og måned`() {
        val førsteJanuar = fixedClockAt(1.januar(2021))
        val fjortendeJanuar = fixedClockAt(14.januar(2021))
        val trettiførsteJanuar = fixedClockAt(31.januar(2021))

        SendPåminnelseNyStønadsperiodeContext.genererIdForTidspunkt(førsteJanuar) shouldBe SendPåminnelseNyStønadsperiodeContext.genererIdForTidspunkt(
            fjortendeJanuar,
        ).also {
            it.name shouldBe "SendPåminnelseNyStønadsperiode"
            it.yearMonth shouldBe YearMonth.of(2021, Month.JANUARY)
        }
        SendPåminnelseNyStønadsperiodeContext.genererIdForTidspunkt(førsteJanuar) shouldBe SendPåminnelseNyStønadsperiodeContext.genererIdForTidspunkt(
            trettiførsteJanuar,
        )
        SendPåminnelseNyStønadsperiodeContext.genererIdForTidspunkt(fjortendeJanuar) shouldBe SendPåminnelseNyStønadsperiodeContext.genererIdForTidspunkt(
            trettiførsteJanuar,
        )

        val femteFebruar = fixedClockAt(5.februar(2021))

        SendPåminnelseNyStønadsperiodeContext.genererIdForTidspunkt(førsteJanuar) shouldNotBe SendPåminnelseNyStønadsperiodeContext.genererIdForTidspunkt(
            femteFebruar,
        ).also {
            it.name shouldBe "SendPåminnelseNyStønadsperiode"
            it.yearMonth shouldBe YearMonth.of(2021, Month.FEBRUARY)
        }
    }

    @Test
    fun `sender påminnelse måneden før ytelsen utløper`() {
        val clock = TikkendeKlokke()
        val (sak: Sak, _, _) = søknadsbehandlingIverksattInnvilget(clock = clock)

        SendPåminnelseNyStønadsperiodeContext(
            id = NameAndYearMonthId(
                name = "SendPåminnelseNyStønadsperiode",
                yearMonth = YearMonth.of(2021, Month.NOVEMBER),
            ),
            opprettet = Tidspunkt.now(clock),
            endret = Tidspunkt.now(clock),
            prosessert = setOf(),
            sendt = setOf(),
            feilede = listOf(),
        ).skalSendePåminnelse(sak, person()) shouldBe Påminnelsesvurdering.SkalSendes

        SendPåminnelseNyStønadsperiodeContext(
            id = NameAndYearMonthId(
                name = "SendPåminnelseNyStønadsperiode",
                yearMonth = YearMonth.of(2021, Month.DECEMBER),
            ),
            opprettet = Tidspunkt.now(clock),
            endret = Tidspunkt.now(clock),
            prosessert = setOf(),
            sendt = setOf(),
            feilede = listOf(),
        ).skalSendePåminnelse(sak, person()) shouldBe Påminnelsesvurdering.SkalIkkeSendes
    }

    @Test
    fun `sender ikke påminnelse dersom personen er død`() {
        val clock = TikkendeKlokke()
        val (sak: Sak, _, _) = søknadsbehandlingIverksattInnvilget(clock = clock)
        clock.spolTil(1.november(2021))
        val context = contextForMåned(Month.NOVEMBER, clock)
        val dødsdato = 31.oktober(2021)
        // Stønadsperioden går ut desember 2021, altså etter dødsmåneden, og november er måneden før utløp.
        context.skalSendePåminnelse(sak, person(dødsdato = dødsdato)) shouldBe
            Påminnelsesvurdering.BrukerErDødMedYtelseEtterDødsmåned(dødsdato)

        val actual = context.prosessert(sak.saksnummer, clock)
        actual shouldBe context.copy(
            prosessert = setOf(sak.saksnummer),
            endret = actual.endret(),
        )
    }

    @Test
    fun `lager ikke oppgave for død person i andre måneder enn måneden før utløp`() {
        val clock = TikkendeKlokke()
        val (sak: Sak, _, _) = søknadsbehandlingIverksattInnvilget(clock = clock)

        // Jobben går hver måned. Uten denne begrensningen ville det blitt ny oppgave hver måned.
        contextForMåned(Month.OCTOBER, clock).skalSendePåminnelse(sak, person(dødsdato = 15.september(2021))) shouldBe
            Påminnelsesvurdering.SkalIkkeSendes
        contextForMåned(Month.DECEMBER, clock).skalSendePåminnelse(sak, person(dødsdato = 15.september(2021))) shouldBe
            Påminnelsesvurdering.SkalIkkeSendes
    }

    @Test
    fun `lager ikke oppgave for død person når ytelsen slutter i dødsmåneden`() {
        val clock = TikkendeKlokke()
        val (sak: Sak, _, _) = søknadsbehandlingIverksattInnvilget(clock = clock)

        contextForMåned(Month.NOVEMBER, clock).skalSendePåminnelse(sak, person(dødsdato = 1.desember(2021))) shouldBe
            Påminnelsesvurdering.SkalIkkeSendes
    }

    private fun contextForMåned(måned: Month, clock: TikkendeKlokke) = SendPåminnelseNyStønadsperiodeContext(
        id = NameAndYearMonthId(
            name = "SendPåminnelseNyStønadsperiode",
            yearMonth = YearMonth.of(2021, måned),
        ),
        opprettet = Tidspunkt.now(clock),
        endret = Tidspunkt.now(clock),
        prosessert = setOf(),
        sendt = setOf(),
        feilede = listOf(),
    )
}
