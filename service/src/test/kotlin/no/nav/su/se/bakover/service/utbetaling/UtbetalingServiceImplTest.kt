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
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import org.junit.jupiter.api.Test

internal class UtbetalingServiceImplTest {

    @Test
    fun `responses`() {
        val notFoundMock = mock<UtbetalingRepo> { on { hentUtbetaling(any()) } doReturn null }
        UtbetalingServiceImpl(notFoundMock).hentUtbetaling(UUID30.randomUUID()) shouldBe FantIkkeUtbetaling.left()
        verify(notFoundMock).hentUtbetaling(any())

        val utbetaling = Utbetaling(
            utbetalingslinjer = listOf(),
            fnr = Fnr("12345678910")
        )
        val foundMock = mock<UtbetalingRepo> { on { hentUtbetaling(any()) } doReturn utbetaling }
        UtbetalingServiceImpl(foundMock).hentUtbetaling(utbetaling.id) shouldBe utbetaling.right()
        verify(foundMock).hentUtbetaling(utbetaling.id)
    }
}
