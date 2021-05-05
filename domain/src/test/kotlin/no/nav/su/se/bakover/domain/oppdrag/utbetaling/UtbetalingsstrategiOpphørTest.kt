package no.nav.su.se.bakover.domain.oppdrag.utbetaling

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.ZoneOffset
import java.util.UUID

internal class UtbetalingsstrategiOpphørTest {
    private val fixedClock: Clock = Clock.fixed(1.januar(2021).startOfDay().instant, ZoneOffset.UTC)

    private val fnr = Fnr("12345678910")
    private val sakId = UUID.randomUUID()
    private val saksnummer = Saksnummer(2021)
    private val behandler = NavIdentBruker.Saksbehandler("Z123")

    private val enUtbetalingslinje = Utbetalingslinje.Ny(
        id = UUID30.randomUUID(),
        opprettet = Tidspunkt.MIN,
        fraOgMed = 1.januar(2021),
        tilOgMed = 31.desember(2021),
        forrigeUtbetalingslinjeId = null,
        beløp = 5000,
    )

    private val enUtbetaling = Utbetaling.OversendtUtbetaling.MedKvittering(
        sakId = sakId,
        saksnummer = saksnummer,
        simulering = Simulering(
            gjelderId = fnr,
            gjelderNavn = "navn",
            datoBeregnet = idag(),
            nettoBeløp = 0,
            periodeList = listOf(),
        ),
        kvittering = Kvittering(Kvittering.Utbetalingsstatus.OK, ""),
        utbetalingsrequest = Utbetalingsrequest(value = ""),
        utbetalingslinjer = listOf(enUtbetalingslinje),
        fnr = fnr,
        type = Utbetaling.UtbetalingsType.NY,
        behandler = behandler,
    )

    @Test
    fun `kaster exception dersom det ikke eksisterer utbetalinger som kan opphøres`() {
        assertThrows<Utbetalingsstrategi.UtbetalingStrategyException> {
            Utbetalingsstrategi.Opphør(
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                utbetalinger = emptyList(),
                behandler = NavIdentBruker.Saksbehandler("Z123"),
                clock = fixedClock,
                opphørsDato = 1.januar(2021),
            ).generate()
        }.also {
            it.message shouldBe "Sak: $sakId har ingen utbetalinger som kan opphøres"
        }
    }

    @Test
    fun `kaster exception dersom man forsøker å opphøre utbetalinger bakover i tid`() {
        assertThrows<Utbetalingsstrategi.UtbetalingStrategyException> {
            Utbetalingsstrategi.Opphør(
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                utbetalinger = listOf(enUtbetaling),
                behandler = NavIdentBruker.Saksbehandler("Z123"),
                clock = fixedClock,
                opphørsDato = 1.januar(2020),
            ).generate()
        }.also {
            it.message shouldBe "Støtter kun opphør framover i tid"
        }
    }

    @Test
    fun `kaster exception dersom man forsøker å opphøre utbetalinger senere enn siste utbetalingslinje sin sluttdato`() {
        assertThrows<Utbetalingsstrategi.UtbetalingStrategyException> {
            Utbetalingsstrategi.Opphør(
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                utbetalinger = listOf(enUtbetaling),
                behandler = NavIdentBruker.Saksbehandler("Z123"),
                clock = fixedClock,
                opphørsDato = 1.januar(2022),
            ).generate()
        }.also {
            it.message shouldBe "Dato for opphør må være tidligere enn tilOgMed for siste utbetalingslinje"
        }
    }

    @Test
    fun `kaster exception dersom man forsøker å opphøre fra en ugyldig dato`() {
        assertThrows<Utbetalingsstrategi.UtbetalingStrategyException> {
            Utbetalingsstrategi.Opphør(
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                utbetalinger = listOf(enUtbetaling),
                behandler = NavIdentBruker.Saksbehandler("Z123"),
                clock = fixedClock,
                opphørsDato = 19.januar(2021),
            ).generate()
        }.also {
            it.message shouldBe "Ytelse kan kun opphøres fra første eller siste dag i en måned."
        }
    }

    @Test
    fun `lager opphør for siste utbetalingslinje`() {
        Utbetalingsstrategi.Opphør(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            utbetalinger = listOf(enUtbetaling),
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            clock = fixedClock,
            opphørsDato = 1.januar(2021),
        ).generate().let {
            it.sakId shouldBe sakId
            it.saksnummer shouldBe saksnummer
            it.fnr shouldBe fnr
            it.type shouldBe Utbetaling.UtbetalingsType.OPPHØR
            it.behandler shouldBe behandler
            it.utbetalingslinjer shouldHaveSize 1
            it.utbetalingslinjer.first().let { endretUtbetalingslinje ->
                (endretUtbetalingslinje as Utbetalingslinje.Endring).let {
                    endretUtbetalingslinje.fraOgMed shouldBe enUtbetalingslinje.fraOgMed
                    endretUtbetalingslinje.tilOgMed shouldBe enUtbetalingslinje.tilOgMed
                    endretUtbetalingslinje.beløp shouldBe enUtbetalingslinje.beløp
                    endretUtbetalingslinje.forrigeUtbetalingslinjeId shouldBe enUtbetalingslinje.forrigeUtbetalingslinjeId
                    endretUtbetalingslinje.statusendring shouldBe Utbetalingslinje.Statusendring(
                        status = Utbetalingslinje.LinjeStatus.OPPHØR,
                        fraOgMed = 1.januar(2021),
                    )
                }
            }
        }
    }
}
