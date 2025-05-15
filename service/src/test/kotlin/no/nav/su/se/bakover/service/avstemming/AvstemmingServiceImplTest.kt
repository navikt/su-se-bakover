package no.nav.su.se.bakover.service.avstemming

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.tid.endOfDay
import no.nav.su.se.bakover.common.domain.tid.juni
import no.nav.su.se.bakover.common.domain.tid.mars
import no.nav.su.se.bakover.common.domain.tid.startOfDay
import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.domain.oppdrag.avstemming.AvstemmingPublisher
import no.nav.su.se.bakover.domain.oppdrag.avstemming.AvstemmingRepo
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import økonomi.domain.Fagområde

internal class AvstemmingServiceImplTest {

    @ParameterizedTest
    @MethodSource("fagområdeProvider")
    fun `konsistensavstemming inkluderer utbetalinger opprettet til og med dagen før konsistensavstemming`(
        fagområde: Fagområde,
    ) {
        val avstemmingRepoMock = mock<AvstemmingRepo> {
            on { hentUtbetalingerForKonsistensavstemming(any(), any(), any()) } doReturn emptyList()
        }
        val publisherMock = mock<AvstemmingPublisher> {
            doAnswer {
                (it.arguments[0] as Avstemming.Konsistensavstemming.Ny).copy(avstemmingXmlRequest = "jippi").right()
            }.whenever(mock).publish(any<Avstemming.Konsistensavstemming.Ny>())
        }

        val expectedLøpendeFraOgMed = 17.mars(2021)
        val expectedOpprettetTilOgMed = 16.mars(2021)

        val konsistensavstemming = AvstemmingServiceImpl(
            repo = avstemmingRepoMock,
            publisher = publisherMock,
            clock = fixedClock,
        ).konsistensavstemming(
            løpendeFraOgMed = expectedLøpendeFraOgMed,
            fagområde = fagområde,
        ).getOrFail()

        konsistensavstemming shouldBe Avstemming.Konsistensavstemming.Ny(
            id = konsistensavstemming.id,
            opprettet = fixedTidspunkt,
            løpendeFraOgMed = expectedLøpendeFraOgMed.startOfDay(zoneIdOslo),
            opprettetTilOgMed = expectedOpprettetTilOgMed.endOfDay(zoneIdOslo),
            utbetalinger = emptyList(),
            avstemmingXmlRequest = "jippi",
            fagområde = fagområde,
        )

        inOrder(
            avstemmingRepoMock,
            publisherMock,
        ) {
            verify(avstemmingRepoMock).hentUtbetalingerForKonsistensavstemming(
                løpendeFraOgMed = expectedLøpendeFraOgMed.startOfDay(zoneIdOslo),
                opprettetTilOgMed = expectedOpprettetTilOgMed.endOfDay(zoneIdOslo),
                fagområde = fagområde,
            )
            verify(publisherMock).publish(
                konsistensavstemming.copy(
                    avstemmingXmlRequest = null,
                ),
            )
            verify(avstemmingRepoMock).opprettKonsistensavstemming(konsistensavstemming)
        }
    }

    @ParameterizedTest
    @MethodSource("fagområdeProvider")
    fun `svarer med feil dersom publisering feiler`(
        fagområde: Fagområde,
    ) {
        val avstemmingRepoMock = mock<AvstemmingRepo> {
            on { hentUtbetalingerForKonsistensavstemming(any(), any(), any()) } doReturn emptyList()
        }
        val publisherMock = mock<AvstemmingPublisher> {
            on { publish(any<Avstemming.Konsistensavstemming.Ny>()) } doReturn AvstemmingPublisher.KunneIkkeSendeAvstemming.left()
        }

        val expectedLøpendeFraOgMed = 17.mars(2021)
        val expectedOpprettetTilOgMed = 16.mars(2021)

        AvstemmingServiceImpl(
            repo = avstemmingRepoMock,
            publisher = publisherMock,
            clock = fixedClock,
        ).konsistensavstemming(
            løpendeFraOgMed = expectedLøpendeFraOgMed,
            fagområde = fagområde,
        ) shouldBe AvstemmingFeilet.left()

        inOrder(
            avstemmingRepoMock,
            publisherMock,
        ) {
            verify(avstemmingRepoMock).hentUtbetalingerForKonsistensavstemming(
                løpendeFraOgMed = expectedLøpendeFraOgMed.startOfDay(zoneIdOslo),
                opprettetTilOgMed = expectedOpprettetTilOgMed.endOfDay(zoneIdOslo),
                fagområde = fagområde,
            )
            verify(publisherMock).publish(any<Avstemming.Konsistensavstemming.Ny>())
            verify(avstemmingRepoMock, never()).opprettKonsistensavstemming(any())
        }
    }

    @ParameterizedTest
    @MethodSource("fagområdeProvider")
    fun `automatiskKonsistensavstemmingUtførtFor kaller repo med korrekte parametere`(
        fagområde: Fagområde,
    ) {
        val avstemmingRepoMock = mock<AvstemmingRepo> {
            on { konsistensavstemmingUtførtForOgPåDato(any(), any()) } doReturn false
        }

        AvstemmingServiceImpl(
            repo = avstemmingRepoMock,
            publisher = mock(),
            clock = fixedClock,
        ).konsistensavstemmingUtførtForOgPåDato(dato = 1.juni(2021), fagområde = fagområde) shouldBe false

        verify(avstemmingRepoMock).konsistensavstemmingUtførtForOgPåDato(
            dato = 1.juni(2021),
            fagområde = fagområde,
        )
    }

    companion object {
        @JvmStatic
        fun fagområdeProvider() = listOf(
            Arguments.of(Fagområde.SUUFORE),
            Arguments.of(Fagområde.SUALDER),
        )
    }
}
