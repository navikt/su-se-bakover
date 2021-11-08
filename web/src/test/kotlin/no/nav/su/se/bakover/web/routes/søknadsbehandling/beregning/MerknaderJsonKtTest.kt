package no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning

import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.beregning.Merknad
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class MerknaderJsonKtTest {

    @Test
    fun `serialisering og av merknad for endret grunnbeløp`() {
        //language=json
        val expected = """
            {
              "type": "EndringGrunnbeløp",
              "gammeltGrunnbeløp": {
                "dato": "2020-05-01",
                "grunnbeløp": 101351
              },
              "nyttGrunnbeløp": {
                "dato": "2021-05-01",
                "grunnbeløp": 106399
              }
            }
        """.trimIndent()

        val merknad = Merknad.Beregning.EndringGrunnbeløp(
            gammeltGrunnbeløp = Merknad.Beregning.EndringGrunnbeløp.Detalj.forDato(1.mai(2020)),
            nyttGrunnbeløp = Merknad.Beregning.EndringGrunnbeløp.Detalj.forDato(1.mai(2021)),
        )
        JSONAssert.assertEquals(expected, serialize(merknad.toJson()), true)
    }

    @Test
    fun `serialisering og av merknad for beløp lik null`() {
        //language=json
        val expected = """
            {
              "type": "BeløpErNull"
            }
        """.trimIndent()

        val merknad = Merknad.Beregning.BeløpErNull
        JSONAssert.assertEquals(expected, serialize(merknad.toJson()), true)
    }

    @Test
    fun `serialisering og av merknad for beløp mellom null og to prosent`() {
        //language=json
        val expected = """
            {
              "type": "BeløpMellomNullOgToProsentAvHøySats"
            }
        """.trimIndent()

        val merknad = Merknad.Beregning.BeløpMellomNullOgToProsentAvHøySats
        JSONAssert.assertEquals(expected, serialize(merknad.toJson()), true)
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
        JSONAssert.assertEquals(expected, serialize(merknad.toJson()), true)
    }
}
