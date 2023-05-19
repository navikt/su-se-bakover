package no.nav.su.se.bakover.web.routes.revurdering

import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeAvgjort
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.sakId
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class TilbakekrevingsbehandlingJsonTest {

    private val ikkeAvgjort = IkkeAvgjort(
        id = UUID.randomUUID(),
        opprettet = fixedTidspunkt,
        sakId = sakId,
        revurderingId = revurderingId,
        periode = år(2021),
    )

    @Test
    fun `til json`() {
        val ikkeAvgjort = """
            {
                "avgjørelse":"IKKE_AVGJORT"
            }
        """.trimIndent()

        JSONAssert.assertEquals(ikkeAvgjort, serialize(this.ikkeAvgjort.toJson()), true)

        val forsto = """
            {
                "avgjørelse":"TILBAKEKREV"
            }
        """.trimIndent()

        JSONAssert.assertEquals(
            forsto,
            serialize(this.ikkeAvgjort.tilbakekrev().toJson()),
            true,
        )

        val kunneIkkeForstått = """
            {
                "avgjørelse":"IKKE_TILBAKEKREV"
            }
        """.trimIndent()

        JSONAssert.assertEquals(
            kunneIkkeForstått,
            serialize(this.ikkeAvgjort.ikkeTilbakekrev().toJson()),
            true,
        )
    }
}
