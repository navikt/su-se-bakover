package no.nav.su.se.bakover.domain.oppdrag

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.left
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import no.nav.su.se.bakover.common.Rekkefølge
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.april
import no.nav.su.se.bakover.common.domain.tid.august
import no.nav.su.se.bakover.common.domain.tid.desember
import no.nav.su.se.bakover.common.domain.tid.februar
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.domain.tid.september
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.utbetaling.utbetalingslinjeNy
import org.junit.jupiter.api.Test
import vilkår.uføre.domain.Uføregrad
import økonomi.domain.avstemming.Avstemmingsnøkkel
import økonomi.domain.utbetaling.ForrigeUtbetalingslinjeKoblendeListe
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.Utbetalinger
import økonomi.domain.utbetaling.Utbetalingslinje
import økonomi.domain.utbetaling.UtbetalingslinjePåTidslinje
import økonomi.domain.utbetaling.hentGjeldendeUtbetaling
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
    fun `finner ikke gjeldende utbetaling for en tom liste`() {
        Utbetalinger().hentGjeldendeUtbetaling(
            forDato = fixedLocalDate,
        ) shouldBe Utbetalinger.FantIkkeGjeldendeUtbetaling.left()
    }

    @Test
    fun `finner ikke gjeldende utbetaling for dato utenfor tidslinja`() {
        Utbetalinger(createUtbetaling()).hentGjeldendeUtbetaling(
            forDato = 1.februar(2021),
        ) shouldBe Utbetalinger.FantIkkeGjeldendeUtbetaling.left()
    }

    @Test
    fun `finner gjeldende utbetaling for dato innenfor tidslinja`() {
        Utbetalinger(createUtbetaling()).hentGjeldendeUtbetaling(
            forDato = 31.januar(2021),
        ).shouldBeTypeOf<Either.Right<UtbetalingslinjePåTidslinje.Ny>>()
    }

    private fun createUtbetaling(
        opprettet: Tidspunkt = fixedTidspunkt,
        utbetalingsLinjer: NonEmptyList<Utbetalingslinje> = createUtbetalingslinjer(opprettet),
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
        opprettet: Tidspunkt = fixedTidspunkt,
        rekkefølge: Rekkefølge = Rekkefølge.start(),
    ) = utbetalingslinjeNy(
        periode = Periode.create(fraOgMed, tilOgMed),
        beløp = beløp,
        forrigeUtbetalingslinjeId = forrigeUtbetalingslinjeId,
        uføregrad = uføregrad.value,
        opprettet = opprettet,
        rekkefølge = rekkefølge,
    )

    private fun createUtbetalingslinjer(
        utbetalingOpprettet: Tidspunkt = fixedTidspunkt,
    ): NonEmptyList<Utbetalingslinje> {
        val rekkefølge = Rekkefølge.generator()
        return ForrigeUtbetalingslinjeKoblendeListe(
            listOf(
                createUtbetalingslinje(
                    fraOgMed = 1.januar(2019),
                    tilOgMed = 30.april(2020),
                    opprettet = utbetalingOpprettet.plusUnits(1),
                    rekkefølge = rekkefølge.neste(),
                ),
                createUtbetalingslinje(
                    fraOgMed = 1.mai(2020),
                    tilOgMed = 31.august(2020),
                    opprettet = utbetalingOpprettet.plusUnits(2),
                    rekkefølge = rekkefølge.neste(),
                ),
                createUtbetalingslinje(
                    fraOgMed = 1.september(2020),
                    tilOgMed = 31.januar(2021),
                    opprettet = utbetalingOpprettet.plusUnits(3),
                    rekkefølge = rekkefølge.neste(),
                ),
            ),
        ).toNonEmptyList()
    }
}
