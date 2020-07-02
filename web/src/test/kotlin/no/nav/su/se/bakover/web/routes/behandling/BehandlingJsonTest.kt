package no.nav.su.se.bakover.web.routes.behandling

import io.kotest.assertions.json.shouldMatchJson
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.Vilkår
import no.nav.su.se.bakover.domain.Vilkårsvurdering
import no.nav.su.se.bakover.web.deserialize
import no.nav.su.se.bakover.web.serialize
import org.junit.jupiter.api.Test

internal class BehandlingJsonTest {

    //language=JSON
    val behandlingJsonString = """
            {
                "id": 1,
                "vilkårsvurderinger": {
                  "UFØRHET": {
                    "id":1,
                    "begrunnelse":"uførhetBegrunnelse",
                    "status":"OK"
                   },
                   "FORMUE": {
                    "id":2,
                    "begrunnelse":"formueBegrunnelse",
                    "status":"IKKE_VURDERT"
                   }
                }
            }
        """.trimIndent()

    val behandling = Behandling(
        id = 1,
        vilkårsvurderinger = mutableListOf(
            Vilkårsvurdering(1, Vilkår.UFØRHET, "uførhetBegrunnelse", Vilkårsvurdering.Status.OK),
            Vilkårsvurdering(2, Vilkår.FORMUE, "formueBegrunnelse", Vilkårsvurdering.Status.IKKE_VURDERT)
        )
    )

    @Test
    fun `should serialize to json string`() {
        serialize(behandling.toDto().toJson()) shouldMatchJson behandlingJsonString
    }

    @Test
    fun `should deserialize json string`() {
        deserialize<BehandlingJson>(behandlingJsonString) shouldBe behandling.toDto().toJson()
    }
}
