package no.nav.su.se.bakover.domain.beregning

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.objectMapper
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class MerknadTest {

    @Test
    fun `serialisering og deserialisering av endring grunnbeløp`() {
        val merknad = Merknad.EndringGrunnbeløp(
            gammeltGrunnbeløp = Merknad.EndringGrunnbeløp.Detalj.forDato(1.mai(2019)),
            nyttGrunnbeløp = Merknad.EndringGrunnbeløp.Detalj.forDato(1.mai(2020)),
        )

        //language=json
        val expectedJson = """
        {
            "type": "EndringGrunnbeløp",
            "gammeltGrunnbeløp": {
              "dato":"2019-05-01",
              "grunnbeløp": 99858
            },
            "nyttGrunnbeløp": {
              "dato":"2020-05-01",
              "grunnbeløp": 101351
            }
        }
        """.trimIndent()

        JSONAssert.assertEquals(expectedJson, objectMapper.writeValueAsString(merknad), true)
        merknad shouldBe objectMapper.readValue<Merknad>(expectedJson)
    }
}
