package no.nav.su.se.bakover.domain.oppdrag

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.left
import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.september
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.utbetalingslinje
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

internal class UtbetalingTest {

    @Test
    fun `tidligste og seneste dato`() {
        createUtbetaling().tidligsteDato() shouldBe 1.januar(2019)
        createUtbetaling().senesteDato() shouldBe 31.januar(2021)
    }

    @Test
    fun `brutto beløp`() {
        createUtbetaling().bruttoBeløp() shouldBe 1500
    }

    @Test
    fun `er førstegangsutbetaling`() {
        createUtbetaling(utbetalingsLinjer = nonEmptyListOf(createUtbetalingslinje(forrigeUtbetalingslinjeId = null)))
            .erFørstegangsUtbetaling() shouldBe true

        createUtbetaling(utbetalingsLinjer = nonEmptyListOf(createUtbetalingslinje(forrigeUtbetalingslinjeId = UUID30.randomUUID())))
            .erFørstegangsUtbetaling() shouldBe false

        createUtbetaling(
            utbetalingsLinjer = nonEmptyListOf(
                createUtbetalingslinje(forrigeUtbetalingslinjeId = null),
                createUtbetalingslinje(forrigeUtbetalingslinjeId = UUID30.randomUUID()),
            ),
        ).erFørstegangsUtbetaling() shouldBe true

        createUtbetaling(
            utbetalingsLinjer = nonEmptyListOf(
                createUtbetalingslinje(forrigeUtbetalingslinjeId = UUID30.randomUUID()),
                createUtbetalingslinje(forrigeUtbetalingslinjeId = UUID30.randomUUID()),
            ),
        ).erFørstegangsUtbetaling() shouldBe false
    }

    @Test
    fun `endring av første utbetalingslinje er ikke det samme som førstegangsutbetaling`() {
        val førsteUtbetalingslinje = createUtbetalingslinje()

        createUtbetaling(utbetalingsLinjer = nonEmptyListOf(førsteUtbetalingslinje))
            .also { it.erFørstegangsUtbetaling() shouldBe true }

        val opphør = Utbetalingslinje.Endring.Opphør(
            utbetalingslinje = førsteUtbetalingslinje,
            virkningsperiode = førsteUtbetalingslinje.periode,
            clock = fixedClock,
        )
        createUtbetaling(utbetalingsLinjer = nonEmptyListOf(opphør))
            .also { it.erFørstegangsUtbetaling() shouldBe false }
    }

    @Test
    fun `finner ikke gjeldende utbetaling for en tom liste`() {
        emptyList<Utbetaling>().hentGjeldendeUtbetaling(
            forDato = fixedLocalDate,
            clock = fixedClock,
        ) shouldBe FantIkkeGjeldendeUtbetaling.left()
    }

    @Test
    fun `finner ikke gjeldende utbetaling for dato utenfor tidslinja`() {
        listOf(createUtbetaling()).hentGjeldendeUtbetaling(
            forDato = 1.februar(2021),
            clock = fixedClock,
        ) shouldBe FantIkkeGjeldendeUtbetaling.left()
    }

    @Test
    fun `finner gjeldende utbetaling for dato innenfor tidslinja`() {
        listOf(createUtbetaling()).hentGjeldendeUtbetaling(
            forDato = 31.januar(2021),
            clock = fixedClock,
        ).shouldBeTypeOf<Either.Right<UtbetalingslinjePåTidslinje.Ny>>()
    }

    private fun createUtbetaling(
        opprettet: Tidspunkt = fixedTidspunkt,
        utbetalingsLinjer: NonEmptyList<Utbetalingslinje> = createUtbetalingslinjer(),
    ) = Utbetaling.UtbetalingForSimulering(
        opprettet = opprettet,
        sakId = UUID.randomUUID(),
        saksnummer = Saksnummer(2021),
        fnr = Fnr.generer(),
        utbetalingslinjer = utbetalingsLinjer,
        behandler = NavIdentBruker.Saksbehandler("Z123"),
        avstemmingsnøkkel = Avstemmingsnøkkel(opprettet = fixedTidspunkt),
        sakstype = Sakstype.UFØRE,
    )

    private fun createUtbetalingslinje(
        fraOgMed: LocalDate = 1.januar(2020),
        tilOgMed: LocalDate = 31.desember(2020),
        beløp: Int = 500,
        forrigeUtbetalingslinjeId: UUID30? = null,
        uføregrad: Uføregrad = Uføregrad.parse(50),
    ) = utbetalingslinje(
        periode = Periode.create(fraOgMed, tilOgMed),
        beløp = beløp,
        forrigeUtbetalingslinjeId = forrigeUtbetalingslinjeId,
        uføregrad = uføregrad.value
    )

    private fun createUtbetalingslinjer() = nonEmptyListOf(
        createUtbetalingslinje(
            fraOgMed = 1.januar(2019),
            tilOgMed = 30.april(2020)
        ),
        createUtbetalingslinje(
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.august(2020),
        ),
        createUtbetalingslinje(
            fraOgMed = 1.september(2020),
            tilOgMed = 31.januar(2021),
        )
    )
}
