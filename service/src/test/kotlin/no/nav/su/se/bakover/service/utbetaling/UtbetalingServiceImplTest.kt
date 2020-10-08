package no.nav.su.se.bakover.service.utbetaling

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.database.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import org.junit.jupiter.api.Test
import org.mockito.internal.verification.Times

internal class UtbetalingServiceImplTest {

    @Test
    fun `hent utbetaling - ikke funnet`() {
        val repoMock = mock<UtbetalingRepo> { on { hentUtbetaling(any<UUID30>()) } doReturn null }

        UtbetalingServiceImpl(repoMock).hentUtbetaling(UUID30.randomUUID()) shouldBe FantIkkeUtbetaling.left()

        verify(repoMock, Times(1)).hentUtbetaling(any<UUID30>())
    }

    @Test
    fun `hent utbetaling - funnet`() {
        val utbetaling = Utbetaling(
            utbetalingslinjer = listOf(),
            fnr = Fnr("12345678910")
        )
        val repoMock = mock<UtbetalingRepo> { on { hentUtbetaling(any<UUID30>()) } doReturn utbetaling }

        UtbetalingServiceImpl(repoMock).hentUtbetaling(utbetaling.id) shouldBe utbetaling.right()

        verify(repoMock).hentUtbetaling(utbetaling.id)
    }

    @Test
    fun `oppdater med kvittering - ikke funnet`() {
        val kvittering = Kvittering(
            Kvittering.Utbetalingsstatus.OK,
            ""
        )

        val avstemmingsnøkkel = Avstemmingsnøkkel()

        val repoMock = mock<UtbetalingRepo> { on { hentUtbetaling(avstemmingsnøkkel) } doReturn null }

        UtbetalingServiceImpl(repoMock).oppdaterMedKvittering(
            avstemmingsnøkkel = avstemmingsnøkkel,
            kvittering = kvittering
        ) shouldBe FantIkkeUtbetaling.left()

        verify(repoMock, Times(1)).hentUtbetaling(avstemmingsnøkkel)
        verify(repoMock, Times(0)).oppdaterMedKvittering(any(), any())
    }

    @Test
    fun `oppdater med kvittering - kvittering eksisterer ikke fra før`() {
        val avstemmingsnøkkel = Avstemmingsnøkkel()
        val utbetaling = Utbetaling(
            utbetalingslinjer = listOf(),
            fnr = Fnr("12345678910"),
            oppdragsmelding = Oppdragsmelding(Oppdragsmelding.Oppdragsmeldingstatus.SENDT, "", avstemmingsnøkkel)
        )
        val kvittering = Kvittering(
            Kvittering.Utbetalingsstatus.OK,
            ""
        )

        val postUpdate = utbetaling.copy(
            kvittering = kvittering
        )

        val repoMock = mock<UtbetalingRepo> {
            on { hentUtbetaling(avstemmingsnøkkel) } doReturn utbetaling
            on { oppdaterMedKvittering(utbetaling.id, kvittering) } doReturn postUpdate
        }

        UtbetalingServiceImpl(repoMock).oppdaterMedKvittering(
            utbetaling.oppdragsmelding!!.avstemmingsnøkkel,
            kvittering
        ) shouldBe postUpdate.right()

        verify(repoMock, Times(1)).hentUtbetaling(avstemmingsnøkkel)
        verify(repoMock, Times(1)).oppdaterMedKvittering(utbetaling.id, kvittering)
    }

    @Test
    fun `oppdater med kvittering - kvittering eksisterer fra før`() {
        val avstemmingsnøkkel = Avstemmingsnøkkel()
        val utbetaling = Utbetaling(
            utbetalingslinjer = listOf(),
            fnr = Fnr("12345678910"),
            kvittering = Kvittering(
                Kvittering.Utbetalingsstatus.OK,
                ""
            ),
            oppdragsmelding = Oppdragsmelding(Oppdragsmelding.Oppdragsmeldingstatus.SENDT, "", avstemmingsnøkkel)
        )

        val nyKvittering = Kvittering(
            utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
            originalKvittering = ""
        )

        val repoMock = mock<UtbetalingRepo> {
            on { hentUtbetaling(avstemmingsnøkkel) } doReturn utbetaling
        }

        UtbetalingServiceImpl(repoMock).oppdaterMedKvittering(
            avstemmingsnøkkel,
            nyKvittering
        ) shouldBe utbetaling.right()

        verify(repoMock, Times(1)).hentUtbetaling(avstemmingsnøkkel)
        verify(repoMock, Times(0)).oppdaterMedKvittering(utbetaling.id, nyKvittering)
    }
}
