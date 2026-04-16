package no.nav.su.se.bakover.web.services.fradragssjekken

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.test.defaultMock
import no.nav.su.se.bakover.test.fixedClock
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import kotlin.test.assertFailsWith

internal class FradragsjobbenServiceTest {

    @Test
    fun `kan ikke kjøre fradragssjekk for fortiden`() {
        val service = lagService()
        val tidligereMaaned: Måned = Måned.now(fixedClock).minusMonths(1L)

        service.validerKjøringForMåned(tidligereMaaned) shouldBe FradragsSjekkFeil.DatoErTilbakeITid
    }

    @Test
    fun `kan ikke kjøre fradragssjekk for fremtiden`() {
        val service = lagService()
        val nesteMaaned: Måned = Måned.now(fixedClock).plusMonths(1L)

        service.validerKjøringForMåned(nesteMaaned) shouldBe FradragsSjekkFeil.DatoErFremITid
    }

    @Test
    fun `kan kjøre fradragssjekk for inneværende måned`() {
        val naaVærendeMåned: Måned = Måned.now(fixedClock)

        val service = lagService(
            fradragssjekkRunPostgresRepo = mock {
                on { harOrdinaerKjoringForMåned(naaVærendeMåned) } doReturn false
            },
        )

        service.validerKjøringForMåned(naaVærendeMåned) shouldBe null
    }

    @Test
    fun `kan ikke kjøre fradragssjekk for inneværende måned hvis vanlig kjøring er gjort`() {
        val naaVærendeMåned: Måned = Måned.now(fixedClock)

        val service = lagService(
            fradragssjekkRunPostgresRepo = mock {
                on { harOrdinaerKjoringForMåned(naaVærendeMåned) } doReturn true
            },
        )
        service.validerKjøringForMåned(naaVærendeMåned) shouldBe FradragsSjekkFeil.AlleredeKjørtForMåned
    }

    @Test
    fun `direkte kall til kjørFradragssjekkForMåned validerer også måned`() {
        val service = lagService()
        val tidligereMaaned: Måned = Måned.now(fixedClock).minusMonths(1L)

        assertFailsWith<IllegalArgumentException> {
            service.kjørFradragssjekkForMånedMedValidering(tidligereMaaned, dryRun = false)
        }
    }

    private fun lagService(
        fradragssjekkRunPostgresRepo: FradragssjekkRunPostgresRepo = mock(),
    ): FradragsjobbenServiceImpl {
        return FradragsjobbenServiceImpl(
            aapKlient = defaultMock(),
            pesysKlient = defaultMock(),
            sakService = defaultMock(),
            oppgaveService = defaultMock(),
            utbetalingsRepo = defaultMock(),
            satsFactory = defaultMock(),
            fradragssjekkRunPostgresRepo = fradragssjekkRunPostgresRepo,
            clock = fixedClock,
        )
    }
}
