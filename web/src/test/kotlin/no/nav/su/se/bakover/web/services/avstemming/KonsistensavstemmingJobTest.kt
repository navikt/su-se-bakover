package no.nav.su.se.bakover.web.services.avstemming

import arrow.core.right
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.service.avstemming.AvstemmingService
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import økonomi.domain.Fagområde
import java.time.LocalDate

internal class KonsistensavstemmingJobTest {

    @Test
    fun `eksekverer ikke hvis ikke kjøreplan ikke inneholder dagens dato`() {
        val avstemmingService = mock<AvstemmingService>()
        KonsistensavstemmingJob.run(
            avstemmingService = avstemmingService,
            kjøreplan = emptySet(),
            clock = fixedClock,
            jobName = "test",
            log = mock(),
        ).also {
            verifyNoInteractions(avstemmingService)
        }
    }

    @Test
    fun `eksekveres ikke hvis allerede utført for aktuell dato`() {
        val avstemmingService = mock<AvstemmingService> {
            on { konsistensavstemming(any(), any()) } doReturn Avstemming.Konsistensavstemming.Ny(
                id = UUID30.randomUUID(),
                opprettet = fixedTidspunkt,
                løpendeFraOgMed = fixedTidspunkt,
                opprettetTilOgMed = fixedTidspunkt,
                utbetalinger = listOf(),
                avstemmingXmlRequest = null,
                fagområde = Fagområde.SUUFORE,
            ).right()
            on { konsistensavstemmingUtførtForOgPåDato(LocalDate.now(fixedClock), Fagområde.SUUFORE) } doReturn true
        }
        KonsistensavstemmingJob.run(
            avstemmingService = avstemmingService,
            kjøreplan = setOf(LocalDate.now(fixedClock)),
            clock = fixedClock,
            jobName = "test",
            log = mock(),
        ).also {
            verify(avstemmingService).konsistensavstemmingUtførtForOgPåDato(
                LocalDate.now(fixedClock),
                Fagområde.SUUFORE,
            )
            verifyNoMoreInteractions(avstemmingService)
        }
    }

    @Test
    fun `eksekveres hvis enda ikke utført for aktuell dato`() {
        val avstemmingService = mock<AvstemmingService> {
            on { konsistensavstemming(any(), any()) } doReturn Avstemming.Konsistensavstemming.Ny(
                id = UUID30.randomUUID(),
                opprettet = fixedTidspunkt,
                løpendeFraOgMed = fixedTidspunkt,
                opprettetTilOgMed = fixedTidspunkt,
                utbetalinger = listOf(),
                avstemmingXmlRequest = null,
                fagområde = Fagområde.SUUFORE,
            ).right()
            on { konsistensavstemmingUtførtForOgPåDato(LocalDate.now(fixedClock), Fagområde.SUUFORE) } doReturn false
        }
        KonsistensavstemmingJob.run(
            avstemmingService = avstemmingService,
            kjøreplan = setOf(LocalDate.now(fixedClock)),
            clock = fixedClock,
            jobName = "test",
            log = mock(),

        ).also {
            verify(avstemmingService).konsistensavstemmingUtførtForOgPåDato(
                LocalDate.now(fixedClock),
                Fagområde.SUUFORE,
            )
            verify(avstemmingService).konsistensavstemming(LocalDate.now(fixedClock), Fagområde.SUUFORE)
            verifyNoMoreInteractions(avstemmingService)
        }
    }
}
