package no.nav.su.se.bakover.web.routes.vilkårsvurdering

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.Vilkår
import no.nav.su.se.bakover.domain.Vilkårsvurdering
import no.nav.su.se.bakover.domain.VilkårsvurderingDto.Companion.toDto
import no.nav.su.se.bakover.web.deserialize
import no.nav.su.se.bakover.web.serialize
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class VilkårsvurderingJsonTest {

    private val vvId = UUID.randomUUID()

    //language=JSON
    private val vilkårsvurderingJsonString = """
            {
                "UFØRHET": {
                    "id": "$vvId",
                    "begrunnelse":"uførhetBegrunnelse",
                    "status":"OK"
                }
            }
        """.trimIndent()

    val vilkårsvurderinger = listOf(
        Vilkårsvurdering(
            id = vvId,
            vilkår = Vilkår.UFØRHET,
            begrunnelse = "uførhetBegrunnelse",
            status = Vilkårsvurdering.Status.OK
        )
    )

    @Test
    fun `should serialize to json string`() {
        JSONAssert.assertEquals(vilkårsvurderingJsonString, serialize(vilkårsvurderinger.toDto().toJson()), true)
    }

    @Test
    fun `should deserialize json string`() {
        deserialize<VilkårsvurderingJson>(vilkårsvurderingJsonString).shouldBe(
            vilkårsvurderinger.map { it.toDto() }.toJson()
        )
    }
}
