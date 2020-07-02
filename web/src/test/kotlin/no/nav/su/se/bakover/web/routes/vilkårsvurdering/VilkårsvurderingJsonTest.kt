package no.nav.su.se.bakover.web.routes.vilkårsvurdering

import io.kotest.assertions.json.shouldMatchJson
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.Vilkår
import no.nav.su.se.bakover.domain.Vilkårsvurdering
import no.nav.su.se.bakover.domain.VilkårsvurderingDto.Companion.toDto
import no.nav.su.se.bakover.web.deserialize
import no.nav.su.se.bakover.web.serialize
import org.junit.jupiter.api.Test

internal class VilkårsvurderingJsonTest {
    //language=JSON
    val vilkårsvurderingJsonString = """
            {
                "UFØRHET": {
                    "id":1,
                    "begrunnelse":"uførhetBegrunnelse",
                    "status":"OK"
                }
            }
        """.trimIndent()

    val vilkårsvurderinger = listOf(
        Vilkårsvurdering(1, Vilkår.UFØRHET, "uførhetBegrunnelse", Vilkårsvurdering.Status.OK)
    )

    @Test
    fun `should serialize to json string`() {
        serialize(vilkårsvurderinger.toDto().toJson()) shouldMatchJson vilkårsvurderingJsonString
    }

    @Test
    fun `should deserialize json string`() {
        deserialize<VilkårsvurderingJson>(vilkårsvurderingJsonString).shouldBe(
            vilkårsvurderinger.map { it.toDto() }.toJson()
        )
    }
}
