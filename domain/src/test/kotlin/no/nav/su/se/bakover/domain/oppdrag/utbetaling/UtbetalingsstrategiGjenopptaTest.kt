package no.nav.su.se.bakover.domain.oppdrag.utbetaling

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.november
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.ZoneOffset
import java.util.UUID

internal class UtbetalingsstrategiGjenopptaTest {

    private val fixedClock: Clock = Clock.fixed(1.januar(2021).startOfDay().instant, ZoneOffset.UTC)

    private val fnr = Fnr("12345678910")
    private val sakId = UUID.randomUUID()
    private val saksnummer = Saksnummer(2021)
    private val attestant = NavIdentBruker.Attestant("Z123")

    @Test
    fun `gjenopptar enkel utbetaling`() {
        val opprinnelig: Utbetaling.OversendtUtbetaling.UtenKvittering = createOversendtUtbetaling(
            nonEmptyListOf(
                Utbetalingslinje.Ny(
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.desember(2020),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 1500,
                ),
            ),
            type = Utbetaling.UtbetalingsType.NY,
        )

        val stans: Utbetaling.OversendtUtbetaling.UtenKvittering = createOversendtUtbetaling(
            nonEmptyListOf(
                Utbetalingslinje.Ny(
                    fraOgMed = 1.oktober(2020),
                    tilOgMed = 31.desember(2020),
                    forrigeUtbetalingslinjeId = opprinnelig.utbetalingslinjer[0].id,
                    beløp = 0,
                ),
            ),
            type = Utbetaling.UtbetalingsType.GJENOPPTA,
        )

        val actual: Utbetaling.UtbetalingForSimulering = Utbetalingsstrategi.Gjenoppta(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            utbetalinger = nonEmptyListOf(opprinnelig, stans),
            behandler = attestant,
            clock = fixedClock,
        ).generate()

        actual shouldBe
            Utbetaling.UtbetalingForSimulering(
                id = actual.id,
                opprettet = actual.opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                utbetalingslinjer = nonEmptyListOf(
                    Utbetalingslinje.Ny(
                        id = actual.utbetalingslinjer[0].id,
                        opprettet = actual.utbetalingslinjer[0].opprettet,
                        fraOgMed = 1.oktober(2020),
                        tilOgMed = 31.desember(2020),
                        forrigeUtbetalingslinjeId = stans.utbetalingslinjer[0].id,
                        beløp = opprinnelig.utbetalingslinjer[0].beløp,
                    ),
                ),
                type = Utbetaling.UtbetalingsType.GJENOPPTA,
                behandler = attestant,
                avstemmingsnøkkel = actual.avstemmingsnøkkel,
            )
    }

    @Test
    fun `kan ikke gjenopprette dersom utbetalinger ikke er oversendt`() {
        shouldThrow<Utbetalingsstrategi.UtbetalingStrategyException> {
            Utbetalingsstrategi.Gjenoppta(
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                utbetalinger = emptyList(),
                behandler = attestant,
                clock = fixedClock,
            ).generate()
        }.message shouldContain "Ingen oversendte utbetalinger"
    }

    @Test
    fun `gjenopptar mer 'avansert' utbetaling`() {
        val første = createOversendtUtbetaling(
            nonEmptyListOf(
                Utbetalingslinje.Ny(
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.desember(2020),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 1500,
                ),
            ),
            type = Utbetaling.UtbetalingsType.NY,

        )

        val førsteStans = createOversendtUtbetaling(
            nonEmptyListOf(
                Utbetalingslinje.Ny(
                    fraOgMed = 1.oktober(2020),
                    tilOgMed = 31.desember(2020),
                    forrigeUtbetalingslinjeId = første.utbetalingslinjer[0].id,
                    beløp = 0,
                ),
            ),
            type = Utbetaling.UtbetalingsType.STANS,
        )

        val førsteGjenopptak = createOversendtUtbetaling(
            nonEmptyListOf(
                Utbetalingslinje.Ny(
                    fraOgMed = 1.oktober(2020),
                    tilOgMed = 31.desember(2020),
                    forrigeUtbetalingslinjeId = førsteStans.utbetalingslinjer[0].id,
                    beløp = 1500,
                ),
            ),
            type = Utbetaling.UtbetalingsType.GJENOPPTA,
        )

        val andre = createOversendtUtbetaling(
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Ny(
                    fraOgMed = 1.november(2020),
                    tilOgMed = 31.oktober(2021),
                    forrigeUtbetalingslinjeId = førsteGjenopptak.utbetalingslinjer[0].id,
                    beløp = 5100,
                ),
            ),
            type = Utbetaling.UtbetalingsType.NY,
        )

        val andreStans = createOversendtUtbetaling(
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Ny(
                    fraOgMed = 1.mai(2021),
                    tilOgMed = 31.oktober(2021),
                    forrigeUtbetalingslinjeId = andre.utbetalingslinjer[0].id,
                    beløp = 0,
                ),
            ),
            type = Utbetaling.UtbetalingsType.STANS,
        )

        val actual = Utbetalingsstrategi.Gjenoppta(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            utbetalinger = nonEmptyListOf(første, førsteStans, førsteGjenopptak, andre, andreStans),
            behandler = attestant,
            clock = fixedClock,
        ).generate()

        actual.utbetalingslinjer[0].assert(
            fraOgMed = 1.mai(2021),
            tilOgMed = 31.oktober(2021),
            forrigeUtbetalingslinje = andreStans.utbetalingslinjer[0].id,
            beløp = 5100,
        )
    }

    @Test
    fun `kan ikke gjenoppta utbetalinger hvis ingen er stanset`() {
        val første = createOversendtUtbetaling(
            nonEmptyListOf(
                Utbetalingslinje.Ny(
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.desember(2020),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 1500,
                ),
            ),
            type = Utbetaling.UtbetalingsType.NY,
        )

        shouldThrow<Utbetalingsstrategi.UtbetalingStrategyException> {
            Utbetalingsstrategi.Gjenoppta(
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                utbetalinger = nonEmptyListOf(første),
                behandler = attestant,
                clock = fixedClock,
            ).generate()
        }.also {
            it.message shouldContain "Fant ingen utbetalinger som kan gjenopptas"
        }
    }

    @Test
    fun `kan ikke gjenoppta utbetalinger hvis siste ikke er stanset`() {
        val første = createOversendtUtbetaling(
            nonEmptyListOf(
                Utbetalingslinje.Ny(
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.juli(2020),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 1500,
                ),
            ),
            type = Utbetaling.UtbetalingsType.NY,
        )

        val andre = createOversendtUtbetaling(
            nonEmptyListOf(
                Utbetalingslinje.Ny(
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.juli(2020),
                    forrigeUtbetalingslinjeId = første.utbetalingslinjer[0].id,
                    beløp = 1500,
                ),
            ),
            type = Utbetaling.UtbetalingsType.STANS,
        )

        val tredje = createOversendtUtbetaling(
            nonEmptyListOf(
                Utbetalingslinje.Ny(
                    fraOgMed = 1.august(2020),
                    tilOgMed = 31.desember(2020),
                    forrigeUtbetalingslinjeId = andre.utbetalingslinjer[0].id,
                    beløp = 1500,
                ),
            ),
            type = Utbetaling.UtbetalingsType.NY,
        )

        shouldThrow<Utbetalingsstrategi.UtbetalingStrategyException> {
            Utbetalingsstrategi.Gjenoppta(
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                utbetalinger = nonEmptyListOf(første, andre, tredje),
                behandler = attestant,
                clock = fixedClock,
            ).generate()
        }.also {
            it.message shouldContain "Fant ingen utbetalinger som kan gjenopptas"
        }
    }

    @Test
    fun `gjenopptar utbetalinger med flere utbetalingslinjer`() {
        val l1 = Utbetalingslinje.Ny(
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1500,
        )
        val l2 = Utbetalingslinje.Ny(
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = l1.id,
            beløp = 5100,
        )
        val første = createOversendtUtbetaling(
            nonEmptyListOf(l1, l2), Utbetaling.UtbetalingsType.NY,
        )

        val stans = createOversendtUtbetaling(
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Ny(
                    fraOgMed = 1.april(2020),
                    tilOgMed = 31.desember(2020),
                    forrigeUtbetalingslinjeId = første.utbetalingslinjer[1].id,
                    beløp = 0,
                ),
            ),
            type = Utbetaling.UtbetalingsType.STANS,
        )

        Utbetalingsstrategi.Gjenoppta(
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            utbetalinger = nonEmptyListOf(første, stans),
            behandler = attestant,
            clock = fixedClock,
        ).generate().also {
            it.utbetalingslinjer[0].assert(
                fraOgMed = 1.april(2020),
                tilOgMed = 30.april(2020),
                forrigeUtbetalingslinje = stans.utbetalingslinjer[0].id,
                beløp = 1500,
            )
            it.utbetalingslinjer[1].assert(
                fraOgMed = 1.mai(2020),
                tilOgMed = 31.desember(2020),
                forrigeUtbetalingslinje = it.utbetalingslinjer[0].id,
                beløp = 5100,
            )
        }
    }

    private fun createOversendtUtbetaling(
        utbetalingslinjer: NonEmptyList<Utbetalingslinje>,
        type: Utbetaling.UtbetalingsType,
    ) =
        Utbetaling.OversendtUtbetaling.UtenKvittering(
            sakId = sakId,
            saksnummer = saksnummer,
            utbetalingsrequest = Utbetalingsrequest(""),
            utbetalingslinjer = utbetalingslinjer,
            fnr = fnr,
            type = type,
            simulering = Simulering(
                gjelderId = Fnr(fnr = fnr.toString()),
                gjelderNavn = "navn",
                datoBeregnet = idag(),
                nettoBeløp = 0,
                periodeList = listOf()
            ),
            behandler = NavIdentBruker.Saksbehandler("Z123"),
        )
}
