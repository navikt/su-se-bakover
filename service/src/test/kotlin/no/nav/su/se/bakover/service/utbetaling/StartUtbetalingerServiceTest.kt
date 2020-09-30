package no.nav.su.se.bakover.service.utbetaling

import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.database.ObjectRepo
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.NyUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding.Oppdragsmeldingstatus.SENDT
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingPersistenceObserver
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertPeriode
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.service.argShouldBe
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.doNothing
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

internal class StartUtbetalingerServiceTest {

    @Test
    fun `Utbetalinger som er stanset blir startet igjen`() {
        val setup = Setup()
        var actualUtbetalingsId: UUID30? = null

        val utbetalingPersistenceObserverMock = mock<UtbetalingPersistenceObserver> {
            on {
                addOppdragsmelding(
                    argThat {
                        it shouldBe actualUtbetalingsId
                    },
                    argThat {
                        it shouldBe setup.oppdragsMeldingSendt
                    }
                )
            } doReturn setup.oppdragsMeldingSendt

            on {
                addSimulering(
                    any(),
                    argThat {
                        it shouldBe setup.simulerStartutbetaling
                    }
                )
            }.doNothing()
        }

        val oppdragPersistenceObserverMock = mock<Oppdrag.OppdragPersistenceObserver> {
            on {
                opprettUtbetaling(
                    any(),
                    argThat {
                        it shouldBe setup.forventetUtbetaling(
                            actualUtbetaling = it,
                            utbetalingslinjer = listOf(setup.utbetLinje1, setup.utbetLinje2, setup.utbetLinje3)
                        )
                        it.addObserver(utbetalingPersistenceObserverMock)
                    }
                )
            }.doNothing()

            on { hentFnr(argShouldBe(setup.sakId)) } doReturn setup.fnr
        }

        val simuleringClientMock = mock<SimuleringClient> {
            on {
                simulerUtbetaling(
                    argThat {
                        it shouldBe setup.forventetNyUtbetaling(
                            actualUtbetaling = it.utbetaling,
                            utbetalingslinjer = listOf(setup.utbetLinje1, setup.utbetLinje2, setup.utbetLinje3)
                        )
                        actualUtbetalingsId = it.utbetaling.id
                    }
                )
            } doReturn setup.simulerStartutbetaling.right()
        }

        val publisherMock = mock<UtbetalingPublisher> {
            on {
                publish(
                    argThat {
                        it shouldBe setup.forventetNyUtbetaling(
                            actualUtbetaling = it.utbetaling,
                            simulering = setup.simulerStartutbetaling,
                            utbetalingslinjer = listOf(setup.utbetLinje1, setup.utbetLinje2, setup.utbetLinje3)
                        )
                            .copy(
                                oppdrag = setup.eksisterendeOppdrag.copy(utbetalinger = (setup.eksisterendeOppdrag.hentUtbetalinger() + it.utbetaling).toMutableList())
                            )
                    }
                )
            } doReturn setup.oppdragsMeldingSendt.right()
        }

        val sak = setup.sak.copy().also {
            it.oppdrag.addObserver(oppdragPersistenceObserverMock)
            it.oppdrag.hentUtbetalinger().forEach { utbetaling ->
                utbetaling.addObserver(utbetalingPersistenceObserverMock)
            }
        }
        val repoMock = mock<ObjectRepo> {
            on { hentSak(argShouldBe(setup.sakId)) } doReturn sak
        }

        val service = StartUtbetalingerService(repoMock, simuleringClientMock, publisherMock)
        val startetUtbet = service.startUtbetalinger(setup.sakId)

        val expected = Utbetaling(
            id = sak.oppdrag.hentUtbetalinger().last().id,
            opprettet = sak.oppdrag.hentUtbetalinger().last().opprettet,
            simulering = setup.simulerStartutbetaling,
            kvittering = null,
            oppdragsmelding = setup.oppdragsMeldingSendt,
            utbetalingslinjer = listOf(setup.utbetLinje1, setup.utbetLinje2, setup.utbetLinje3),
            avstemmingId = null,
            fnr = setup.fnr
        )
        startetUtbet shouldBe expected.copy(
            simulering = setup.simulerStartutbetaling,
            oppdragsmelding = setup.oppdragsMeldingSendt
        ).right()
    }

    data class Setup(
        val fnr: Fnr = Fnr("20128127969"),
        val sakId: UUID = UUID.fromString("3ae00766-f055-4f8f-b816-42f4b7f8bc96"),
        val oppdragsMeldingSendt: Oppdragsmelding = Oppdragsmelding(SENDT, ""),
        val attestant: Attestant = Attestant("SU"),
        val oppdragId: UUID30 = UUID30.randomUUID(),
        val utbetLinje1: Utbetalingslinje = Utbetalingslinje(
            fom = 1.januar(1970),
            tom = 31.januar(1970),
            beløp = 100,
            forrigeUtbetalingslinjeId = null
        ),
        val utbetLinje2: Utbetalingslinje = Utbetalingslinje(
            fom = 1.februar(1970),
            tom = 31.mars(1970),
            beløp = 200,
            forrigeUtbetalingslinjeId = utbetLinje1.id
        ),
        val utbetLinje3: Utbetalingslinje = Utbetalingslinje(
            fom = 1.april(1970),
            tom = 31.juli(1970),
            beløp = 300,
            forrigeUtbetalingslinjeId = utbetLinje2.id
        ),
        val utbetaling1: Utbetaling = Utbetaling(
            utbetalingslinjer = listOf(
                utbetLinje1
            ),
            oppdragsmelding = Oppdragsmelding(SENDT, ""),
            fnr = fnr
        ),
        val utbet2Id: UUID30 = UUID30.randomUUID(),
        val utbetaling2: Utbetaling = Utbetaling(
            utbetalingslinjer = listOf(
                utbetLinje2,
                utbetLinje3
            ),
            oppdragsmelding = Oppdragsmelding(SENDT, ""),
            fnr = fnr
        ),
        val stansutbetaling: Utbetaling = Utbetaling(
            utbetalingslinjer = listOf(
                Utbetalingslinje(
                    fom = 1.januar(1970),
                    tom = 31.juli(1970),
                    beløp = 0,
                    forrigeUtbetalingslinjeId = utbetaling2.sisteUtbetalingslinje()!!.id
                )
            ),
            oppdragsmelding = Oppdragsmelding(SENDT, ""),
            fnr = fnr
        ),
        val simulerStartutbetaling: Simulering = Simulering(
            gjelderId = fnr,
            gjelderNavn = "",
            datoBeregnet = LocalDate.EPOCH,
            nettoBeløp = 0,
            periodeList = listOf(
                SimulertPeriode(
                    fom = 1.januar(1970),
                    tom = 31.januar(1970),
                    utbetaling = listOf()
                ),
                SimulertPeriode(
                    fom = 1.februar(1970),
                    tom = 31.mars(1970),
                    utbetaling = listOf()
                ),
                SimulertPeriode(
                    fom = 1.januar(1970),
                    tom = 31.januar(1970),
                    utbetaling = listOf()
                )
            )
        ),
        val sak: Sak = Sak(
            fnr = fnr,
            søknader = mutableListOf(),
            oppdrag = Oppdrag(
                id = oppdragId,
                opprettet = Tidspunkt.EPOCH,
                sakId = sakId,
                utbetalinger = mutableListOf(
                    utbetaling1,
                    utbetaling2,
                    stansutbetaling
                )
            )
        ),
        val eksisterendeOppdrag: Oppdrag = Oppdrag(
            id = oppdragId,
            opprettet = Tidspunkt.EPOCH,
            sakId = sakId,
            utbetalinger = mutableListOf(
                utbetaling1, utbetaling2, stansutbetaling
            )
        )
    ) {
        fun forventetNyUtbetaling(
            actualUtbetaling: Utbetaling,
            simulering: Simulering? = null,
            oppdragsmelding: Oppdragsmelding? = null,
            utbetalingslinjer: List<Utbetalingslinje> = emptyList()
        ) = NyUtbetaling(
            oppdrag = eksisterendeOppdrag,
            utbetaling = forventetUtbetaling(actualUtbetaling, simulering, oppdragsmelding, utbetalingslinjer),
            attestant = attestant
        )

        fun forventetUtbetaling(
            actualUtbetaling: Utbetaling,
            simulering: Simulering? = null,
            oppdragsmelding: Oppdragsmelding? = null,
            utbetalingslinjer: List<Utbetalingslinje> = emptyList()
        ) = Utbetaling(
            id = actualUtbetaling.id,
            opprettet = actualUtbetaling.opprettet,
            utbetalingslinjer = utbetalingslinjer,
            fnr = fnr,
            simulering = simulering,
            oppdragsmelding = oppdragsmelding,
        )
    }
}
