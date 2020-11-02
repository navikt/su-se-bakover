package no.nav.su.se.bakover.service.oppdrag

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.database.oppdrag.OppdragRepo
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import org.junit.jupiter.api.Test
import org.mockito.internal.verification.Times
import java.util.UUID

internal class OppdragServiceImplTest {
    @Test
    fun `hent oppdrag`() {
        val repoMock = mock<OppdragRepo> { on { hentOppdrag(any()) } doReturn null }

        OppdragServiceImpl(repoMock).hentOppdrag(UUID.randomUUID()) shouldBe FantIkkeOppdrag.left()

        verify(repoMock, Times(1)).hentOppdrag(any())
    }

    @Test
    fun `hent oppdrag - funnet`() {
        val sakId = UUID.randomUUID()
        val oppdrag = Oppdrag(
            id = UUID30.randomUUID(),
            opprettet = Tidspunkt.now(),
            sakId = sakId,
            utbetalinger = emptyList()
        )
        val repoMock = mock<OppdragRepo> { on { hentOppdrag(sakId) } doReturn oppdrag }

        OppdragServiceImpl(repoMock).hentOppdrag(sakId) shouldBe oppdrag.right()

        verify(repoMock).hentOppdrag(sakId)
    }
}
