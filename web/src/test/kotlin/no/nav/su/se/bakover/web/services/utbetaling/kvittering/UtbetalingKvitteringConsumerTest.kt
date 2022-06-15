package no.nav.su.se.bakover.web.services.utbetaling.kvittering

import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.service.utbetaling.FantIkkeUtbetaling
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService.KunneIkkeFerdigstilleVedtak.FantIkkeVedtakForUtbetalingId
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.web.argThat
import no.nav.su.se.bakover.web.routes.søknadsbehandling.BehandlingTestUtils.sakId
import no.nav.su.se.bakover.web.routes.søknadsbehandling.BehandlingTestUtils.saksnummer
import no.nav.su.se.bakover.web.services.utbetaling.kvittering.UtbetalingKvitteringResponseTest.Companion.avstemmingsnøkkelIXml
import no.nav.su.se.bakover.web.services.utbetaling.kvittering.UtbetalingKvitteringResponseTest.Companion.kvitteringXml
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.internal.verification.Times
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

internal class UtbetalingKvitteringConsumerTest {

    private val avstemmingsnøkkel = Avstemmingsnøkkel.fromString(avstemmingsnøkkelIXml)
    private val utbetalingUtenKvittering = Utbetaling.UtbetalingForSimulering(
        opprettet = fixedTidspunkt,
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = Fnr.generer(),
        utbetalingslinjer = nonEmptyListOf(
            Utbetalingslinje.Ny(
                id = UUID30.randomUUID(),
                opprettet = Tidspunkt.EPOCH,
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.januar(2021),
                forrigeUtbetalingslinjeId = null,
                beløp = 0,
                uføregrad = Uføregrad.parse(50),
            ),
        ),
        type = Utbetaling.UtbetalingsType.NY,
        behandler = NavIdentBruker.Attestant("Z123"),
        avstemmingsnøkkel = avstemmingsnøkkel,
        sakstype = Sakstype.UFØRE,
    ).toSimulertUtbetaling(
        simulering = Simulering(
            gjelderId = Fnr("12345678910"),
            gjelderNavn = "navn",
            datoBeregnet = idag(fixedClock),
            nettoBeløp = 0,
            periodeList = listOf(),
        ),
    ).toOversendtUtbetaling(
        oppdragsmelding = Utbetalingsrequest(""),
    )
    private val kvittering = Kvittering(
        utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
        originalKvittering = "<xmlMessage>",
        mottattTidspunkt = Tidspunkt.now(fixedClock),
    )

    @Test
    fun `kaster exception hvis vi ikke klarer å oppdatere kvittering`() {
        val serviceMock = mock<UtbetalingService> {
            on { oppdaterMedKvittering(eq(avstemmingsnøkkel), any()) } doReturn FantIkkeUtbetaling.left()
        }
        val consumer = UtbetalingKvitteringConsumer(serviceMock, mock(), fixedClock)

        shouldThrow<RuntimeException> {
            consumer.onMessage(kvitteringXml())
        }.also {
            it.message shouldStartWith "Kunne ikke oppdatere kvittering eller vedtak ved prossessering av kvittering"
        }
        verify(serviceMock, Times(2)).oppdaterMedKvittering(any(), any())
    }

    @Test
    fun `kaster exception hvis vi ikke klarer å oppdatere vedtak`() {
        val utbetalingServiceMock = mock<UtbetalingService> {
            on {
                oppdaterMedKvittering(
                    eq(avstemmingsnøkkel),
                    any(),
                )
            } doReturn utbetalingUtenKvittering.toKvittertUtbetaling(kvittering).right()
        }
        val ferdigstillVedtakServiceMock = mock<FerdigstillVedtakService> {
            on { ferdigstillVedtakEtterUtbetaling(any()) } doReturn FantIkkeVedtakForUtbetalingId(UUID30.randomUUID()).left()
        }
        val consumer = UtbetalingKvitteringConsumer(utbetalingServiceMock, ferdigstillVedtakServiceMock, fixedClock)

        shouldThrow<RuntimeException> {
            consumer.onMessage(kvitteringXml())
        }.also {
            it.message shouldStartWith "Kunne ikke oppdatere kvittering eller vedtak ved prossessering av kvittering"
        }
        verify(ferdigstillVedtakServiceMock, Times(2)).ferdigstillVedtakEtterUtbetaling(any())
    }

    @Test
    fun `kaster videre eventuelle exceptions fra kall til ferdigstill`() {
        val xmlMessage = kvitteringXml(UtbetalingKvitteringResponse.Alvorlighetsgrad.OK)

        val kvittering = Kvittering(
            utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
            originalKvittering = xmlMessage,
            mottattTidspunkt = fixedTidspunkt,
        )
        val utbetalingMedKvittering = utbetalingUtenKvittering.toKvittertUtbetaling(kvittering)

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { oppdaterMedKvittering(any(), any()) } doReturn utbetalingMedKvittering.right()
        }

        val ferdigstillVedtakServiceMock = mock<FerdigstillVedtakService> {
            on { ferdigstillVedtakEtterUtbetaling(any()) }.thenThrow(IllegalArgumentException("Kastet fra FerdigstillIverksettingService"))
        }

        val consumer = UtbetalingKvitteringConsumer(utbetalingServiceMock, ferdigstillVedtakServiceMock, fixedClock)

        assertThrows<RuntimeException> {
            consumer.onMessage(xmlMessage)
        }.let {
            it.message shouldContain "Kastet fra FerdigstillIverksettingService"
        }

        verify(utbetalingServiceMock).oppdaterMedKvittering(
            avstemmingsnøkkel = argThat { it shouldBe avstemmingsnøkkel },
            kvittering = argThat { it shouldBe kvittering },
        )

        verify(ferdigstillVedtakServiceMock).ferdigstillVedtakEtterUtbetaling(
            argThat { it shouldBe utbetalingMedKvittering },
        )
    }
}
