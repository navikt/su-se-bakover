package no.nav.su.se.bakover.database.beregning

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.beregning.Merknad
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class PersistertMerknadTest {
    @Test
    fun `serialisering og av merknad for beløp lik null`() {
        //language=json
        val expected = """
            {
              "type": "BeløpErNull"
            }
        """.trimIndent()

        val merknad = Merknad.Beregning.Avslag.BeløpErNull
        JSONAssert.assertEquals(expected, serialize(merknad.toSnapshot()), true)

        merknad shouldBe merknad.toSnapshot().toDomain()
    }

    @Test
    fun `serialisering og av merknad for beløp mellom null og to prosent`() {
        //language=json
        val expected = """
            {
              "type": "BeløpMellomNullOgToProsentAvHøySats"
            }
        """.trimIndent()

        val merknad = Merknad.Beregning.Avslag.BeløpMellomNullOgToProsentAvHøySats
        JSONAssert.assertEquals(expected, serialize(merknad.toSnapshot()), true)

        merknad shouldBe merknad.toSnapshot().toDomain()
    }

    @Test
    fun `serialisering og av merknad for sosialstønad fører til under to prosent`() {
        //language=json
        val expected = """
            {
              "type": "SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats"
            }
        """.trimIndent()

        val merknad = Merknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats
        JSONAssert.assertEquals(expected, serialize(merknad.toSnapshot()), true)

        merknad shouldBe merknad.toSnapshot().toDomain()
    }
}
