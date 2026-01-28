package no.nav.su.se.bakover.domain.jobcontext

import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.job.NameAndYearMonthId
import no.nav.su.se.bakover.common.domain.tid.april
import no.nav.su.se.bakover.common.domain.tid.desember
import no.nav.su.se.bakover.common.domain.tid.februar
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.mars
import no.nav.su.se.bakover.common.domain.tid.november
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import java.time.LocalDate
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
    fun `sender ikke påminnelse dersom personen er død`() {
        val clock = TikkendeKlokke()
        val (sak: Sak, _, _) = søknadsbehandlingIverksattInnvilget(clock = clock)
        clock.spolTil(1.desember(2021))
        val context = SendPåminnelseNyStønadsperiodeContext(
            id = NameAndYearMonthId(
                name = "SendPåminnelseNyStønadsperiode",
                yearMonth = YearMonth.of(2021, Month.DECEMBER),
            ),
            opprettet = Tidspunkt.now(clock),
            endret = Tidspunkt.now(clock),
            prosessert = setOf(),
            sendt = setOf(),
            feilede = listOf(),
        )
        val actual = context.håndter(
            sak = sak,
            clock = clock,
            sessionFactory = TestSessionFactory(),
            lagDokument = { throw IllegalStateException("Skal ikke komme så langt.") },
            lagreDokument = { _, _ -> },
            lagreContext = { _, _ -> },
            hentPerson = { person(dødsdato = 30.november(2021)).right() },
        ).getOrFail()
        actual shouldBe context.copy(
            prosessert = setOf(sak.saksnummer),
            endret = actual.endret(),
        )
    }

    @Test
    fun `påmminelse sendes basert på inneværende eller enste måned avhengig av om det er før eller etter februar 2026`() {
        val clock = TikkendeKlokke()
        val januarConext = SendPåminnelseNyStønadsperiodeContext(
            id = NameAndYearMonthId(
                name = "SendPåminnelseNyStønadsperiode",
                yearMonth = YearMonth.of(2026, Month.JANUARY),
            ),
            opprettet = Tidspunkt.now(clock),
            endret = Tidspunkt.now(clock),
            prosessert = setOf(),
            sendt = setOf(),
            feilede = listOf(),
        )

        // Send påmminnelse kun hvis utløper inneværende måned
        clock.spolTil(LocalDate.of(2026, 1, 1))
        søknadsbehandlingIverksattInnvilget(
            clock = clock,
            stønadsperiode = Stønadsperiode.create(Periode.create(1.januar(2026), 31.januar(2026))),
        ).let { (sak: Sak, _, _) ->
            januarConext.skalSendePåminnelse(sak, person(), clock) shouldBe true
        }
        søknadsbehandlingIverksattInnvilget(
            clock = clock,
            stønadsperiode = Stønadsperiode.create(Periode.create(1.januar(2026), 28.februar(2026))),
        ).let { (sak: Sak, _, _) ->
            januarConext.skalSendePåminnelse(sak, person(), clock) shouldBe false
        }

        val februarContext = januarConext.copy(
            id = NameAndYearMonthId(
                name = "SendPåminnelseNyStønadsperiode",
                yearMonth = YearMonth.of(2026, Month.FEBRUARY),
            ),
        )
        // Send påmminnelse både hvis utløper inneværende og neste måned
        clock.spolTil(LocalDate.of(2026, 2, 1))
        søknadsbehandlingIverksattInnvilget(
            clock = clock,
            stønadsperiode = Stønadsperiode.create(Periode.create(1.januar(2026), 28.februar(2026))),
        ).let { (sak: Sak, _, _) ->
            februarContext.skalSendePåminnelse(sak, person(), clock) shouldBe true
        }
        søknadsbehandlingIverksattInnvilget(
            clock = clock,
            stønadsperiode = Stønadsperiode.create(Periode.create(1.januar(2026), 31.mars(2026))),
        ).let { (sak: Sak, _, _) ->
            februarContext.skalSendePåminnelse(sak, person(), clock) shouldBe true
        }

        val marsContext = februarContext.copy(
            id = NameAndYearMonthId(
                name = "SendPåminnelseNyStønadsperiode",
                yearMonth = YearMonth.of(2026, Month.MARCH),
            ),
        )
        // Send påmminnelse kun hvis utløper neste måned
        clock.spolTil(LocalDate.of(2026, 3, 1))
        søknadsbehandlingIverksattInnvilget(
            clock = clock,
            stønadsperiode = Stønadsperiode.create(Periode.create(1.januar(2026), 31.mars(2026))),
        ).let { (sak: Sak, _, _) ->
            marsContext.skalSendePåminnelse(sak, person(), clock) shouldBe false
        }
        søknadsbehandlingIverksattInnvilget(
            clock = clock,
            stønadsperiode = Stønadsperiode.create(Periode.create(1.januar(2026), 30.april(2026))),
        ).let { (sak: Sak, _, _) ->
            marsContext.skalSendePåminnelse(sak, person(), clock) shouldBe true
        }
    }
}
