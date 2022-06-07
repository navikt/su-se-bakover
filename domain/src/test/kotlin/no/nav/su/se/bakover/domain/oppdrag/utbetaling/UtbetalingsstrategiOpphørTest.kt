package no.nav.su.se.bakover.domain.oppdrag.utbetaling

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

internal class UtbetalingsstrategiOpphørTest {

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
        uføregrad = Uføregrad.parse(50),
    )

    private val enUtbetaling = Utbetaling.UtbetalingForSimulering(
        opprettet = fixedTidspunkt,
        sakId = sakId,
        saksnummer = saksnummer,

        utbetalingslinjer = nonEmptyListOf(enUtbetalingslinje),
        fnr = fnr,
        type = Utbetaling.UtbetalingsType.NY,
        behandler = behandler,
        avstemmingsnøkkel = Avstemmingsnøkkel(fixedTidspunkt)
    ).toSimulertUtbetaling(
        simulering = Simulering(
            gjelderId = fnr,
            gjelderNavn = "navn",
            datoBeregnet = idag(fixedClock),
            nettoBeløp = 0,
            periodeList = listOf(),
        ),
    ).toOversendtUtbetaling(
        oppdragsmelding = Utbetalingsrequest(value = ""),
    ).toKvittertUtbetaling(
        kvittering = Kvittering(Kvittering.Utbetalingsstatus.OK, "", mottattTidspunkt = fixedTidspunkt),
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
            it.message shouldBe "Ingen oversendte utbetalinger å opphøre"
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
            it.message shouldBe "Ytelse kan kun opphøres fra første dag i måneden"
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
        ).generate().also {
            it.sakId shouldBe sakId
            it.saksnummer shouldBe saksnummer
            it.fnr shouldBe fnr
            it.type shouldBe Utbetaling.UtbetalingsType.OPPHØR
            it.behandler shouldBe behandler
            it.utbetalingslinjer shouldBe nonEmptyListOf(
                Utbetalingslinje.Endring.Opphør(
                    id = enUtbetalingslinje.id,
                    opprettet = it.utbetalingslinjer[0].opprettet,
                    fraOgMed = enUtbetalingslinje.fraOgMed,
                    tilOgMed = enUtbetalingslinje.tilOgMed,
                    beløp = enUtbetalingslinje.beløp,
                    forrigeUtbetalingslinjeId = enUtbetalingslinje.forrigeUtbetalingslinjeId,
                    virkningstidspunkt = 1.januar(2021),
                    uføregrad = enUtbetalingslinje.uføregrad,
                ),
            )
        }
    }
}
