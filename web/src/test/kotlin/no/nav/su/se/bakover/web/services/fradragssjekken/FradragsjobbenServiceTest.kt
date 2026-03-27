package no.nav.su.se.bakover.web.services.fradragssjekken

import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.test.defaultMock
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertFailsWith

internal class FradragsjobbenServiceTest {

    @Test
    fun `kan ikke kjøre fradragssjekk for tidligere måned`() {
        val service = lagService()

        assertFailsWith<FradragssjekkKanIkkeKjøresForTidligereMånedException> {
            service.validerKjøringForMåned(mars(2026), dryRun = false)
        }
    }

    @Test
    fun `kan kjøre fradragssjekk for inneværende måned`() {
        val service = lagService(
            fradragssjekkRunPostgresRepo = mock {
                on { harOrdinaerKjoringForMåned(april(2026)) } doReturn false
            },
        )

        service.validerKjøringForMåned(april(2026), dryRun = false)
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
            fradragssjekkRunPostgresRepo = fradragssjekkRunPostgresRepo,
            clock = Clock.fixed(Instant.parse("2026-04-15T12:00:00Z"), ZoneOffset.UTC),
        )
    }
}
