package no.nav.su.se.bakover.web.routes.revurdering

import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrevingsbehandling
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.periode2021
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.sakId
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class TilbakekrevingsbehandlingJsonTest {

    private val ikkeAvgjort = Tilbakekrevingsbehandling.VurderTilbakekreving.IkkeAvgjort(
        id = UUID.randomUUID(),
        opprettet = fixedTidspunkt,
        sakId = sakId,
        revurderingId = revurderingId,
        periode = periode2021,
    )

    @Test
    fun `til json`() {
        val ikkeAvgjort = """
            {
                "avgjørelse":"IKKE_AVGJORT"
            }
        """.trimIndent()

        JSONAssert.assertEquals(ikkeAvgjort, serialize(this.ikkeAvgjort.toJson()!!), true)

        val forsto = """
            {
                "avgjørelse":"FORSTO"
            }
        """.trimIndent()

        JSONAssert.assertEquals(forsto, serialize(this.ikkeAvgjort.forsto().toJson()!!), true)

        val burdeForstått = """
            {
                "avgjørelse":"BURDE_FORSTÅTT"
            }
        """.trimIndent()

        JSONAssert.assertEquals(burdeForstått, serialize(this.ikkeAvgjort.burdeForstått().toJson()!!), true)

        val kunneIkkeForstått = """
            {
                "avgjørelse":"KUNNE_IKKE_FORSTÅ"
            }
        """.trimIndent()

        JSONAssert.assertEquals(kunneIkkeForstått, serialize(this.ikkeAvgjort.kunneIkkeForstå().toJson()!!), true)
    }
}
