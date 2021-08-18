package no.nav.su.se.bakover.web.routes.grunnlag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.test.grunnlagsdataEnsligMedFradrag
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.FradragJson
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

class FradragsgrunnlagJsonTest {

    @Test
    fun `serialiserer og deserialiserer fradragsgrunnlag`() {
        JSONAssert.assertEquals(expectedFradragsgrunnlagJson, serialize(fradragsgrunnlag.toJson()), true)
        deserialize<FradragJson>(expectedFradragsgrunnlagJson) shouldBe fradragsgrunnlag.toJson()
    }

    companion object {
        // internal val fradragsgrunnlagId = UUID.randomUUID()
        // internal val fradragsgrunnlagOpprettet = Tidspunkt.now(fixedClock)

        //language=JSON
        internal val expectedFradragsgrunnlagJson = """
            {
              "periode" : {
                "fraOgMed" : "2021-01-01",
                "tilOgMed" : "2021-12-31"
              },
              "type" : "Arbeidsinntekt",
              "beløp": 1000.0,
              "utenlandskInntekt": null,
              "tilhører": "BRUKER"
            }
        """.trimIndent()

        internal val fradragsgrunnlag = grunnlagsdataEnsligMedFradrag().fradragsgrunnlag.first()
        // internal val fradragsgrunnlagx = Grunnlag.Fradragsgrunnlag.tryCreate(
        //     id = fradragsgrunnlagId,
        //     opprettet = fradragsgrunnlagOpprettet,
        //     fradrag = TestFradrag,
        // ).orNull()!!
    }
}
