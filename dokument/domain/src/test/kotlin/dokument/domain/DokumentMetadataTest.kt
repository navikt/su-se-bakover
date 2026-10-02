package dokument.domain

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class DokumentMetadataTest {
    @Test
    fun `historisk referanse erstatter ikke ordinær referanse`() {
        val sakId = UUID.randomUUID()
        val revurderingId = UUID.randomUUID()

        val ordinær = Dokument.Metadata(sakId = sakId, revurderingId = revurderingId)
        ordinær.revurderingId shouldBe revurderingId
        ordinær.historiskRevurderingId shouldBe null

        val historisk = Dokument.Metadata(sakId = sakId, historiskRevurderingId = revurderingId)
        historisk.revurderingId shouldBe null
        historisk.historiskRevurderingId shouldBe revurderingId
    }

    @Test
    fun `avviser ordinær og historisk revurderingsreferanse på samme dokument`() {
        val sakId = UUID.randomUUID()
        val revurderingId = UUID.randomUUID()
        val historiskRevurderingId = UUID.randomUUID()

        assertThrows<IllegalArgumentException> {
            Dokument.Metadata(
                sakId = sakId,
                revurderingId = revurderingId,
                historiskRevurderingId = historiskRevurderingId,
            )
        }
    }
}
