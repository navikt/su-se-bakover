package no.nav.su.se.bakover.domain.oppdrag.utbetaling

import arrow.core.NonEmptyList
import arrow.core.left
import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.november
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingsinstruksjonForEtterbetalinger
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertPeriode
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.saksnummer
import org.junit.jupiter.api.Test

internal class UtbetalingsstrategiGjenopptaTest {
    @Test
    fun `gjenopptar enkel utbetaling`() {
        val opprinnelig: Utbetaling.OversendtUtbetaling.UtenKvittering = oversendtUtbetaling()

        val stans: Utbetaling.OversendtUtbetaling.UtenKvittering = createOversendtUtbetaling(
            nonEmptyListOf(
                Utbetalingslinje.Endring.Stans(
                    utbetalingslinje = opprinnelig.sisteUtbetalingslinje(),
                    virkningstidspunkt = 1.oktober(2020),
                    clock = fixedClock,
                ),
            ),
        )

        val actual: Utbetaling.UtbetalingForSimulering = Utbetalingsstrategi.Gjenoppta(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            eksisterendeUtbetalinger = nonEmptyListOf(
                opprinnelig,
                stans,
            ),
            behandler = attestant,
            clock = fixedClock,
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
                    fraOgMed = opprinnelig.utbetalingslinjer[0].periode.fraOgMed,
                    tilOgMed = opprinnelig.utbetalingslinjer[0].periode.tilOgMed,
                    forrigeUtbetalingslinjeId = opprinnelig.utbetalingslinjer[0].forrigeUtbetalingslinjeId,
                    beløp = opprinnelig.utbetalingslinjer[0].beløp,
                    virkningsperiode = Periode.create(1.oktober(2020), opprinnelig.utbetalingslinjer[0].periode.tilOgMed),
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
            eksisterendeUtbetalinger = emptyList(),
            behandler = attestant,
            clock = fixedClock,
            sakstype = Sakstype.UFØRE,
        ).generer() shouldBe Utbetalingsstrategi.Gjenoppta.Feil.FantIngenUtbetalinger.left()
    }

    @Test
    fun `gjenopptar mer 'avansert' utbetaling`() {
        val første = oversendtUtbetaling()

        val førsteStans = createOversendtUtbetaling(
            nonEmptyListOf(
                Utbetalingslinje.Endring.Stans(
                    utbetalingslinje = første.sisteUtbetalingslinje(),
                    virkningstidspunkt = 1.oktober(2020),
                    clock = fixedClock,
                ),
            ),
        )

        val førsteGjenopptak = createOversendtUtbetaling(
            nonEmptyListOf(
                Utbetalingslinje.Endring.Reaktivering(
                    utbetalingslinje = førsteStans.sisteUtbetalingslinje(),
                    virkningstidspunkt = 1.oktober(2020),
                    clock = fixedClock,
                ),
            ),
        )

        val andre = createOversendtUtbetaling(
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Ny(
                    opprettet = fixedTidspunkt,
                    fraOgMed = 1.november(2020),
                    tilOgMed = 31.oktober(2021),
                    forrigeUtbetalingslinjeId = førsteGjenopptak.utbetalingslinjer[0].id,
                    beløp = 5100,
                    uføregrad = Uføregrad.parse(50),
                ),
            ),
        )

        val andreStans = createOversendtUtbetaling(
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Endring.Stans(
                    utbetalingslinje = andre.sisteUtbetalingslinje(),
                    virkningstidspunkt = 1.mai(2021),
                    clock = fixedClock,
                ),
            ),
        )

        val actual = Utbetalingsstrategi.Gjenoppta(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            eksisterendeUtbetalinger = nonEmptyListOf(
                første,
                førsteStans,
                førsteGjenopptak,
                andre,
                andreStans,
            ),
            behandler = attestant,
            clock = fixedClock,
            sakstype = Sakstype.UFØRE,
        ).generer().getOrFail("skal kunne lage utbetaling")

        actual.utbetalingslinjer shouldBe nonEmptyListOf(
            Utbetalingslinje.Endring.Reaktivering(
                id = andre.utbetalingslinjer[0].id,
                opprettet = actual.utbetalingslinjer[0].opprettet,
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
        val første = oversendtUtbetaling()
        Utbetalingsstrategi.Gjenoppta(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            eksisterendeUtbetalinger = nonEmptyListOf(første),
            behandler = attestant,
            clock = fixedClock,
            sakstype = Sakstype.UFØRE,
        ).generer() shouldBe Utbetalingsstrategi.Gjenoppta.Feil.SisteUtbetalingErIkkeStans.left()
    }

    @Test
    fun `kan ikke gjenoppta utbetalinger hvis siste ikke er stanset`() {
        val første = oversendtUtbetaling()

        val andre = createOversendtUtbetaling(
            nonEmptyListOf(
                Utbetalingslinje.Endring.Stans(
                    utbetalingslinje = første.sisteUtbetalingslinje(),
                    virkningstidspunkt = 1.januar(2020),
                    clock = fixedClock,
                ),
            ),
        )

        val tredje = createOversendtUtbetaling(
            nonEmptyListOf(
                Utbetalingslinje.Endring.Reaktivering(
                    utbetalingslinje = andre.sisteUtbetalingslinje(),
                    virkningstidspunkt = 1.januar(2020),
                    clock = fixedClock,
                ),
            ),
        )

        Utbetalingsstrategi.Gjenoppta(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            eksisterendeUtbetalinger = nonEmptyListOf(
                første,
                andre,
                tredje,
            ),
            behandler = attestant,
            clock = fixedClock,
            sakstype = Sakstype.UFØRE,
        ).generer() shouldBe Utbetalingsstrategi.Gjenoppta.Feil.SisteUtbetalingErIkkeStans.left()
    }

    @Test
    fun `gjenopptar utbetalinger med flere utbetalingslinjer`() {
        val l1 = Utbetalingslinje.Ny(
            opprettet = fixedTidspunkt,
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1500,
            uføregrad = Uføregrad.parse(50),
        )
        val l2 = Utbetalingslinje.Ny(
            opprettet = fixedTidspunkt,
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = l1.id,
            beløp = 5100,
            uføregrad = Uføregrad.parse(50),
        )
        val utbetaling = createOversendtUtbetaling(
            utbetalingslinjer = nonEmptyListOf(
                l1,
                l2,
            ),
        )

        val stans = createOversendtUtbetaling(
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Endring.Stans(
                    utbetalingslinje = utbetaling.sisteUtbetalingslinje(),
                    virkningstidspunkt = 1.april(2020),
                    clock = fixedClock,
                ),
            ),
        )

        Utbetalingsstrategi.Gjenoppta(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            eksisterendeUtbetalinger = nonEmptyListOf(
                utbetaling,
                stans,
            ),
            behandler = attestant,
            clock = fixedClock,
            sakstype = Sakstype.UFØRE,
        ).generer().getOrFail("skal kunne lage utbetaling").also {
            Utbetalingslinje.Endring.Reaktivering(
                id = utbetaling.sisteUtbetalingslinje().id,
                opprettet = it.utbetalingslinjer[0].opprettet,
                fraOgMed = utbetaling.sisteUtbetalingslinje().periode.fraOgMed,
                tilOgMed = utbetaling.sisteUtbetalingslinje().periode.tilOgMed,
                forrigeUtbetalingslinjeId = utbetaling.sisteUtbetalingslinje().forrigeUtbetalingslinjeId,
                beløp = utbetaling.sisteUtbetalingslinje().beløp,
                virkningsperiode = Periode.create(1.april(2020), utbetaling.sisteUtbetalingslinje().periode.tilOgMed),
                uføregrad = utbetaling.sisteUtbetalingslinje().uføregrad,
            )
        }
    }

    private fun createOversendtUtbetaling(
        utbetalingslinjer: NonEmptyList<Utbetalingslinje>,
    ): Utbetaling.OversendtUtbetaling.UtenKvittering {
        return Utbetaling.UtbetalingForSimulering(
            opprettet = fixedTidspunkt,
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            utbetalingslinjer = utbetalingslinjer,
            behandler = saksbehandler,
            avstemmingsnøkkel = Avstemmingsnøkkel(fixedTidspunkt),
            sakstype = Sakstype.UFØRE,
        ).toSimulertUtbetaling(
            simulering = Simulering(
                gjelderId = fnr,
                gjelderNavn = "navn",
                datoBeregnet = idag(fixedClock),
                nettoBeløp = 0,
                periodeList = listOf(
                    SimulertPeriode(
                        fraOgMed = utbetalingslinjer.minOf { it.periode.fraOgMed },
                        tilOgMed = utbetalingslinjer.maxOf { it.periode.tilOgMed },
                        utbetaling = listOf(),
                    ),
                ),
            ),
        ).toOversendtUtbetaling(
            oppdragsmelding = Utbetalingsrequest(""),
        )
    }
}
