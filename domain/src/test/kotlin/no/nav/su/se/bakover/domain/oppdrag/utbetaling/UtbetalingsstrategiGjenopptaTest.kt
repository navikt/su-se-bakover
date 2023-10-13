package no.nav.su.se.bakover.domain.oppdrag.utbetaling

import arrow.core.NonEmptyList
import arrow.core.left
import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Rekkefølge
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.extensions.april
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.mai
import no.nav.su.se.bakover.common.extensions.november
import no.nav.su.se.bakover.common.extensions.oktober
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.minAndMaxOf
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingsinstruksjonForEtterbetalinger
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.saksnummer
import no.nav.su.se.bakover.test.utbetaling.kvittering
import org.junit.jupiter.api.Test
import økonomi.domain.simulering.Simulering
import økonomi.domain.simulering.SimulertMåned
import java.time.Clock
import java.time.ZoneOffset

internal class UtbetalingsstrategiGjenopptaTest {
    @Test
    fun `gjenopptar enkel utbetaling`() {
        val clock = TikkendeKlokke()
        val opprinnelig: Utbetaling.OversendtUtbetaling.MedKvittering = kvittertUtbetaling(clock = clock)

        val stans: Utbetaling.OversendtUtbetaling.MedKvittering = createKvittertUtbetaling(
            opprettet = Tidspunkt.now(clock),
            clock = clock,
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Endring.Stans(
                    utbetalingslinjeSomSkalEndres = opprinnelig.sisteUtbetalingslinje(),
                    virkningstidspunkt = 1.oktober(2020),
                    opprettet = Tidspunkt.now(clock),
                    rekkefølge = Rekkefølge.start(),
                ),
            ),
        )

        val actual: Utbetaling.UtbetalingForSimulering = Utbetalingsstrategi.Gjenoppta(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            eksisterendeUtbetalinger = Utbetalinger(
                opprinnelig,
                stans,
            ),
            behandler = attestant,
            clock = clock,
            sakstype = Sakstype.UFØRE,
        ).generer().getOrFail("skal kunne lage utbetaling")

        actual shouldBe Utbetaling.UtbetalingForSimulering(
            id = actual.id,
            opprettet = actual.opprettet,
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Endring.Reaktivering(
                    id = opprinnelig.utbetalingslinjer[0].id,
                    opprettet = actual.utbetalingslinjer[0].opprettet,
                    rekkefølge = Rekkefølge.start(),
                    fraOgMed = opprinnelig.utbetalingslinjer[0].periode.fraOgMed,
                    tilOgMed = opprinnelig.utbetalingslinjer[0].periode.tilOgMed,
                    forrigeUtbetalingslinjeId = opprinnelig.utbetalingslinjer[0].forrigeUtbetalingslinjeId,
                    beløp = opprinnelig.utbetalingslinjer[0].beløp,
                    virkningsperiode = Periode.create(
                        1.oktober(2020),
                        opprinnelig.utbetalingslinjer[0].periode.tilOgMed,
                    ),
                    uføregrad = opprinnelig.utbetalingslinjer[0].uføregrad,
                    utbetalingsinstruksjonForEtterbetalinger = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
                ),
            ),
            behandler = attestant,
            avstemmingsnøkkel = actual.avstemmingsnøkkel,
            sakstype = Sakstype.UFØRE,
        )
    }

    @Test
    fun `kan ikke gjenopprette dersom utbetalinger ikke er oversendt`() {
        Utbetalingsstrategi.Gjenoppta(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            eksisterendeUtbetalinger = Utbetalinger(),
            behandler = attestant,
            clock = fixedClock,
            sakstype = Sakstype.UFØRE,
        ).generer() shouldBe Utbetalingsstrategi.Gjenoppta.Feil.FantIngenUtbetalinger.left()
    }

    @Test
    fun `gjenopptar mer 'avansert' utbetaling`() {
        val clock = TikkendeKlokke()

        val første = kvittertUtbetaling(clock = clock)

        val førsteStans = createKvittertUtbetaling(
            opprettet = Tidspunkt.now(clock),
            clock = clock,
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Endring.Stans(
                    utbetalingslinjeSomSkalEndres = første.sisteUtbetalingslinje(),
                    virkningstidspunkt = 1.oktober(2020),
                    opprettet = Tidspunkt.now(clock),
                    rekkefølge = Rekkefølge.start(),
                ),
            ),
        )

        val førsteGjenopptak = createKvittertUtbetaling(
            opprettet = Tidspunkt.now(clock),
            clock = clock,
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Endring.Reaktivering(
                    utbetalingslinjeSomSkalEndres = førsteStans.sisteUtbetalingslinje(),
                    virkningstidspunkt = 1.oktober(2020),
                    opprettet = Tidspunkt.now(clock),
                    rekkefølge = Rekkefølge.start(),
                ),
            ),
        )

        val andre = createKvittertUtbetaling(
            opprettet = Tidspunkt.now(clock),
            clock = clock,
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Ny(
                    opprettet = Tidspunkt.now(clock),
                    fraOgMed = 1.november(2020),
                    tilOgMed = 31.oktober(2021),
                    forrigeUtbetalingslinjeId = førsteGjenopptak.utbetalingslinjer[0].id,
                    beløp = 5100,
                    uføregrad = Uføregrad.parse(50),
                    rekkefølge = Rekkefølge.start(),
                ),
            ),
        )

        val andreStans = createKvittertUtbetaling(
            opprettet = Tidspunkt.now(clock),
            clock = clock,
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Endring.Stans(
                    utbetalingslinjeSomSkalEndres = andre.sisteUtbetalingslinje(),
                    virkningstidspunkt = 1.mai(2021),
                    clock = clock,
                    rekkefølge = Rekkefølge.start(),
                ),
            ),
        )

        val actual = Utbetalingsstrategi.Gjenoppta(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            eksisterendeUtbetalinger = Utbetalinger(
                første,
                førsteStans,
                førsteGjenopptak,
                andre,
                andreStans,
            ),
            behandler = attestant,
            clock = clock,
            sakstype = Sakstype.UFØRE,
        ).generer().getOrFail("skal kunne lage utbetaling")

        actual.utbetalingslinjer shouldBe nonEmptyListOf(
            Utbetalingslinje.Endring.Reaktivering(
                id = andre.utbetalingslinjer[0].id,
                opprettet = actual.utbetalingslinjer[0].opprettet,
                rekkefølge = Rekkefølge.start(),
                fraOgMed = andre.utbetalingslinjer[0].periode.fraOgMed,
                tilOgMed = andre.utbetalingslinjer[0].periode.tilOgMed,
                forrigeUtbetalingslinjeId = andre.utbetalingslinjer[0].forrigeUtbetalingslinjeId,
                beløp = andre.utbetalingslinjer[0].beløp,
                virkningsperiode = Periode.create(1.mai(2021), andre.utbetalingslinjer[0].periode.tilOgMed),
                uføregrad = andre.utbetalingslinjer[0].uføregrad,
            ),
        )
    }

    @Test
    fun `kan ikke gjenoppta utbetalinger hvis ingen er stanset`() {
        val første = kvittertUtbetaling()
        Utbetalingsstrategi.Gjenoppta(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            eksisterendeUtbetalinger = Utbetalinger(første),
            behandler = attestant,
            clock = fixedClock,
            sakstype = Sakstype.UFØRE,
        ).generer() shouldBe Utbetalingsstrategi.Gjenoppta.Feil.SisteUtbetalingErIkkeStans.left()
    }

    @Test
    fun `kan ikke gjenoppta utbetalinger hvis siste ikke er stanset`() {
        val clock = TikkendeKlokke()
        val første = kvittertUtbetaling(clock = clock)

        val andre = createKvittertUtbetaling(
            opprettet = Tidspunkt.now(clock),
            clock = clock,
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Endring.Stans(
                    utbetalingslinjeSomSkalEndres = første.sisteUtbetalingslinje(),
                    virkningstidspunkt = 1.januar(2020),
                    opprettet = Tidspunkt.now(clock),
                    rekkefølge = Rekkefølge.start(),
                ),
            ),
        )

        val tredje = createKvittertUtbetaling(
            opprettet = Tidspunkt.now(clock),
            clock = clock,
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Endring.Reaktivering(
                    utbetalingslinjeSomSkalEndres = andre.sisteUtbetalingslinje(),
                    virkningstidspunkt = 1.januar(2020),
                    opprettet = Tidspunkt.now(clock),
                    rekkefølge = Rekkefølge.start(),
                ),
            ),
        )

        Utbetalingsstrategi.Gjenoppta(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            eksisterendeUtbetalinger = Utbetalinger(
                første,
                andre,
                tredje,
            ),
            behandler = attestant,
            clock = clock,
            sakstype = Sakstype.UFØRE,
        ).generer() shouldBe Utbetalingsstrategi.Gjenoppta.Feil.SisteUtbetalingErIkkeStans.left()
    }

    @Test
    fun `gjenopptar utbetalinger med flere utbetalingslinjer`() {
        val clock = TikkendeKlokke()
        val l1 = Utbetalingslinje.Ny(
            opprettet = Tidspunkt.now(clock),
            rekkefølge = Rekkefølge.start(),
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1500,
            uføregrad = Uføregrad.parse(50),
        )
        val l2 = Utbetalingslinje.Ny(
            opprettet = Tidspunkt.now(clock),
            rekkefølge = Rekkefølge.skip(0),
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = l1.id,
            beløp = 5100,
            uføregrad = Uføregrad.parse(50),
        )
        val utbetaling = createKvittertUtbetaling(
            opprettet = Tidspunkt.now(clock),
            clock = clock,
            utbetalingslinjer = nonEmptyListOf(
                l1,
                l2,
            ),
        )

        val stans = createKvittertUtbetaling(
            opprettet = Tidspunkt.now(clock),
            clock = clock,
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Endring.Stans(
                    utbetalingslinjeSomSkalEndres = utbetaling.sisteUtbetalingslinje(),
                    virkningstidspunkt = 1.april(2020),
                    opprettet = Tidspunkt.now(clock),
                    rekkefølge = Rekkefølge.start(),
                ),
            ),
        )

        Utbetalingsstrategi.Gjenoppta(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            eksisterendeUtbetalinger = Utbetalinger(
                utbetaling,
                stans,
            ),
            behandler = attestant,
            clock = clock,
            sakstype = Sakstype.UFØRE,
        ).generer().getOrFail("skal kunne lage utbetaling").also {
            Utbetalingslinje.Endring.Reaktivering(
                id = utbetaling.sisteUtbetalingslinje().id,
                opprettet = it.utbetalingslinjer[0].opprettet,
                rekkefølge = Rekkefølge.start(),
                fraOgMed = utbetaling.sisteUtbetalingslinje().periode.fraOgMed,
                tilOgMed = utbetaling.sisteUtbetalingslinje().periode.tilOgMed,
                forrigeUtbetalingslinjeId = utbetaling.sisteUtbetalingslinje().forrigeUtbetalingslinjeId,
                beløp = utbetaling.sisteUtbetalingslinje().beløp,
                virkningsperiode = Periode.create(1.april(2020), utbetaling.sisteUtbetalingslinje().periode.tilOgMed),
                uføregrad = utbetaling.sisteUtbetalingslinje().uføregrad,
            )
        }
    }

    private fun createKvittertUtbetaling(
        opprettet: Tidspunkt = fixedTidspunkt,
        utbetalingslinjer: NonEmptyList<Utbetalingslinje>,
        clock: Clock,
    ): Utbetaling.OversendtUtbetaling.MedKvittering {
        return Utbetaling.UtbetalingForSimulering(
            opprettet = opprettet,
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            utbetalingslinjer = utbetalingslinjer,
            behandler = saksbehandler,
            avstemmingsnøkkel = Avstemmingsnøkkel(opprettet),
            sakstype = Sakstype.UFØRE,
        ).toSimulertUtbetaling(
            simulering = Simulering(
                gjelderId = fnr,
                gjelderNavn = "navn",
                datoBeregnet = opprettet.toLocalDate(ZoneOffset.UTC),
                nettoBeløp = 0,
                måneder = SimulertMåned.create(utbetalingslinjer.map { it.periode }.minAndMaxOf().måneder()),
                rawResponse = "UtbetalingsstrategiGjenopptaTest baserer ikke denne på rå XML.",
            ),
        ).toOversendtUtbetaling(
            oppdragsmelding = Utbetalingsrequest(""),
        ).toKvittertUtbetaling(kvittering(clock = clock))
    }
}
