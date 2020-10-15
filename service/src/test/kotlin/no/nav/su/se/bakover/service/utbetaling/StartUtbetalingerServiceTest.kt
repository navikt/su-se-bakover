// package no.nav.su.se.bakover.service.utbetaling
//
// import arrow.core.left
// import arrow.core.right
// import com.nhaarman.mockitokotlin2.any
// import com.nhaarman.mockitokotlin2.capture
// import com.nhaarman.mockitokotlin2.doAnswer
// import com.nhaarman.mockitokotlin2.doReturn
// import com.nhaarman.mockitokotlin2.inOrder
// import com.nhaarman.mockitokotlin2.mock
// import com.nhaarman.mockitokotlin2.verify
// import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
// import io.kotest.matchers.shouldBe
// import no.nav.su.se.bakover.common.Tidspunkt
// import no.nav.su.se.bakover.common.UUID30
// import no.nav.su.se.bakover.common.april
// import no.nav.su.se.bakover.common.februar
// import no.nav.su.se.bakover.common.januar
// import no.nav.su.se.bakover.common.juli
// import no.nav.su.se.bakover.common.mars
// import no.nav.su.se.bakover.domain.Attestant
// import no.nav.su.se.bakover.domain.Fnr
// import no.nav.su.se.bakover.domain.Sak
// import no.nav.su.se.bakover.domain.oppdrag.NyUtbetaling
// import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
// import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
// import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding.Oppdragsmeldingstatus.FEIL
// import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding.Oppdragsmeldingstatus.SENDT
// import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
// import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
// import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
// import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
// import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
// import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
// import no.nav.su.se.bakover.service.argShouldBe
// import no.nav.su.se.bakover.service.argThat
// import no.nav.su.se.bakover.service.sak.FantIkkeSak
// import no.nav.su.se.bakover.service.sak.SakService
// import org.junit.jupiter.api.Test
// import org.mockito.ArgumentCaptor
// import org.mockito.internal.verification.Times
// import java.time.Clock
// import java.time.Instant
// import java.time.LocalDate
// import java.time.ZoneOffset
// import java.util.UUID
//
// internal class StartUtbetalingerServiceTest {
//
//     @Test
//     fun `Utbetalinger som er stanset blir startet igjen`() {
//         val setup = Setup()
//
//         val publisherMock = mock<UtbetalingPublisher> {
//             on {
//                 publish(
//                     argThat {
//                         it shouldBe setup.forventetNyUtbetaling(
//                             actualUtbetaling = it.utbetaling,
//                             simulering = setup.simulerStartutbetaling
//                         )
//                     }
//                 )
//             } doReturn setup.oppdragsmeldingSendt.right()
//         }
//
//         val sakServiceMock = mock<SakService> {
//             on { hentSak(argShouldBe(setup.sakId)) } doReturn setup.eksisterendeSak.right()
//         }
//
//         val capturedOpprettUtbetaling = ArgumentCaptor.forClass(Utbetaling.Gjenoppta::class.java)
//         val capturedAddSimulering = ArgumentCaptor.forClass(Simulering::class.java)
//         val capturedAddOppdragsmelding = ArgumentCaptor.forClass(Oppdragsmelding::class.java)
//         val utbetalingServiceMock = mock<UtbetalingService> {
//             on {
//                 simulerUtbetaling(
//                     argThat { it shouldBe setup.forventetNyUtbetaling(actualUtbetaling = it.utbetaling) }
//                 )
//             } doReturn setup.simulerStartutbetaling.right()
//             on {
//                 opprettUtbetaling(any(), capture<Utbetaling.Gjenoppta>(capturedOpprettUtbetaling))
//             } doAnswer { capturedOpprettUtbetaling.value }
//             on {
//                 addSimulering(any(), capture<Simulering>(capturedAddSimulering))
//             } doAnswer {
//                 capturedOpprettUtbetaling.value.copy(
//                     simulering = capturedAddSimulering.value
//                 )
//             }
//             on {
//                 addOppdragsmelding(any(), capture<Oppdragsmelding>(capturedAddOppdragsmelding))
//             } doAnswer {
//                 capturedOpprettUtbetaling.value.copy(
//                     simulering = capturedAddSimulering.value,
//                     oppdragsmelding = capturedAddOppdragsmelding.value
//                 )
//             }
//         }
//
//         val service = StartUtbetalingerService(
//             utbetalingPublisher = publisherMock,
//             utbetalingService = utbetalingServiceMock,
//             sakService = sakServiceMock,
//             clock = setup.clock
//         )
//         val actualSak = service.startUtbetalinger(setup.sakId)
//         val expectedUtbetaling = setup.forventetUtbetaling(
//             actualUtbetaling = actualSak.orNull()!!.oppdrag.hentUtbetalinger()[3],
//             simulering = setup.simulerStartutbetaling,
//             oppdragsmelding = setup.oppdragsmeldingSendt
//         )
//         val expectedSak = setup.eksisterendeSak.copy(
//             oppdrag = setup.eksisterendeOppdrag.copy(
//                 utbetalinger = setup.eksisterendeOppdrag.hentUtbetalinger() + expectedUtbetaling
//             )
//         )
//
//         actualSak shouldBe expectedSak.right()
//
//         inOrder(
//             sakServiceMock,
//             publisherMock,
//             utbetalingServiceMock,
//             utbetalingServiceMock
//         ) {
//             verify(sakServiceMock, Times(1)).hentSak(any<UUID>())
//             verify(utbetalingServiceMock, Times(1)).simulerUtbetaling(any())
//             verify(utbetalingServiceMock, Times(1)).opprettUtbetaling(any(), any())
//             verify(utbetalingServiceMock, Times(1)).addSimulering(any(), any())
//             verify(publisherMock, Times(1)).publish(any())
//             verify(utbetalingServiceMock, Times(1)).addOppdragsmelding(any(), any())
//         }
//     }
//
//     @Test
//     fun `Fant ikke sak`() {
//         val setup = Setup()
//
//         val repoMock = mock<SakService> {
//             on { hentSak(argThat<UUID> { it shouldBe setup.sakId }) } doReturn FantIkkeSak.left()
//         }
//
//         val service = StartUtbetalingerService(
//             utbetalingPublisher = mock(),
//             utbetalingService = mock(),
//             sakService = repoMock,
//             clock = setup.clock
//         )
//         val actualResponse = service.startUtbetalinger(sakId = setup.sakId)
//
//         actualResponse shouldBe StartUtbetalingFeilet.FantIkkeSak.left()
//
//         verify(repoMock, Times(1)).hentSak(any<UUID>())
//         verifyNoMoreInteractions(repoMock)
//     }
//
//     @Test
//     fun `Har ingen oversendte utbetalinger`() {
//         val setup = Setup()
//
//         val sak = setup.eksisterendeSak.copy(
//             oppdrag = setup.eksisterendeSak.oppdrag.copy(
//                 utbetalinger = setup.eksisterendeSak.oppdrag.hentUtbetalinger()
//                     .map { Utbetaling.Ny(utbetalingslinjer = emptyList(), fnr = setup.fnr) }.toMutableList()
//             )
//         )
//         val repoMock = mock<SakService> {
//             on { hentSak(argThat<UUID> { it shouldBe setup.sakId }) } doReturn sak.right()
//         }
//
//         val service = StartUtbetalingerService(
//             utbetalingPublisher = mock(),
//             utbetalingService = mock(),
//             sakService = repoMock,
//             clock = setup.clock
//         )
//         val actualResponse = service.startUtbetalinger(sakId = setup.sakId)
//
//         actualResponse shouldBe StartUtbetalingFeilet.HarIngenOversendteUtbetalinger.left()
//
//         verify(repoMock, Times(1)).hentSak(any<UUID>())
//         verifyNoMoreInteractions(repoMock)
//     }
//
//     @Test
//     fun `Siste utbetaling er ikke en stansutbetaling`() {
//         val setup = Setup()
//
//         val sak = setup.eksisterendeSak.copy(
//             oppdrag = setup.eksisterendeSak.oppdrag.copy(
//                 utbetalinger = setup.eksisterendeSak.oppdrag.hentUtbetalinger().toMutableList().also {
//                     it.removeLast()
//                 }
//             )
//         )
//         val repoMock = mock<SakService> {
//             on { hentSak(argThat<UUID> { it shouldBe setup.sakId }) } doReturn sak.right()
//         }
//
//         val service = StartUtbetalingerService(
//             utbetalingPublisher = mock(),
//             utbetalingService = mock(),
//             sakService = repoMock,
//             clock = setup.clock
//         )
//         val actualResponse = service.startUtbetalinger(sakId = setup.sakId)
//
//         actualResponse shouldBe StartUtbetalingFeilet.SisteUtbetalingErIkkeEnStansutbetaling.left()
//
//         verify(repoMock, Times(1)).hentSak(any<UUID>())
//
//         verifyNoMoreInteractions(repoMock)
//     }
//
//     @Test
//     fun `Simulering feilet`() {
//         val setup = Setup()
//
//         val sak = setup.eksisterendeSak.copy()
//         val repoMock = mock<SakService> {
//             on { hentSak(argThat<UUID> { it shouldBe setup.sakId }) } doReturn sak.right()
//         }
//
//         val utbetalingServiceMock = mock<UtbetalingService> {
//             on {
//                 simulerUtbetaling(
//                     argThat {
//                         it shouldBe setup.forventetNyUtbetaling(it.utbetaling)
//                     }
//                 )
//             } doReturn SimuleringFeilet.TEKNISK_FEIL.left()
//         }
//
//         val service = StartUtbetalingerService(
//             utbetalingPublisher = mock(),
//             utbetalingService = utbetalingServiceMock,
//             sakService = repoMock,
//             clock = setup.clock
//         )
//         val actualResponse = service.startUtbetalinger(sakId = setup.sakId)
//
//         actualResponse shouldBe StartUtbetalingFeilet.SimuleringAvStartutbetalingFeilet.left()
//
//         inOrder(repoMock, utbetalingServiceMock) {
//             verify(repoMock, Times(1)).hentSak(any<UUID>())
//             verify(utbetalingServiceMock, Times(1)).simulerUtbetaling(any())
//         }
//         verifyNoMoreInteractions(repoMock, utbetalingServiceMock)
//     }
//
//     @Test
//     fun `Utbetaling feilet`() {
//         val setup = Setup()
//
//         val publisherMock = mock<UtbetalingPublisher> {
//             on {
//                 publish(
//                     argThat {
//                         it shouldBe setup.forventetNyUtbetaling(
//                             actualUtbetaling = it.utbetaling,
//                             simulering = setup.simulerStartutbetaling
//                         )
//                     }
//                 )
//             } doReturn UtbetalingPublisher.KunneIkkeSendeUtbetaling(setup.oppdragsmeldingFeil).left()
//         }
//
//         val capturedOpprettUtbetaling = ArgumentCaptor.forClass(Utbetaling.Gjenoppta::class.java)
//         val capturedAddSimulering = ArgumentCaptor.forClass(Simulering::class.java)
//         val utbetalingServiceMock = mock<UtbetalingService> {
//             on {
//                 simulerUtbetaling(
//                     argThat {
//                         it shouldBe setup.forventetNyUtbetaling(
//                             actualUtbetaling = it.utbetaling
//                         )
//                     }
//                 )
//             } doReturn setup.simulerStartutbetaling.right()
//             on {
//                 opprettUtbetaling(any(), capture<Utbetaling.Gjenoppta>(capturedOpprettUtbetaling))
//             } doAnswer { capturedOpprettUtbetaling.value }
//             on {
//                 addSimulering(any(), capture<Simulering>(capturedAddSimulering))
//             } doAnswer {
//                 capturedOpprettUtbetaling.value.copy(
//                     simulering = capturedAddSimulering.value
//                 )
//             }
//         }
//
//         val repoMock = mock<SakService> {
//             on { hentSak(argShouldBe(setup.sakId)) } doReturn setup.eksisterendeSak.right()
//         }
//
//         val service = StartUtbetalingerService(
//             utbetalingPublisher = publisherMock,
//             utbetalingService = utbetalingServiceMock,
//             sakService = repoMock,
//             clock = setup.clock
//         )
//         val startetUtbetaling = service.startUtbetalinger(setup.sakId)
//
//         startetUtbetaling shouldBe StartUtbetalingFeilet.SendingAvUtebetalingTilOppdragFeilet.left()
//
//         inOrder(
//             repoMock,
//             publisherMock,
//             utbetalingServiceMock
//         ) {
//             verify(repoMock, Times(1)).hentSak(any<UUID>())
//             verify(utbetalingServiceMock, Times(1)).simulerUtbetaling(any())
//             verify(utbetalingServiceMock, Times(1)).opprettUtbetaling(any(), any())
//             verify(utbetalingServiceMock, Times(1)).addSimulering(any(), any())
//             verify(publisherMock, Times(1)).publish(any())
//             verify(utbetalingServiceMock, Times(1)).addOppdragsmelding(any(), any())
//         }
//     }
//
//     data class Setup(
//         val clock: Clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
//         val fnr: Fnr = Fnr("20128127969"),
//         val sakId: UUID = UUID.fromString("3ae00766-f055-4f8f-b816-42f4b7f8bc96"),
//         val oppdragsmeldingSendt: Oppdragsmelding = Oppdragsmelding(SENDT, "", Avstemmingsnøkkel()),
//         val oppdragsmeldingFeil: Oppdragsmelding = Oppdragsmelding(FEIL, "", Avstemmingsnøkkel()),
//         val attestant: Attestant = Attestant("SU"),
//         val oppdragId: UUID30 = UUID30.randomUUID(),
//         val eksisterendeUtbetalingslinje1: Utbetalingslinje = Utbetalingslinje(
//             fraOgMed = 1.januar(1970),
//             tilOgMed = 31.januar(1970),
//             beløp = 100,
//             forrigeUtbetalingslinjeId = null
//         ),
//         val eksisterendeUtbetalingslinje2: Utbetalingslinje = Utbetalingslinje(
//             fraOgMed = 1.februar(1970),
//             tilOgMed = 31.mars(1970),
//             beløp = 200,
//             forrigeUtbetalingslinjeId = eksisterendeUtbetalingslinje1.id
//         ),
//         val eksisterendeUtbetalingslinje3: Utbetalingslinje = Utbetalingslinje(
//             fraOgMed = 1.april(1970),
//             tilOgMed = 31.juli(1970),
//             beløp = 300,
//             forrigeUtbetalingslinjeId = eksisterendeUtbetalingslinje2.id
//         ),
//         val eksisterendeStansUtbetalingslinje: Utbetalingslinje = Utbetalingslinje(
//             fraOgMed = 1.januar(1970),
//             tilOgMed = 31.juli(1970),
//             beløp = 0,
//             forrigeUtbetalingslinjeId = eksisterendeUtbetalingslinje3.id
//         ),
//         val eksisterendeUtbetaling1: Utbetaling = Utbetaling.Ny(
//             utbetalingslinjer = listOf(eksisterendeUtbetalingslinje1),
//             oppdragsmelding = Oppdragsmelding(SENDT, "", Avstemmingsnøkkel()),
//             fnr = fnr
//         ),
//         val eksisterendeUtbetaling2: Utbetaling = Utbetaling.Ny(
//             utbetalingslinjer = listOf(eksisterendeUtbetalingslinje2, eksisterendeUtbetalingslinje3),
//             oppdragsmelding = Oppdragsmelding(SENDT, "", Avstemmingsnøkkel()),
//             fnr = fnr
//         ),
//         val eksisterendeStansutbetaling: Utbetaling = Utbetaling.Stans(
//             utbetalingslinjer = listOf(eksisterendeStansUtbetalingslinje),
//             oppdragsmelding = Oppdragsmelding(SENDT, "", Avstemmingsnøkkel()),
//             fnr = fnr
//         ),
//         // Denne inneholder bare dummy-data inntil "avstemming" av simuleringen er på plass
//         val simulerStartutbetaling: Simulering = Simulering(
//             gjelderId = fnr,
//             gjelderNavn = "",
//             datoBeregnet = LocalDate.EPOCH,
//             nettoBeløp = 0,
//             periodeList = emptyList()
//         ),
//         val eksisterendeOppdrag: Oppdrag = Oppdrag(
//             id = oppdragId,
//             opprettet = Tidspunkt.EPOCH,
//             sakId = sakId,
//             utbetalinger = mutableListOf(
//                 eksisterendeUtbetaling1,
//                 eksisterendeUtbetaling2,
//                 eksisterendeStansutbetaling
//             )
//         ),
//         val eksisterendeSak: Sak = Sak(
//             id = sakId,
//             opprettet = Tidspunkt.EPOCH,
//             fnr = fnr,
//             søknader = mutableListOf(),
//             oppdrag = eksisterendeOppdrag
//         )
//     ) {
//         fun forventetNyUtbetaling(
//             actualUtbetaling: Utbetaling,
//             simulering: Simulering? = null,
//             oppdragsmelding: Oppdragsmelding? = null
//         ) = NyUtbetaling(
//             oppdrag = eksisterendeOppdrag,
//             utbetaling = forventetUtbetaling(actualUtbetaling, simulering, oppdragsmelding),
//             attestant = attestant,
//             avstemmingsnøkkel = Avstemmingsnøkkel(Tidspunkt.now(clock))
//         )
//
//         fun forventetUtbetaling(
//             actualUtbetaling: Utbetaling,
//             simulering: Simulering? = null,
//             oppdragsmelding: Oppdragsmelding? = null,
//             utbetalingslinjer: List<Utbetalingslinje> = listOf(
//                 eksisterendeUtbetalingslinje1.copy(
//                     id = actualUtbetaling.utbetalingslinjer[0].id,
//                     opprettet = actualUtbetaling.utbetalingslinjer[0].opprettet,
//                     forrigeUtbetalingslinjeId = eksisterendeStansUtbetalingslinje.id
//                 ),
//                 eksisterendeUtbetalingslinje2.copy(
//                     id = actualUtbetaling.utbetalingslinjer[1].id,
//                     opprettet = actualUtbetaling.utbetalingslinjer[1].opprettet,
//                     forrigeUtbetalingslinjeId = actualUtbetaling.utbetalingslinjer[0].id
//                 ),
//                 eksisterendeUtbetalingslinje3.copy(
//                     id = actualUtbetaling.utbetalingslinjer[2].id,
//                     opprettet = actualUtbetaling.utbetalingslinjer[2].opprettet,
//                     forrigeUtbetalingslinjeId = actualUtbetaling.utbetalingslinjer[1].id
//                 )
//             ).also {
//                 check(actualUtbetaling.utbetalingslinjer.size == it.size)
//             }
//         ) = Utbetaling.Gjenoppta(
//             id = actualUtbetaling.id,
//             opprettet = actualUtbetaling.opprettet,
//             utbetalingslinjer = utbetalingslinjer,
//             fnr = fnr,
//             simulering = simulering,
//             oppdragsmelding = oppdragsmelding,
//         )
//     }
// }
