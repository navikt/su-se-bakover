package no.nav.su.se.bakover.service.utbetaling

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
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
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding.Oppdragsmeldingstatus.FEIL
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding.Oppdragsmeldingstatus.SENDT
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingPersistenceObserver
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertPeriode
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.service.argShouldBe
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.doAnswer
import no.nav.su.se.bakover.service.doNothing
import org.junit.jupiter.api.Test
import org.mockito.internal.verification.Times
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
                        it shouldBe setup.oppdragsmeldingSendt
                    }
                )
            } doAnswer { _: UUID30, o: Oppdragsmelding -> o }

            on {
                addSimulering(
                    argThat {
                        it shouldBe actualUtbetalingsId
                    },
                    argThat {
                        it shouldBe setup.simulerStartutbetaling
                    }
                )
            }.doNothing()
        }

        val oppdragPersistenceObserverMock = mock<Oppdrag.OppdragPersistenceObserver> {
            on {
                opprettUtbetaling(
                    argThat {
                        it shouldBe setup.oppdragId
                    },
                    argThat {
                        it shouldBe setup.forventetUtbetaling(
                            actualUtbetaling = it,
                            utbetalingslinjer = listOf(
                                setup.utbetalingslinje1,
                                setup.utbetalingslinje2,
                                setup.utbetalingslinje3
                            )
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
                            utbetalingslinjer = listOf(
                                setup.utbetalingslinje1,
                                setup.utbetalingslinje2,
                                setup.utbetalingslinje3
                            )
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
                            utbetalingslinjer = listOf(
                                setup.utbetalingslinje1,
                                setup.utbetalingslinje2,
                                setup.utbetalingslinje3
                            )
                        )
                            .copy(
                                oppdrag = setup.eksisterendeOppdrag.copy(utbetalinger = (setup.eksisterendeOppdrag.hentUtbetalinger() + it.utbetaling).toMutableList())
                            )
                    }
                )
            } doReturn setup.oppdragsmeldingSendt.right()
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
        val startetUtbetaling = service.startUtbetalinger(setup.sakId)

        startetUtbetaling shouldBe setup.forventetUtbetaling(
            startetUtbetaling.orNull()!!,
            simulering = setup.simulerStartutbetaling,
            oppdragsmelding = setup.oppdragsmeldingSendt,
            utbetalingslinjer = listOf(setup.utbetalingslinje1, setup.utbetalingslinje2, setup.utbetalingslinje3)
        ).right()

        inOrder(
            repoMock,
            publisherMock,
            oppdragPersistenceObserverMock,
            simuleringClientMock,
            utbetalingPersistenceObserverMock
        ) {
            verify(repoMock, Times(1)).hentSak(any<UUID>())
            verify(simuleringClientMock, Times(1)).simulerUtbetaling(any())
            verify(oppdragPersistenceObserverMock, Times(1)).opprettUtbetaling(any(), any())
            verify(utbetalingPersistenceObserverMock, Times(1)).addSimulering(any(), any())
            verify(publisherMock, Times(1)).publish(any())
            verify(utbetalingPersistenceObserverMock, Times(1)).addOppdragsmelding(any(), any())
        }
    }

    @Test
    fun `Fant ikke sak`() {
        val setup = Setup()

        val repoMock = mock<ObjectRepo> {
            on { hentSak(argThat<UUID> { it shouldBe setup.sakId }) } doReturn null
        }

        val service = StartUtbetalingerService(
            repo = repoMock,
            simuleringClient = mock(),
            utbetalingPublisher = mock()
        )
        val actualResponse = service.startUtbetalinger(sakId = setup.sakId)

        actualResponse shouldBe StartUtbetalingFeilet.FantIkkeSak.left()

        verify(repoMock, Times(1)).hentSak(any<UUID>())
        verifyNoMoreInteractions(repoMock)
    }

    @Test
    fun `Har ingen oversendte utbetalinger`() {
        val setup = Setup()

        val sak = setup.sak.copy(
            oppdrag = setup.sak.oppdrag.copy(
                utbetalinger = setup.sak.oppdrag.hentUtbetalinger()
                    .map { it.copy(oppdragsmelding = null) }
                    .toMutableList()
            )
        )
        val repoMock = mock<ObjectRepo> {
            on { hentSak(argThat<UUID> { it shouldBe setup.sakId }) } doReturn sak
        }

        val service = StartUtbetalingerService(
            repo = repoMock,
            simuleringClient = mock(),
            utbetalingPublisher = mock()
        )
        val actualResponse = service.startUtbetalinger(sakId = setup.sakId)

        actualResponse shouldBe StartUtbetalingFeilet.HarIngenOversendteUtbetalinger.left()

        verify(repoMock, Times(1)).hentSak(any<UUID>())
        verifyNoMoreInteractions(repoMock)
    }

    @Test
    fun `Siste utbetaling er ikke en stansutbetaling`() {
        val setup = Setup()

        val sak = setup.sak.copy(
            oppdrag = setup.sak.oppdrag.copy(
                utbetalinger = setup.sak.oppdrag.hentUtbetalinger().toMutableList().also {
                    it.removeLast()
                }
            )
        )
        val repoMock = mock<ObjectRepo> {
            on { hentSak(argThat<UUID> { it shouldBe setup.sakId }) } doReturn sak
        }

        val service = StartUtbetalingerService(
            repo = repoMock,
            simuleringClient = mock(),
            utbetalingPublisher = mock()
        )
        val actualResponse = service.startUtbetalinger(sakId = setup.sakId)

        actualResponse shouldBe StartUtbetalingFeilet.SisteUtbetalingErIkkeEnStansutbetaling.left()

        verify(repoMock, Times(1)).hentSak(any<UUID>())

        verifyNoMoreInteractions(repoMock)
    }

    @Test
    fun `Simulering feilet`() {
        val setup = Setup()

        val simuleringClientMock = mock<SimuleringClient> {
            on {
                simulerUtbetaling(
                    argThat {
                        it shouldBe setup.forventetNyUtbetaling(it.utbetaling)
                    }
                )
            } doReturn SimuleringFeilet.TEKNISK_FEIL.left()
        }

        val sak = setup.sak.copy()
        val repoMock = mock<ObjectRepo> {
            on { hentSak(argThat<UUID> { it shouldBe setup.sakId }) } doReturn sak
        }

        val service = StartUtbetalingerService(
            repo = repoMock,
            simuleringClient = simuleringClientMock,
            utbetalingPublisher = mock()
        )
        val actualResponse = service.startUtbetalinger(sakId = setup.sakId)

        actualResponse shouldBe StartUtbetalingFeilet.SimuleringAvStartutbetalingFeilet.left()

        inOrder(repoMock, simuleringClientMock) {
            verify(repoMock, Times(1)).hentSak(any<UUID>())
            verify(simuleringClientMock, Times(1)).simulerUtbetaling(any())
        }
        verifyNoMoreInteractions(repoMock, simuleringClientMock)
    }

    @Test
    fun `Utbetaling feilet`() {
        val setup = Setup()
        var actualUtbetalingsId: UUID30? = null

        val utbetalingPersistenceObserverMock = mock<UtbetalingPersistenceObserver> {
            on {
                addOppdragsmelding(
                    argThat {
                        it shouldBe actualUtbetalingsId
                    },
                    argThat {
                        it shouldBe setup.oppdragsmeldingFeil
                    }
                )
            } doAnswer { _: UUID30, o: Oppdragsmelding -> o }

            on {
                addSimulering(
                    argThat {
                        it shouldBe actualUtbetalingsId
                    },
                    argThat {
                        it shouldBe setup.simulerStartutbetaling
                    }
                )
            }.doNothing()
        }

        val oppdragPersistenceObserverMock = mock<Oppdrag.OppdragPersistenceObserver> {
            on {
                opprettUtbetaling(
                    argThat {
                        it shouldBe setup.oppdragId
                    },
                    argThat {
                        it shouldBe setup.forventetUtbetaling(
                            actualUtbetaling = it,
                            utbetalingslinjer = listOf(
                                setup.utbetalingslinje1,
                                setup.utbetalingslinje2,
                                setup.utbetalingslinje3
                            )
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
                            utbetalingslinjer = listOf(
                                setup.utbetalingslinje1,
                                setup.utbetalingslinje2,
                                setup.utbetalingslinje3
                            )
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
                            utbetalingslinjer = listOf(
                                setup.utbetalingslinje1,
                                setup.utbetalingslinje2,
                                setup.utbetalingslinje3
                            )
                        )
                            .copy(
                                oppdrag = setup.eksisterendeOppdrag.copy(utbetalinger = (setup.eksisterendeOppdrag.hentUtbetalinger() + it.utbetaling).toMutableList())
                            )
                    }
                )
            } doReturn UtbetalingPublisher.KunneIkkeSendeUtbetaling(setup.oppdragsmeldingFeil).left()
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
        val startetUtbetaling = service.startUtbetalinger(setup.sakId)

        startetUtbetaling shouldBe StartUtbetalingFeilet.SendingAvUtebetalingTilOppdragFeilet.left()

        inOrder(
            repoMock,
            publisherMock,
            oppdragPersistenceObserverMock,
            simuleringClientMock,
            utbetalingPersistenceObserverMock
        ) {
            verify(repoMock, Times(1)).hentSak(any<UUID>())
            verify(simuleringClientMock, Times(1)).simulerUtbetaling(any())
            verify(oppdragPersistenceObserverMock, Times(1)).opprettUtbetaling(any(), any())
            verify(utbetalingPersistenceObserverMock, Times(1)).addSimulering(any(), any())
            verify(publisherMock, Times(1)).publish(any())
            verify(utbetalingPersistenceObserverMock, Times(1)).addOppdragsmelding(any(), any())
        }
    }

    data class Setup(
        val fnr: Fnr = Fnr("20128127969"),
        val sakId: UUID = UUID.fromString("3ae00766-f055-4f8f-b816-42f4b7f8bc96"),
        val oppdragsmeldingSendt: Oppdragsmelding = Oppdragsmelding(SENDT, ""),
        val oppdragsmeldingFeil: Oppdragsmelding = Oppdragsmelding(FEIL, ""),
        val attestant: Attestant = Attestant("SU"),
        val oppdragId: UUID30 = UUID30.randomUUID(),
        val utbetalingslinje1: Utbetalingslinje = Utbetalingslinje(
            fom = 1.januar(1970),
            tom = 31.januar(1970),
            beløp = 100,
            forrigeUtbetalingslinjeId = null
        ),
        val utbetalingslinje2: Utbetalingslinje = Utbetalingslinje(
            fom = 1.februar(1970),
            tom = 31.mars(1970),
            beløp = 200,
            forrigeUtbetalingslinjeId = utbetalingslinje1.id
        ),
        val utbetalingslinje3: Utbetalingslinje = Utbetalingslinje(
            fom = 1.april(1970),
            tom = 31.juli(1970),
            beløp = 300,
            forrigeUtbetalingslinjeId = utbetalingslinje2.id
        ),
        val utbetaling1: Utbetaling = Utbetaling(
            utbetalingslinjer = listOf(
                utbetalingslinje1
            ),
            oppdragsmelding = Oppdragsmelding(SENDT, ""),
            fnr = fnr
        ),
        val utbet2Id: UUID30 = UUID30.randomUUID(),
        val utbetaling2: Utbetaling = Utbetaling(
            utbetalingslinjer = listOf(
                utbetalingslinje2,
                utbetalingslinje3
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
            id = sakId,
            opprettet = Tidspunkt.EPOCH,
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
            utbetalingslinjer: List<Utbetalingslinje> = listOf(utbetalingslinje1, utbetalingslinje2, utbetalingslinje3)
        ) = NyUtbetaling(
            oppdrag = eksisterendeOppdrag,
            utbetaling = forventetUtbetaling(actualUtbetaling, simulering, oppdragsmelding, utbetalingslinjer),
            attestant = attestant
        )

        fun forventetUtbetaling(
            actualUtbetaling: Utbetaling,
            simulering: Simulering? = null,
            oppdragsmelding: Oppdragsmelding? = null,
            utbetalingslinjer: List<Utbetalingslinje> = listOf(utbetalingslinje1, utbetalingslinje2, utbetalingslinje3)
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
