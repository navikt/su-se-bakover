package no.nav.su.se.bakover.domain.oppdrag.utbetaling

import arrow.core.NonEmptyList
import arrow.core.left
import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Rekkefølge
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.april
import no.nav.su.se.bakover.common.domain.tid.august
import no.nav.su.se.bakover.common.domain.tid.idag
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.juli
import no.nav.su.se.bakover.common.domain.tid.juni
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.domain.tid.mars
import no.nav.su.se.bakover.common.domain.tid.november
import no.nav.su.se.bakover.common.domain.tid.oktober
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.common.tid.periode.august
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.minAndMaxOf
import no.nav.su.se.bakover.common.tid.periode.november
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.saksnummer
import no.nav.su.se.bakover.test.utbetaling.utbetalingslinjeNy
import org.junit.jupiter.api.Test
import økonomi.domain.avstemming.Avstemmingsnøkkel
import økonomi.domain.kvittering.Kvittering
import økonomi.domain.simulering.Simulering
import økonomi.domain.simulering.SimulertMåned
import økonomi.domain.utbetaling.KunneIkkeGenerereUtbetalingsstrategiForStans
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.Utbetalinger
import økonomi.domain.utbetaling.UtbetalingsinstruksjonForEtterbetalinger
import økonomi.domain.utbetaling.Utbetalingslinje
import økonomi.domain.utbetaling.Utbetalingsrequest
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
            eksisterendeUtbetalinger = Utbetalinger(utbetaling),
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
        val clock = TikkendeKlokke()
        val utbetalingslinjeSomSkalEndres = utbetalingslinjeNy(
            beløp = 5000,
            periode = år(2020),
            clock = clock,
        )
        val utbetaling = createUtbetaling(
            utbetalingslinjer = nonEmptyListOf(
                utbetalingslinjeSomSkalEndres,
                Utbetalingslinje.Endring.Stans(
                    utbetalingslinjeSomSkalEndres = utbetalingslinjeSomSkalEndres,
                    virkningstidspunkt = 1.mai(2020),
                    clock = clock,
                    rekkefølge = Rekkefølge.skip(0),
                ),
            ),
            clock = clock,
        )

        Utbetalingsstrategi.Stans(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            eksisterendeUtbetalinger = Utbetalinger(utbetaling),
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            stansDato = 1.juli(2020),
            clock = fixedClock,
            sakstype = Sakstype.UFØRE,
        ).generer() shouldBe KunneIkkeGenerereUtbetalingsstrategiForStans.SisteUtbetalingErEnStans.left()
    }

    @Test
    fun `det er ikke lov å stanse en utbetaling som allerede er opphørt`() {
        val clock = TikkendeKlokke()
        val førsteUtbetalingslinje = utbetalingslinjeNy(
            periode = år(2020),
            beløp = 1000,
            clock = clock,
        )
        val utbetaling = createUtbetaling(
            utbetalingslinjer = nonEmptyListOf(
                førsteUtbetalingslinje,
                Utbetalingslinje.Endring.Opphør(
                    utbetalingslinjeSomSkalEndres = førsteUtbetalingslinje,
                    virkningsperiode = år(2020),
                    clock = clock,
                    rekkefølge = Rekkefølge.skip(0),
                ),
            ),
            clock = clock,
        )

        Utbetalingsstrategi.Stans(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            eksisterendeUtbetalinger = Utbetalinger(utbetaling),
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            stansDato = 1.juli(2020),
            clock = fixedClock,
            sakstype = Sakstype.UFØRE,
        ).generer() shouldBe KunneIkkeGenerereUtbetalingsstrategiForStans.SisteUtbetalingErOpphør.left()
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
            eksisterendeUtbetalinger = Utbetalinger(utbetaling),
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            stansDato = 1.juli(2021),
            clock = fixedClock,
            sakstype = Sakstype.UFØRE,
        ).generer() shouldBe KunneIkkeGenerereUtbetalingsstrategiForStans.IngenUtbetalingerEtterStansDato.left()
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
            eksisterendeUtbetalinger = Utbetalinger(utbetaling),
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            stansDato = 10.juli(2020),
            clock = fixedClock,
            sakstype = Sakstype.UFØRE,
        ).generer() shouldBe KunneIkkeGenerereUtbetalingsstrategiForStans.StansDatoErIkkeFørsteDatoIInneværendeEllerNesteMåned.left()
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
                eksisterendeUtbetalinger = Utbetalinger(utbetaling),
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
                eksisterendeUtbetalinger = Utbetalinger(utbetaling),
                behandler = NavIdentBruker.Saksbehandler("Z123"),
                stansDato = it,
                clock = fixedClock,
                sakstype = Sakstype.UFØRE,
            )
                .generer() shouldBe KunneIkkeGenerereUtbetalingsstrategiForStans.StansDatoErIkkeFørsteDatoIInneværendeEllerNesteMåned.left()
        }
    }

    @Test
    fun `svarer med feil dersom det eksisterer tidligere opphør i perioden mellom stansdato og nyeste utbetaling`() {
        val clock15Juli21 = TikkendeKlokke(
            Clock.fixed(
                15.juli(2021).atStartOfDay().toInstant(ZoneOffset.UTC),
                ZoneOffset.UTC,
            ),
        )

        val første = createUtbetaling(
            utbetalingslinjer = nonEmptyListOf(
                utbetalingslinjeNy(
                    periode = august(2021)..april(2022),
                    clock = clock15Juli21,
                ),
            ),
            clock = clock15Juli21,
        )
        val opphør = createUtbetaling(
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Endring.Opphør(
                    utbetalingslinjeSomSkalEndres = første.sisteUtbetalingslinje(),
                    virkningsperiode = august(2021)..april(2022),
                    clock = clock15Juli21,
                    rekkefølge = Rekkefølge.start(),
                ),
            ),
            clock = clock15Juli21,
        )
        val andre = createUtbetaling(
            utbetalingslinjer = nonEmptyListOf(
                utbetalingslinjeNy(
                    periode = november(2021)..april(2022),
                    forrigeUtbetalingslinjeId = opphør.sisteUtbetalingslinje().id,
                    beløp = 10000,
                    clock = clock15Juli21,
                ),
            ),
            clock = clock15Juli21,
        )

        Utbetalingsstrategi.Stans(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            eksisterendeUtbetalinger = Utbetalinger(
                første,
                opphør,
                andre,
            ),
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            stansDato = 1.august(2021),
            clock = clock15Juli21,
            sakstype = Sakstype.UFØRE,
        ).generer() shouldBe KunneIkkeGenerereUtbetalingsstrategiForStans.KanIkkeStanseOpphørtePerioder.left()
    }

    @Test
    fun `svarer med feil dersom det eksisterer fremtidige opphør i perioden mellom stansdato og nyeste utbetaling`() {
        val clock15Juli21 = TikkendeKlokke(
            Clock.fixed(
                15.juli(2021).atStartOfDay().toInstant(ZoneOffset.UTC),
                ZoneOffset.UTC,
            ),
        )

        val første = createUtbetaling(
            nonEmptyListOf(
                utbetalingslinjeNy(
                    periode = august(2021)..april(2022),
                    clock = clock15Juli21,
                ),
            ),
            clock = clock15Juli21,
        )
        val opphør = createUtbetaling(
            nonEmptyListOf(
                Utbetalingslinje.Endring.Opphør(
                    utbetalingslinjeSomSkalEndres = første.sisteUtbetalingslinje(),
                    virkningsperiode = Periode.create(
                        1.november(2021),
                        30.april(2022),
                    ),
                    clock = clock15Juli21,
                    rekkefølge = Rekkefølge.start(),
                ),
            ),
            clock = clock15Juli21,
        )
        val tredje = createUtbetaling(
            nonEmptyListOf(
                utbetalingslinjeNy(
                    opprettet = Tidspunkt.now(clock15Juli21),
                    periode = november(2021)..april(2022),
                    forrigeUtbetalingslinjeId = opphør.sisteUtbetalingslinje().id,
                    beløp = 10000,
                ),
            ),
            clock = clock15Juli21,
        )

        Utbetalingsstrategi.Stans(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            eksisterendeUtbetalinger = Utbetalinger(
                første,
                opphør,
                tredje,
            ),
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            stansDato = 1.august(2021),
            clock = clock15Juli21,
            sakstype = Sakstype.UFØRE,
        ).generer() shouldBe KunneIkkeGenerereUtbetalingsstrategiForStans.KanIkkeStanseOpphørtePerioder.left()
    }

    @Test
    fun `stans over 2 utbetalingslinjer`() {
        val clock = TikkendeKlokke(fixedClockAt(1.januar(2021)))
        val førsteNy = utbetalingslinjeNy(
            clock = clock,
            periode = januar(2021),
        )
        val andreNy = utbetalingslinjeNy(
            clock = clock,
            periode = februar(2021),
            forrigeUtbetalingslinjeId = førsteNy.id,
            rekkefølge = Rekkefølge.ANDRE,
        )
        val førsteUtbetaling = createUtbetaling(
            nonEmptyListOf(
                førsteNy,
                andreNy,
            ),
            clock = clock,
        )
        /**
         * Either.Right(UtbetalingForSimulering(id=d257c153-75f0-4480-958f-941001, opprettet=2021-01-01T01:02:11.456789Z, sakId=47b96ad2-11a5-4900-a251-627259362975, saksnummer=12345676, fnr=74124290794, utbetalingslinjer=NonEmptyList(Stans(id=25e19df3-180a-47ca-bffa-c1975e, opprettet=2021-01-01T01:02:11.456789Z, rekkefølge=Rekkefølge(value=0), fraOgMed=2021-02-01, tilOgMed=2021-02-28, forrigeUtbetalingslinjeId=064c957a-cacd-44e8-8907-12c304, beløp=15000, virkningsperiode=Periode(fraOgMed=2021-01-01, tilOgMed=2021-02-28), uføregrad=Uføregrad(value=50), utbetalingsinstruksjonForEtterbetalinger=SåFortSomMulig)), behandler=Z123, avstemmingsnøkkel=1609462931456789000, sakstype=UFØRE))
         */
        Utbetalingsstrategi.Stans(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            eksisterendeUtbetalinger = Utbetalinger(førsteUtbetaling),
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            stansDato = 1.januar(2021),
            clock = clock,
            sakstype = Sakstype.UFØRE,
        ).generer().getOrNull()!!.utbetalingslinjer.single() shouldBe Utbetalingslinje.Endring.Stans(
            id = andreNy.id,
            opprettet = andreNy.opprettet.plus(6, ChronoUnit.SECONDS),
            rekkefølge = Rekkefølge.start(),
            fraOgMed = andreNy.periode.fraOgMed,
            tilOgMed = andreNy.periode.tilOgMed,
            forrigeUtbetalingslinjeId = andreNy.forrigeUtbetalingslinjeId,
            beløp = andreNy.beløp,
            virkningsperiode = Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = andreNy.periode.tilOgMed,
            ),
            uføregrad = andreNy.uføregrad,
            utbetalingsinstruksjonForEtterbetalinger = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
        )
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
                måneder = SimulertMåned.create(utbetalingslinjer.map { it.periode }.minAndMaxOf().måneder()),
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
