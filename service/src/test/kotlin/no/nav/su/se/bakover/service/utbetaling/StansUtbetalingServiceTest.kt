package no.nav.su.se.bakover.service.utbetaling

import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.oppdrag.SimulerUtbetalingRequest
import no.nav.su.se.bakover.domain.oppdrag.UtbetalRequest
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseKode
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseType
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulerUtbetalingForPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertDetaljer
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.utbetalingslinje
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.UUID

internal class StansUtbetalingServiceTest {
    private val fnr = Fnr("12345678910")
    private val sakId = UUID.randomUUID()
    private val saksnummer = Saksnummer(2021)
    private val saksbehandler = NavIdentBruker.Saksbehandler("Z123")

    private val simulering = Simulering(
        gjelderId = fnr,
        gjelderNavn = "navn",
        datoBeregnet = idag(fixedClock),
        nettoBeløp = 0,
        periodeList = listOf(),
    )
    private val oppdragsmelding = Utbetalingsrequest(
        value = "",
    )
    private val førsteUtbetalingslinjeId = UUID30.randomUUID()
    private val førsteUtbetalingslinje = utbetalingslinje(
        id = førsteUtbetalingslinjeId,
        periode = år(2021),
        beløp = 5,
    )
    private val stansUtbetaling = Utbetaling.UtbetalingForSimulering(
        id = UUID30.randomUUID(),
        opprettet = fixedTidspunkt,
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        utbetalingslinjer = nonEmptyListOf(
            Utbetalingslinje.Endring.Stans(
                utbetalingslinje = førsteUtbetalingslinje,
                virkningstidspunkt = 1.februar(2021),
                clock = fixedClock,
            ),
        ),
        type = Utbetaling.UtbetalingsType.STANS,
        behandler = NavIdentBruker.Saksbehandler("Z123"),
        avstemmingsnøkkel = Avstemmingsnøkkel(fixedTidspunkt),
        sakstype = Sakstype.UFØRE,
    )

    private val simulertUtbetaling = stansUtbetaling.toSimulertUtbetaling(simulering)

    private val oversendtUtbetaling = simulertUtbetaling.toOversendtUtbetaling(oppdragsmelding)

    private val sak: Sak = Sak(
        id = sakId,
        saksnummer = saksnummer,
        opprettet = Tidspunkt.EPOCH,
        fnr = fnr,
        utbetalinger = listOf(
            Utbetaling.UtbetalingForSimulering(
                id = UUID30.randomUUID(),
                opprettet = fixedTidspunkt,
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                utbetalingslinjer = nonEmptyListOf(
                    førsteUtbetalingslinje,
                ),
                type = Utbetaling.UtbetalingsType.NY,
                behandler = NavIdentBruker.Saksbehandler("Z123"),
                avstemmingsnøkkel = Avstemmingsnøkkel(fixedTidspunkt),
                sakstype = Sakstype.UFØRE,
            ).toSimulertUtbetaling(
                simulering,
            ).toOversendtUtbetaling(
                oppdragsmelding,
            ),
        ),
        type = Sakstype.UFØRE,
        uteståendeAvkorting = Avkortingsvarsel.Ingen,
    )

    private fun expectedUtbetalingslinje(opprettet: Tidspunkt) = Utbetalingslinje.Endring.Stans(
        id = førsteUtbetalingslinje.id,
        opprettet = opprettet,
        fraOgMed = førsteUtbetalingslinje.fraOgMed,
        tilOgMed = førsteUtbetalingslinje.tilOgMed,
        forrigeUtbetalingslinjeId = førsteUtbetalingslinje.forrigeUtbetalingslinjeId,
        beløp = førsteUtbetalingslinje.beløp,
        virkningstidspunkt = 1.februar(2021),
        uføregrad = førsteUtbetalingslinje.uføregrad,
    )

    @Test
    fun `stans utbetalinger`() {
        val sakServiceMock = mock<SakService> {
            on { hentSak(sak.id) } doReturn sak.right()
        }

        val simuleringClientMock = mock<SimuleringClient> {
            on { simulerUtbetaling(any()) } doReturn simulering.right()
        }

        val utbetalingRepoMock = mock<UtbetalingRepo> {
            on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
        }

        val utbetalingPublisherMock = mock<UtbetalingPublisher> {
            on {
                publish(
                    any(),
                )
            } doReturn oppdragsmelding.right()
        }

        val response = UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            sakService = sakServiceMock,
            simuleringClient = simuleringClientMock,
            utbetalingPublisher = utbetalingPublisherMock,
            clock = fixedClock,
        ).stansUtbetalinger(
            request = UtbetalRequest.Stans(
                request = SimulerUtbetalingRequest.Stans(
                    sakId = sak.id,
                    saksbehandler = saksbehandler,
                    stansdato = 1.februar(2021),
                ),
                simulering = simulering,
            ),
        ).getOrHandle { fail(it.toString()) }

        response.shouldBeType<Utbetaling.OversendtUtbetaling.UtenKvittering>().shouldBeEqualToIgnoringFields(
            other = oversendtUtbetaling,
            property = Utbetaling::id,
        )

        inOrder(
            sakServiceMock,
            simuleringClientMock,
            utbetalingPublisherMock,
            utbetalingRepoMock,
        ) {
            verify(sakServiceMock, times(2)).hentSak(
                sakId = argThat { it shouldBe sak.id },
            )
            verify(simuleringClientMock).simulerUtbetaling(
                argThat {
                    it shouldBe SimulerUtbetalingForPeriode(
                        utbetaling = stansUtbetaling.copy(
                            id = it.utbetaling.id,
                            opprettet = it.utbetaling.opprettet,
                            avstemmingsnøkkel = it.utbetaling.avstemmingsnøkkel,
                            utbetalingslinjer = nonEmptyListOf(
                                expectedUtbetalingslinje(it.utbetaling.utbetalingslinjer[0].opprettet),
                            ),
                        ),
                        simuleringsperiode = Periode.create(
                            fraOgMed = 1.februar(2021),
                            tilOgMed = 31.desember(2021),
                        ),
                    )
                },
            )
            verify(utbetalingPublisherMock).publish(
                argThat {
                    it shouldBe stansUtbetaling.copy(
                        id = it.id,
                        opprettet = it.opprettet,
                        avstemmingsnøkkel = it.avstemmingsnøkkel,
                        utbetalingslinjer = nonEmptyListOf(
                            expectedUtbetalingslinje(it.utbetalingslinjer[0].opprettet),
                        ),
                    ).toSimulertUtbetaling(simulering)
                },
            )

            verify(utbetalingRepoMock).defaultTransactionContext()

            verify(utbetalingRepoMock).opprettUtbetaling(
                argThat {
                    it shouldBe stansUtbetaling.copy(
                        id = it.id,
                        opprettet = it.opprettet,
                        avstemmingsnøkkel = it.avstemmingsnøkkel,
                        utbetalingslinjer = nonEmptyListOf(
                            expectedUtbetalingslinje(it.utbetalingslinjer[0].opprettet),
                        ),
                    ).toSimulertUtbetaling(simulering).toOversendtUtbetaling(oppdragsmelding)
                },
                anyOrNull(),
            )
        }
        verifyNoMoreInteractions(
            sakServiceMock,
            simuleringClientMock,
            utbetalingRepoMock,
            utbetalingPublisherMock,
        )
    }

    @Test
    fun `Sjekk at vi svarer furnuftig når simulering feiler`() {

        val sakServiceMock = mock<SakService> {
            on { hentSak(sak.id) } doReturn sak.right()
        }

        val simuleringClientMock = mock<SimuleringClient> {
            on { simulerUtbetaling(any()) } doReturn SimuleringFeilet.TEKNISK_FEIL.left()
        }

        val utbetalingRepoMock = mock<UtbetalingRepo>()
        val utbetalingPublisherMock = mock<UtbetalingPublisher>()

        val response = UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            sakService = sakServiceMock,
            simuleringClient = simuleringClientMock,
            utbetalingPublisher = utbetalingPublisherMock,
            clock = fixedClock,
        ).stansUtbetalinger(
            request = UtbetalRequest.Stans(
                request = SimulerUtbetalingRequest.Stans(
                    sakId = sak.id,
                    saksbehandler = saksbehandler,
                    stansdato = 1.februar(2021),
                ),
                simulering = simulering,
            ),
        )

        response shouldBe UtbetalStansFeil.KunneIkkeSimulere(SimulerStansFeilet.KunneIkkeSimulere(SimuleringFeilet.TEKNISK_FEIL))
            .left()

        inOrder(sakServiceMock, simuleringClientMock) {
            verify(sakServiceMock, times(2)).hentSak(sakId = argThat { it shouldBe sak.id })

            verify(simuleringClientMock).simulerUtbetaling(
                argThat {
                    it shouldBe SimulerUtbetalingForPeriode(
                        utbetaling = stansUtbetaling.copy(
                            id = it.utbetaling.id,
                            opprettet = it.utbetaling.opprettet,
                            avstemmingsnøkkel = it.utbetaling.avstemmingsnøkkel,
                            utbetalingslinjer = nonEmptyListOf(
                                expectedUtbetalingslinje(it.utbetaling.utbetalingslinjer[0].opprettet),
                            ),
                        ),
                        simuleringsperiode = Periode.create(
                            fraOgMed = 1.februar(2021),
                            tilOgMed = 31.desember(2021),
                        ),
                    )
                },
            )
        }
        verifyNoMoreInteractions(
            sakServiceMock,
            simuleringClientMock,
            utbetalingRepoMock,
            utbetalingPublisherMock,
        )
    }

    @Test
    fun `Sjekk at vi svarer furnuftig når utbetaling feiler`() {
        val sakServiceMock = mock<SakService> {
            on { hentSak(sak.id) } doReturn sak.right()
        }

        val simuleringClientMock = mock<SimuleringClient> {
            on { simulerUtbetaling(any()) } doReturn simulering.right()
        }

        val utbetalingRepoMock = mock<UtbetalingRepo>()

        val utbetalingPublisherMock = mock<UtbetalingPublisher> {
            on {
                publish(any())
            } doReturn UtbetalingPublisher.KunneIkkeSendeUtbetaling(oppdragsmelding).left()
        }

        val response = UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            sakService = sakServiceMock,
            simuleringClient = simuleringClientMock,
            utbetalingPublisher = utbetalingPublisherMock,
            clock = fixedClock,
        ).stansUtbetalinger(
            request = UtbetalRequest.Stans(
                request = SimulerUtbetalingRequest.Stans(
                    sakId = sak.id,
                    saksbehandler = saksbehandler,
                    stansdato = 1.februar(2021),
                ),
                simulering = simulering,
            ),
        )

        response shouldBe UtbetalStansFeil.KunneIkkeUtbetale(UtbetalingFeilet.Protokollfeil).left()

        inOrder(
            sakServiceMock,
            simuleringClientMock,
            utbetalingPublisherMock,
            utbetalingRepoMock,
        ) {
            verify(sakServiceMock, times(2)).hentSak(
                sakId = argThat { it shouldBe sak.id },
            )

            verify(simuleringClientMock).simulerUtbetaling(
                argThat {
                    it shouldBe SimulerUtbetalingForPeriode(
                        utbetaling = stansUtbetaling.copy(
                            id = it.utbetaling.id,
                            opprettet = it.utbetaling.opprettet,
                            avstemmingsnøkkel = it.utbetaling.avstemmingsnøkkel,
                            utbetalingslinjer = nonEmptyListOf(
                                expectedUtbetalingslinje(it.utbetaling.utbetalingslinjer[0].opprettet),
                            ),
                        ),
                        simuleringsperiode = Periode.create(
                            fraOgMed = 1.februar(2021),
                            tilOgMed = 31.desember(2021),
                        ),
                    )
                },
            )
            verify(utbetalingPublisherMock).publish(
                argThat {
                    it shouldBe stansUtbetaling.copy(
                        id = it.id,
                        opprettet = it.opprettet,
                        avstemmingsnøkkel = it.avstemmingsnøkkel,
                        utbetalingslinjer = nonEmptyListOf(
                            expectedUtbetalingslinje(it.utbetalingslinjer[0].opprettet),
                        ),
                    ).toSimulertUtbetaling(simulering)
                },
            )
        }
        verifyNoMoreInteractions(
            sakServiceMock,
            simuleringClientMock,
            utbetalingRepoMock,
            utbetalingPublisherMock,
        )
    }

    @Test
    fun `svarer med feil dersom kontroll av simulering ikke går bra`() {
        val sakServiceMock = mock<SakService> {
            on { hentSak(sak.id) } doReturn sak.right()
        }

        val simuleringMedProblemer = Simulering(
            gjelderId = fnr,
            gjelderNavn = "navn",
            datoBeregnet = idag(fixedClock),
            nettoBeløp = 15000,
            periodeList = listOf(
                SimulertPeriode(
                    fraOgMed = 1.januar(2021),
                    tilOgMed = førsteUtbetalingslinje.tilOgMed,
                    utbetaling = listOf(
                        SimulertUtbetaling(
                            fagSystemId = "",
                            utbetalesTilId = Fnr.generer(),
                            utbetalesTilNavn = "",
                            forfall = 1.januar(2021),
                            feilkonto = false,
                            detaljer = listOf(
                                // Tilsvarer en feilutbetaling
                                SimulertDetaljer(
                                    faktiskFraOgMed = 1.januar(2021),
                                    faktiskTilOgMed = 31.januar(2021),
                                    konto = "konto",
                                    belop = 8946,
                                    tilbakeforing = false,
                                    sats = 0,
                                    typeSats = "",
                                    antallSats = 0,
                                    uforegrad = 0,
                                    klassekode = KlasseKode.SUUFORE,
                                    klassekodeBeskrivelse = "",
                                    klasseType = KlasseType.YTEL,
                                ),
                                SimulertDetaljer(
                                    faktiskFraOgMed = 1.januar(2021),
                                    faktiskTilOgMed = 31.januar(2021),
                                    konto = "konto",
                                    belop = 8949,
                                    tilbakeforing = false,
                                    sats = 0,
                                    typeSats = "",
                                    antallSats = 0,
                                    uforegrad = 0,
                                    klassekode = KlasseKode.KL_KODE_FEIL_INNT,
                                    klassekodeBeskrivelse = "",
                                    klasseType = KlasseType.FEIL,
                                ),
                                SimulertDetaljer(
                                    faktiskFraOgMed = 1.januar(2021),
                                    faktiskTilOgMed = 31.januar(2021),
                                    konto = "konto",
                                    belop = -8949,
                                    tilbakeforing = true,
                                    sats = 0,
                                    typeSats = "",
                                    antallSats = 0,
                                    uforegrad = 0,
                                    klassekode = KlasseKode.SUUFORE,
                                    klassekodeBeskrivelse = "",
                                    klasseType = KlasseType.YTEL,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val simuleringClientMock = mock<SimuleringClient> {
            on { simulerUtbetaling(any()) } doReturn simuleringMedProblemer.right()
        }

        val utbetalingRepoMock = mock<UtbetalingRepo>()

        val utbetalingPublisherMock = mock<UtbetalingPublisher>()

        UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            sakService = sakServiceMock,
            simuleringClient = simuleringClientMock,
            utbetalingPublisher = utbetalingPublisherMock,
            clock = fixedClock,
        ).stansUtbetalinger(
            request = UtbetalRequest.Stans(
                request = SimulerUtbetalingRequest.Stans(
                    sakId = sak.id,
                    saksbehandler = saksbehandler,
                    stansdato = 1.februar(2021),
                ),
                simulering = simuleringMedProblemer,
            ),
        ) shouldBe UtbetalStansFeil.KunneIkkeUtbetale(UtbetalingFeilet.KontrollAvSimuleringFeilet).left()

        verify(utbetalingPublisherMock, never()).publish(any())
        verify(utbetalingRepoMock, never()).opprettUtbetaling(any(), anyOrNull())
    }

    @Test
    fun `svarer med feil dersom første simulering er forskjellig fra konstrollsimulering`() {
        val sakServiceMock = mock<SakService> {
            on { hentSak(sak.id) } doReturn sak.right()
        }

        val simuleringClientMock = mock<SimuleringClient> {
            on { simulerUtbetaling(any()) } doReturn simulering.right()
        }

        val utbetalingRepoMock = mock<UtbetalingRepo>()

        val utbetalingPublisherMock = mock<UtbetalingPublisher> {
            on { publish(any()) } doReturn oppdragsmelding.right()
        }

        UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            sakService = sakServiceMock,
            simuleringClient = simuleringClientMock,
            utbetalingPublisher = utbetalingPublisherMock,
            clock = fixedClock,
        ).stansUtbetalinger(
            request = UtbetalRequest.Stans(
                request = SimulerUtbetalingRequest.Stans(
                    sakId = sak.id,
                    saksbehandler = saksbehandler,
                    stansdato = 1.februar(2021),
                ),
                simulering = simulering.copy(
                    periodeList = listOf(
                        SimulertPeriode(
                            fraOgMed = 1.mars(2021),
                            tilOgMed = 31.mars(2021),
                            utbetaling = emptyList(),
                        ),
                    ),
                ),
            ),
        ) shouldBe UtbetalStansFeil.KunneIkkeUtbetale(UtbetalingFeilet.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte)
            .left()

        verify(
            utbetalingPublisherMock,
            never(),
        ).publish(any())
        verify(utbetalingRepoMock, never()).opprettUtbetaling(any(), anyOrNull())
    }
}
