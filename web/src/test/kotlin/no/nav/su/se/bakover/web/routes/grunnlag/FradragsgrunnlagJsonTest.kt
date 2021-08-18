package no.nav.su.se.bakover.web.routes.grunnlag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.web.fixedClock
import no.nav.su.se.bakover.web.routes.søknadsbehandling.TestFradrag
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.FradragJson
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

class FradragsgrunnlagJsonTest {

    @Test
    fun `serialiserer og deserialiserer fradragsgrunnlag`() {
        JSONAssert.assertEquals(expectedFradragsgrunnlagJson, serialize(fradragsgrunnlag.toJson()), true)
        deserialize<FradragJson>(expectedFradragsgrunnlagJson) shouldBe fradragsgrunnlag.toJson()
    }

    companion object {
        internal val fradragsgrunnlagId = UUID.randomUUID()
        internal val fradragsgrunnlagOpprettet = Tidspunkt.now(fixedClock)

        //language=JSON
        internal val expectedFradragsgrunnlagJson = """
            {
              "periode" : {
                "fraOgMed" : "2020-08-01",
                "tilOgMed" : "2020-08-31"
              },
              "type" : "Arbeidsinntekt",
              "beløp": 1000.0,
              "utenlandskInntekt": null,
              "tilhører": "BRUKER"
            }
        """.trimIndent()

        internal val fradragsgrunnlag = Grunnlag.Fradragsgrunnlag.tryCreate(
            id = fradragsgrunnlagId,
            opprettet = fradragsgrunnlagOpprettet,
            fradrag = TestFradrag,
        ).orNull()!!
    }
}
