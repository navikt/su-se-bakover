package no.nav.su.se.bakover.domain.oppdrag.utbetaling

import arrow.core.NonEmptyList
import arrow.core.left
import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Rekkefølge
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.november
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.april
import no.nav.su.se.bakover.common.periode.august
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.november
import no.nav.su.se.bakover.common.periode.år
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
import no.nav.su.se.bakover.test.utbetalingslinjeNy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

internal class UtbetalingsstrategiStansTest {

    private val fixedClock = Clock.fixed(15.juni(2020).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

    @Test
    fun `stans av utbetaling`() {
        val utbetalingslinje = utbetalingslinjeNy(
            periode = januar(2020)..desember(2020),
            beløp = 1500,
            uføregrad = 50,
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
            rekkefølge = Rekkefølge.start(),
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
                    utbetalingslinjeSomSkalEndres = utbetalingslinjeNy(
                        beløp = 5000,
                        periode = år(2020),
                    ),
                    virkningstidspunkt = 1.mai(2020),
                    clock = fixedClock,
                    rekkefølge = Rekkefølge.start(),
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
                    utbetalingslinjeSomSkalEndres = utbetalingslinjeNy(
                        periode = år(2020),
                        beløp = 0,
                    ),
                    virkningsperiode = år(2020),
                    clock = fixedClock,
                    rekkefølge = Rekkefølge.start(),
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
                utbetalingslinjeNy(
                    periode = år(2020),
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
                utbetalingslinjeNy(
                    periode = år(2020),
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
                utbetalingslinjeNy(
                    periode = januar(2020)..desember(2021),
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
            )
                .generer() shouldBe Utbetalingsstrategi.Stans.Feil.StansDatoErIkkeFørsteDatoIInneværendeEllerNesteMåned.left()
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
                utbetalingslinjeNy(
                    periode = august(2021)..april(2022),
                ),
            ),
        )
        val opphør = createUtbetaling(
            nonEmptyListOf(
                Utbetalingslinje.Endring.Opphør(
                    utbetalingslinjeSomSkalEndres = første.sisteUtbetalingslinje(),
                    virkningsperiode = august(2021)..april(2022),
                    clock = Clock.systemUTC(),
                    rekkefølge = Rekkefølge.start(),
                ),
            ),
        )
        val andre = createUtbetaling(
            nonEmptyListOf(
                utbetalingslinjeNy(
                    periode = november(2021)..april(2022),
                    forrigeUtbetalingslinjeId = opphør.sisteUtbetalingslinje().id,
                    beløp = 10000,
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
                utbetalingslinjeNy(
                    periode = august(2021)..april(2022),
                ),
            ),
            clock = fixedClock,
        )
        val opphør = createUtbetaling(
            nonEmptyListOf(
                Utbetalingslinje.Endring.Opphør(
                    utbetalingslinjeSomSkalEndres = første.sisteUtbetalingslinje(),
                    virkningsperiode = Periode.create(
                        1.november(2021),
                        30.april(2022),
                    ),
                    clock = fixedClock.plus(
                        1,
                        ChronoUnit.SECONDS,
                    ),
                    rekkefølge = Rekkefølge.start(),
                ),
            ),
            clock = fixedClock.plus(
                1,
                ChronoUnit.SECONDS,
            ),
        )
        val andre = createUtbetaling(
            nonEmptyListOf(
                utbetalingslinjeNy(
                    opprettet = fixedTidspunkt.plus(
                        2,
                        ChronoUnit.SECONDS,
                    ),
                    periode = november(2021)..april(2022),
                    forrigeUtbetalingslinjeId = opphør.sisteUtbetalingslinje().id,
                    beløp = 10000,
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
                        utbetaling = null,
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
