package no.nav.su.se.bakover.domain.oppdrag.utbetaling

import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.november
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag.UtbetalingStrategy.Gjenoppta
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingStrategyException
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

internal class OppdragGjenopptaStrategyTest {

    private val fnr = Fnr("12345678910")

    @Test
    fun `gjenopptar enkel utbetaling`() {
        val opprinnelig = createOversendtUtbetaling(
            listOf(
                Utbetalingslinje(
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.desember(2020),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 1500.0
                )
            ),
            type = Utbetaling.UtbetalingsType.NY
        )

        val stans = createOversendtUtbetaling(
            listOf(
                Utbetalingslinje(
                    fraOgMed = 1.oktober(2020),
                    tilOgMed = 31.desember(2020),
                    forrigeUtbetalingslinjeId = opprinnelig.utbetalingslinjer[0].id,
                    beløp = 0.0
                )
            ),
            type = Utbetaling.UtbetalingsType.GJENOPPTA
        )

        createOppdrag(listOf(opprinnelig, stans)).genererUtbetaling(
            strategy = Gjenoppta(
                NavIdentBruker.Attestant("Z123")
            ),
            fnr = fnr
        ).utbetalingslinjer[0].assert(
            fraOgMed = 1.oktober(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinje = stans.utbetalingslinjer[0].id,
            beløp = opprinnelig.utbetalingslinjer[0].beløp
        )
    }

    @Test
    fun `kan ikke gjenopprette dersom utbetalinger ikke er oversendt`() {
        assertThrows<UtbetalingStrategyException> {
            createOppdrag(listOf()).genererUtbetaling(
                Gjenoppta(
                    NavIdentBruker.Attestant("Z123")
                ),
                fnr
            )
        }.also {
            it.message shouldContain "Ingen oversendte utbetalinger"
        }
    }

    @Test
    fun `gjenopptar mer 'avansert' utbetaling`() {
        val første = createOversendtUtbetaling(
            listOf(
                Utbetalingslinje(
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.desember(2020),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 1500.0
                )
            ),
            type = Utbetaling.UtbetalingsType.NY

        )

        val førsteStans = createOversendtUtbetaling(
            listOf(
                element = Utbetalingslinje(
                    fraOgMed = 1.oktober(2020),
                    tilOgMed = 31.desember(2020),
                    forrigeUtbetalingslinjeId = første.utbetalingslinjer[0].id,
                    beløp = 0.0
                )
            ),
            type = Utbetaling.UtbetalingsType.STANS
        )

        val førsteGjenopptak = createOversendtUtbetaling(
            listOf(
                element = Utbetalingslinje(
                    fraOgMed = 1.oktober(2020),
                    tilOgMed = 31.desember(2020),
                    forrigeUtbetalingslinjeId = førsteStans.utbetalingslinjer[0].id,
                    beløp = 1500.0
                )
            ),
            type = Utbetaling.UtbetalingsType.GJENOPPTA
        )

        val andre = createOversendtUtbetaling(
            utbetalingslinjer = listOf(
                Utbetalingslinje(
                    fraOgMed = 1.november(2020),
                    tilOgMed = 31.oktober(2021),
                    forrigeUtbetalingslinjeId = førsteStans.utbetalingslinjer[0].id,
                    beløp = 5100.0
                )
            ),
            type = Utbetaling.UtbetalingsType.NY
        )

        val andreStans = createOversendtUtbetaling(
            utbetalingslinjer = listOf(
                Utbetalingslinje(
                    fraOgMed = 1.mai(2021),
                    tilOgMed = 31.oktober(2021),
                    forrigeUtbetalingslinjeId = andre.utbetalingslinjer[0].id,
                    beløp = 0.0
                )
            ),
            type = Utbetaling.UtbetalingsType.STANS
        )

        createOppdrag(listOf(første, førsteStans, førsteGjenopptak, andre, andreStans)).genererUtbetaling(
            strategy = Gjenoppta(
                NavIdentBruker.Attestant("Z123")
            ),
            fnr = fnr
        ).utbetalingslinjer[0].assert(
            fraOgMed = 1.mai(2021),
            tilOgMed = 31.oktober(2021),
            forrigeUtbetalingslinje = andreStans.utbetalingslinjer[0].id,
            beløp = 5100.0
        )
    }

    @Test
    fun `kan ikke gjenoppta utbetalinger hvis ingen er stanset`() {
        val første = createOversendtUtbetaling(
            listOf(
                Utbetalingslinje(
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.desember(2020),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 1500.0
                )
            ),
            type = Utbetaling.UtbetalingsType.NY
        )

        assertThrows<UtbetalingStrategyException> {
            createOppdrag(listOf(første)).genererUtbetaling(
                strategy = Gjenoppta(
                    NavIdentBruker.Attestant("Z123")
                ),
                fnr = fnr
            )
        }.also {
            it.message shouldContain "Fant ingen utbetalinger som kan gjenopptas"
        }
    }

    @Test
    fun `gjenopptar utbetalinger med flere utbetalingslinjer`() {
        val l1 = Utbetalingslinje(
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1500.0
        )
        val l2 = Utbetalingslinje(
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = l1.id,
            beløp = 5100.0
        )
        val første = createOversendtUtbetaling(
            listOf(l1, l2), Utbetaling.UtbetalingsType.NY
        )

        val stans = createOversendtUtbetaling(
            utbetalingslinjer = listOf(
                Utbetalingslinje(
                    fraOgMed = 1.april(2020),
                    tilOgMed = 31.desember(2020),
                    forrigeUtbetalingslinjeId = første.utbetalingslinjer[1].id,
                    beløp = 0.0
                )
            ),
            type = Utbetaling.UtbetalingsType.STANS
        )

        createOppdrag(listOf(første, stans)).genererUtbetaling(
            strategy = Gjenoppta(
                NavIdentBruker.Attestant("Z123")
            ),
            fnr = fnr
        ).also {
            it.utbetalingslinjer[0].assert(
                fraOgMed = 1.april(2020),
                tilOgMed = 30.april(2020),
                forrigeUtbetalingslinje = stans.utbetalingslinjer[0].id,
                beløp = 1500.0
            )
            it.utbetalingslinjer[1].assert(
                fraOgMed = 1.mai(2020),
                tilOgMed = 31.desember(2020),
                forrigeUtbetalingslinje = it.utbetalingslinjer[0].id,
                beløp = 5100.0
            )
        }
    }

    // TODO consider for test - moved from gjenoppta utbetaling service
    //
    // @Test
    // fun `Har ingen oversendte utbetalinger`() {
    //     val setup = Setup()
    //
    //     val sak = setup.eksisterendeSak.copy(
    //         oppdrag = setup.eksisterendeSak.oppdrag.copy(
    //             utbetalinger = setup.eksisterendeSak.oppdrag.hentUtbetalinger()
    //                 .map { Utbetaling.Ny(utbetalingslinjer = emptyList(), fnr = setup.fnr) }.toMutableList()
    //         )
    //     )
    //     val repoMock = mock<SakService> {
    //         on { hentSak(argThat<UUID> { it shouldBe setup.sakId }) } doReturn sak.right()
    //     }
    //
    //     val service = StartUtbetalingerService(
    //         utbetalingPublisher = mock(),
    //         utbetalingService = mock(),
    //         sakService = repoMock,
    //         clock = setup.clock
    //     )
    //     val actualResponse = service.startUtbetalinger(sakId = setup.sakId)
    //
    //     actualResponse shouldBe StartUtbetalingFeilet.HarIngenOversendteUtbetalinger.left()
    //
    //     verify(repoMock, Times(1)).hentSak(any<UUID>())
    //     verifyNoMoreInteractions(repoMock)
    // }
    //
    // @Test
    // fun `Siste utbetaling er ikke en stansutbetaling`() {
    //     val setup = Setup()
    //
    //     val sak = setup.eksisterendeSak.copy(
    //         oppdrag = setup.eksisterendeSak.oppdrag.copy(
    //             utbetalinger = setup.eksisterendeSak.oppdrag.hentUtbetalinger().toMutableList().also {
    //                 it.removeLast()
    //             }
    //         )
    //     )
    //     val repoMock = mock<SakService> {
    //         on { hentSak(argThat<UUID> { it shouldBe setup.sakId }) } doReturn sak.right()
    //     }
    //
    //     val service = StartUtbetalingerService(
    //         utbetalingPublisher = mock(),
    //         utbetalingService = mock(),
    //         sakService = repoMock,
    //         clock = setup.clock
    //     )
    //     val actualResponse = service.startUtbetalinger(sakId = setup.sakId)
    //
    //     actualResponse shouldBe StartUtbetalingFeilet.SisteUtbetalingErIkkeEnStansutbetaling.left()
    //
    //     verify(repoMock, Times(1)).hentSak(any<UUID>())
    //
    //     verifyNoMoreInteractions(repoMock)
    // }

    private fun createOppdrag(utbetalinger: List<Utbetaling.OversendtUtbetaling>) = Oppdrag(
        id = UUID30.randomUUID(),
        opprettet = Tidspunkt.now(),
        sakId = UUID.randomUUID(),
        utbetalinger = utbetalinger
    )

    private fun createOversendtUtbetaling(utbetalingslinjer: List<Utbetalingslinje>, type: Utbetaling.UtbetalingsType) = Utbetaling.OversendtUtbetaling.UtenKvittering(
        utbetalingsrequest = Utbetalingsrequest(
            value = ""
        ),
        utbetalingslinjer = utbetalingslinjer,
        fnr = fnr,
        type = type,
        simulering = Simulering(
            gjelderId = Fnr(fnr = fnr.toString()),
            gjelderNavn = "navn",
            datoBeregnet = idag(),
            nettoBeløp = 0.0,
            periodeList = listOf()
        ),
        oppdragId = UUID30.randomUUID(),
        behandler = NavIdentBruker.Saksbehandler("Z123")
    )
}
