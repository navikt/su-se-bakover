package no.nav.su.se.bakover.web.routes.behandling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.Vilkår
import no.nav.su.se.bakover.domain.Vilkårsvurdering
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.web.routes.søknad.SøknadJsonTest.Companion.søknad
import no.nav.su.se.bakover.web.routes.søknad.SøknadJsonTest.Companion.søknadJsonString
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class BehandlingJsonTest {

    private val behandlingId = UUID.randomUUID()
    private val vv1id = UUID.randomUUID()
    private val vv2id = UUID.randomUUID()

    //language=JSON
    val behandlingJsonString = """
        {
          "id": "$behandlingId",
          "vilkårsvurderinger": {
            "UFØRHET": {
              "id": "$vv1id",
              "begrunnelse": "uførhetBegrunnelse",
              "status": "OK"
            },
            "FORMUE": {
              "id": "$vv2id",
              "begrunnelse": "formueBegrunnelse",
              "status": "IKKE_VURDERT"
            }
          },
          "søknad": $søknadJsonString
        }
        """.trimIndent()

    val behandling = Behandling(
        id = behandlingId,
        vilkårsvurderinger = mutableListOf(
            Vilkårsvurdering(
                id = vv1id,
                vilkår = Vilkår.UFØRHET,
                begrunnelse = "uførhetBegrunnelse",
                status = Vilkårsvurdering.Status.OK
            ),
            Vilkårsvurdering(
                id = vv2id,
                vilkår = Vilkår.FORMUE,
                begrunnelse = "formueBegrunnelse",
                status = Vilkårsvurdering.Status.IKKE_VURDERT
            )
        ),
        søknad = søknad
    )

    @Test
    fun `should serialize to json string`() {
        JSONAssert.assertEquals(behandlingJsonString, serialize(behandling.toDto().toJson()), true)
    }

    @Test
    fun `should deserialize json string`() {
        deserialize<BehandlingJson>(behandlingJsonString) shouldBe behandling.toDto().toJson()
    }
}
