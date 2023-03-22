package no.nav.su.se.bakover.domain.oppdrag.utbetaling

import arrow.core.NonEmptyList
import arrow.core.left
import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.november
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingsinstruksjonForEtterbetalinger
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertPeriode
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.plus
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.saksnummer
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

internal class UtbetalingsstrategiStansTest {

    private val fixedClock = Clock.fixed(15.juni(2020).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

    @Test
    fun `stans av utbetaling`() {
        val utbetalingslinje = Utbetalingslinje.Ny(
            opprettet = fixedTidspunkt,
            fraOgMed = 1.januar(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1500,
            uføregrad = Uføregrad.parse(50),
        )
        val utbetaling = createUtbetaling(
            nonEmptyListOf(
                utbetalingslinje,
            ),
        )

        val stans = Utbetalingsstrategi.Stans(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            eksisterendeUtbetalinger = listOf(utbetaling),
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            stansDato = 1.juli(2020),
            clock = fixedClock,
            sakstype = Sakstype.UFØRE,
        ).generer().getOrFail()

        stans.utbetalingslinjer[0] shouldBe Utbetalingslinje.Endring.Stans(
            id = utbetalingslinje.id,
            opprettet = stans.utbetalingslinjer[0].opprettet,
            fraOgMed = utbetalingslinje.periode.fraOgMed,
            tilOgMed = utbetalingslinje.periode.tilOgMed,
            forrigeUtbetalingslinjeId = utbetalingslinje.forrigeUtbetalingslinjeId,
            beløp = utbetalingslinje.beløp,
            virkningsperiode = Periode.create(
                fraOgMed = 1.juli(2020),
                tilOgMed = utbetalingslinje.periode.tilOgMed,
            ),
            uføregrad = utbetalingslinje.uføregrad,
            utbetalingsinstruksjonForEtterbetalinger = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
        )
    }

    @Test
    fun `siste utbetaling er en 'stans utbetaling'`() {
        val utbetaling = createUtbetaling(
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Endring.Stans(
                    utbetalingslinje = Utbetalingslinje.Ny(
                        opprettet = fixedTidspunkt,
                        fraOgMed = 1.januar(2020),
                        tilOgMed = 31.desember(2020),
                        forrigeUtbetalingslinjeId = null,
                        beløp = 5000,
                        uføregrad = Uføregrad.parse(50),
                    ),
                    virkningstidspunkt = 1.mai(2020),
                    clock = fixedClock,
                ),
            ),
        )

        Utbetalingsstrategi.Stans(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            eksisterendeUtbetalinger = listOf(utbetaling),
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            stansDato = 1.juli(2020),
            clock = fixedClock,
            sakstype = Sakstype.UFØRE,
        ).generer() shouldBe Utbetalingsstrategi.Stans.Feil.SisteUtbetalingErEnStans.left()
    }

    @Test
    fun `det er ikke lov å stanse en utbetaling som allerede er opphørt`() {
        val utbetaling = createUtbetaling(
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Endring.Opphør(
                    utbetalingslinje = Utbetalingslinje.Ny(
                        opprettet = fixedTidspunkt,
                        fraOgMed = 1.januar(2020),
                        tilOgMed = 31.desember(2020),
                        forrigeUtbetalingslinjeId = null,
                        beløp = 0,
                        uføregrad = Uføregrad.parse(50),
                    ),
                    virkningsperiode = Periode.create(
                        1.januar(2020),
                        31.desember(2020),
                    ),
                    clock = fixedClock,
                ),
            ),
        )

        Utbetalingsstrategi.Stans(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            eksisterendeUtbetalinger = listOf(utbetaling),
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            stansDato = 1.juli(2020),
            clock = fixedClock,
            sakstype = Sakstype.UFØRE,
        ).generer() shouldBe Utbetalingsstrategi.Stans.Feil.SisteUtbetalingErOpphør.left()
    }

    @Test
    fun `har ingen utbetalinger senere enn stansdato`() {
        val utbetaling = createUtbetaling(
            nonEmptyListOf(
                Utbetalingslinje.Ny(
                    opprettet = fixedTidspunkt,
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.desember(2020),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 15000,
                    uføregrad = Uføregrad.parse(50),
                ),
            ),
        )

        Utbetalingsstrategi.Stans(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            eksisterendeUtbetalinger = listOf(utbetaling),
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            stansDato = 1.juli(2021),
            clock = fixedClock,
            sakstype = Sakstype.UFØRE,
        ).generer() shouldBe Utbetalingsstrategi.Stans.Feil.IngenUtbetalingerEtterStansDato.left()
    }

    @Test
    fun `stansdato må være den første i måneden`() {
        val utbetaling = createUtbetaling(
            nonEmptyListOf(
                Utbetalingslinje.Ny(
                    opprettet = fixedTidspunkt,
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.desember(2020),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 15000,
                    uføregrad = Uføregrad.parse(50),
                ),
            ),
        )

        Utbetalingsstrategi.Stans(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            eksisterendeUtbetalinger = listOf(utbetaling),
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            stansDato = 10.juli(2020),
            clock = fixedClock,
            sakstype = Sakstype.UFØRE,
        ).generer() shouldBe Utbetalingsstrategi.Stans.Feil.StansDatoErIkkeFørsteDatoIInneværendeEllerNesteMåned.left()
    }

    @Test
    fun `stansdato kan være den første i inneværende eller neste måned`() {
        val utbetaling = createUtbetaling(
            nonEmptyListOf(
                Utbetalingslinje.Ny(
                    opprettet = fixedTidspunkt,
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.desember(2021),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 15000,
                    uføregrad = Uføregrad.parse(50),
                ),
            ),
        )

        listOf(
            1.juni(2020),
            1.juli(2020),
        ).forEach {
            Utbetalingsstrategi.Stans(
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                eksisterendeUtbetalinger = listOf(utbetaling),
                behandler = NavIdentBruker.Saksbehandler("Z123"),
                stansDato = it,
                clock = fixedClock,
                sakstype = Sakstype.UFØRE,
            ).generer()
        }

        listOf(
            1.januar(2020),
            31.mars(2020),
            15.juni(2020),
            1.oktober(2020),
            1.juni(2021),
            1.juli(2021),
        ).forEach {
            Utbetalingsstrategi.Stans(
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                eksisterendeUtbetalinger = listOf(utbetaling),
                behandler = NavIdentBruker.Saksbehandler("Z123"),
                stansDato = it,
                clock = fixedClock,
                sakstype = Sakstype.UFØRE,
            ).generer() shouldBe Utbetalingsstrategi.Stans.Feil.StansDatoErIkkeFørsteDatoIInneværendeEllerNesteMåned.left()
        }
    }

    @Test
    fun `svarer med feil dersom det eksisterer tidligere opphør i perioden mellom stansdato og nyeste utbetaling`() {
        val fixedClock15Juli21 = Clock.fixed(
            15.juli(2021).atStartOfDay().toInstant(ZoneOffset.UTC),
            ZoneOffset.UTC,
        )

        val første = createUtbetaling(
            nonEmptyListOf(
                Utbetalingslinje.Ny(
                    opprettet = fixedTidspunkt,
                    fraOgMed = 1.august(2021),
                    tilOgMed = 30.april(2022),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 15000,
                    uføregrad = Uføregrad.parse(50),
                ),
            ),
        )
        val opphør = createUtbetaling(
            nonEmptyListOf(
                Utbetalingslinje.Endring.Opphør(
                    utbetalingslinje = første.sisteUtbetalingslinje(),
                    virkningsperiode = Periode.create(
                        1.august(2021),
                        30.april(2022),
                    ),
                    clock = Clock.systemUTC(),
                ),
            ),
        )
        val andre = createUtbetaling(
            nonEmptyListOf(
                Utbetalingslinje.Ny(
                    opprettet = fixedTidspunkt,
                    fraOgMed = 1.november(2021),
                    tilOgMed = 30.april(2022),
                    forrigeUtbetalingslinjeId = opphør.sisteUtbetalingslinje().id,
                    beløp = 10000,
                    uføregrad = Uføregrad.parse(50),
                ),
            ),
        )

        Utbetalingsstrategi.Stans(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            eksisterendeUtbetalinger = listOf(
                første,
                opphør,
                andre,
            ),
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            stansDato = 1.august(2021),
            clock = fixedClock15Juli21,
            sakstype = Sakstype.UFØRE,
        ).generer() shouldBe Utbetalingsstrategi.Stans.Feil.KanIkkeStanseOpphørtePerioder.left()
    }

    @Test
    fun `svarer med feil dersom det eksisterer fremtidige opphør i perioden mellom stansdato og nyeste utbetaling`() {
        val fixedClock15Juli21 = Clock.fixed(
            15.juli(2021).atStartOfDay().toInstant(ZoneOffset.UTC),
            ZoneOffset.UTC,
        )

        val første = createUtbetaling(
            nonEmptyListOf(
                Utbetalingslinje.Ny(
                    opprettet = fixedTidspunkt,
                    fraOgMed = 1.august(2021),
                    tilOgMed = 30.april(2022),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 15000,
                    uføregrad = Uføregrad.parse(50),
                ),
            ),
            clock = fixedClock,
        )
        val opphør = createUtbetaling(
            nonEmptyListOf(
                Utbetalingslinje.Endring.Opphør(
                    utbetalingslinje = første.sisteUtbetalingslinje(),
                    virkningsperiode = Periode.create(
                        1.november(2021),
                        30.april(2022),
                    ),
                    clock = fixedClock.plus(
                        1,
                        ChronoUnit.SECONDS,
                    ),
                ),
            ),
            clock = fixedClock.plus(
                1,
                ChronoUnit.SECONDS,
            ),
        )
        val andre = createUtbetaling(
            nonEmptyListOf(
                Utbetalingslinje.Ny(
                    opprettet = fixedTidspunkt.plus(
                        2,
                        ChronoUnit.SECONDS,
                    ),
                    fraOgMed = 1.november(2021),
                    tilOgMed = 30.april(2022),
                    forrigeUtbetalingslinjeId = opphør.sisteUtbetalingslinje().id,
                    beløp = 10000,
                    uføregrad = Uføregrad.parse(50),
                ),
            ),
            clock = fixedClock.plus(
                2,
                ChronoUnit.SECONDS,
            ),
        )

        Utbetalingsstrategi.Stans(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            eksisterendeUtbetalinger = listOf(
                første,
                andre,
                opphør,
            ),
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            stansDato = 1.august(2021),
            clock = fixedClock15Juli21,
            sakstype = Sakstype.UFØRE,
        ).generer() shouldBe Utbetalingsstrategi.Stans.Feil.KanIkkeStanseOpphørtePerioder.left()
    }

    private fun createUtbetaling(
        utbetalingslinjer: NonEmptyList<Utbetalingslinje>,
        clock: Clock = fixedClock,
    ) =
        Utbetaling.UtbetalingForSimulering(
            opprettet = Tidspunkt.now(clock),
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            utbetalingslinjer = utbetalingslinjer,
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            avstemmingsnøkkel = Avstemmingsnøkkel(Tidspunkt.now(clock)),
            sakstype = Sakstype.UFØRE,
        ).toSimulertUtbetaling(
            simulering = Simulering(
                gjelderId = fnr,
                gjelderNavn = "navn",
                datoBeregnet = idag(clock),
                nettoBeløp = 0,
                periodeList = listOf(
                    SimulertPeriode(
                        fraOgMed = utbetalingslinjer.minOf { it.periode.fraOgMed },
                        tilOgMed = utbetalingslinjer.maxOf { it.periode.tilOgMed },
                        utbetaling = listOf(),
                    ),
                ),
                rawResponse = "UtbetalingsstrategiStansTest baserer ikke denne på rå XML.",
            ),
        ).toOversendtUtbetaling(
            oppdragsmelding = Utbetalingsrequest(
                value = "",
            ),
        ).toKvittertUtbetaling(
            kvittering = Kvittering(
                Kvittering.Utbetalingsstatus.OK_MED_VARSEL,
                "",
                mottattTidspunkt = Tidspunkt.now(clock),
            ),
        )
}
