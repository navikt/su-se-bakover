package no.nav.su.se.bakover.domain.avkorting

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.test.avkorting.avkortingVedSøknadsbehandlingAvkortet
import no.nav.su.se.bakover.test.avkorting.avkortingVedSøknadsbehandlingSkalAvkortes
import org.junit.jupiter.api.Test
import java.util.UUID

internal class AvkortingVedSøknadsbehandlingTest {

    @Test
    fun `overgang SkalAvkortes til Avkortet`() {
        val søknadsbehandlingId = UUID.randomUUID()
        val id = UUID.randomUUID()
        val sakId = UUID.randomUUID()
        val revurderingId = UUID.randomUUID()
        avkortingVedSøknadsbehandlingSkalAvkortes(
            id = id,
            sakId = sakId,
            revurderingId = revurderingId,
        ).avkort(søknadsbehandlingId) shouldBe avkortingVedSøknadsbehandlingAvkortet(
            id = id,
            sakId = sakId,
            søknadsbehandlingId = søknadsbehandlingId,
            revurderingId = revurderingId,
        )
    }
}
