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
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import org.junit.jupiter.api.Test
import org.mockito.internal.verification.Times

internal class UtbetalingServiceImplTest {

    @Test
    fun `hent utbetaling - ikke funnet`() {
        val repoMock = mock<UtbetalingRepo> { on { hentUtbetaling(any()) } doReturn null }

        UtbetalingServiceImpl(repoMock).hentUtbetaling(UUID30.randomUUID()) shouldBe FantIkkeUtbetaling.left()

        verify(repoMock, Times(1)).hentUtbetaling(any())
    }

    @Test
    fun `hent utbetaling - funnet`() {
        val utbetaling = Utbetaling(
            utbetalingslinjer = listOf(),
            fnr = Fnr("12345678910")
        )
        val repoMock = mock<UtbetalingRepo> { on { hentUtbetaling(any()) } doReturn utbetaling }

        UtbetalingServiceImpl(repoMock).hentUtbetaling(utbetaling.id) shouldBe utbetaling.right()

        verify(repoMock).hentUtbetaling(utbetaling.id)
    }

    @Test
    fun `oppdater med kvittering - ikke funnet`() {
        val kvittering = Kvittering(
            Kvittering.Utbetalingsstatus.OK,
            ""
        )

        val repoMock = mock<UtbetalingRepo> { on { hentUtbetaling(any()) } doReturn null }

        UtbetalingServiceImpl(repoMock).oppdaterMedKvittering(
            UUID30.randomUUID(),
            kvittering
        ) shouldBe FantIkkeUtbetaling.left()

        verify(repoMock, Times(1)).hentUtbetaling(any())
        verify(repoMock, Times(0)).oppdaterMedKvittering(any(), any())
    }

    @Test
    fun `oppdater med kvittering - kvittering eksisterer ikke fra før`() {
        val utbetaling = Utbetaling(
            utbetalingslinjer = listOf(),
            fnr = Fnr("12345678910")
        )
        val kvittering = Kvittering(
            Kvittering.Utbetalingsstatus.OK,
            ""
        )

        val postUpdate = utbetaling.copy(
            kvittering = kvittering
        )

        val repoMock = mock<UtbetalingRepo> {
            on { hentUtbetaling(utbetaling.id) } doReturn utbetaling
            on { oppdaterMedKvittering(utbetaling.id, kvittering) } doReturn postUpdate
        }

        UtbetalingServiceImpl(repoMock).oppdaterMedKvittering(utbetaling.id, kvittering) shouldBe postUpdate.right()

        verify(repoMock, Times(1)).hentUtbetaling(utbetaling.id)
        verify(repoMock, Times(1)).oppdaterMedKvittering(utbetaling.id, kvittering)
    }

    @Test
    fun `oppdater med kvittering - kvittering eksisterer fra før`() {
        val utbetaling = Utbetaling(
            utbetalingslinjer = listOf(),
            fnr = Fnr("12345678910"),
            kvittering = Kvittering(
                Kvittering.Utbetalingsstatus.OK,
                ""
            )
        )

        val nyKvittering = Kvittering(
            Kvittering.Utbetalingsstatus.OK,
            ""
        )

        val repoMock = mock<UtbetalingRepo> {
            on { hentUtbetaling(utbetaling.id) } doReturn utbetaling
        }

        UtbetalingServiceImpl(repoMock).oppdaterMedKvittering(utbetaling.id, nyKvittering) shouldBe utbetaling.right()

        verify(repoMock, Times(1)).hentUtbetaling(utbetaling.id)
        verify(repoMock, Times(0)).oppdaterMedKvittering(utbetaling.id, nyKvittering)
    }
}
