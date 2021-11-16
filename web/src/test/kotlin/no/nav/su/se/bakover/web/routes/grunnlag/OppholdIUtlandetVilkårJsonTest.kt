package no.nav.su.se.bakover.web.routes.grunnlag

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.grunnlag.OppholdIUtlandetGrunnlag
import no.nav.su.se.bakover.domain.vilkår.OppholdIUtlandetVilkår
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeOppholdIUtlandet
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.periode2021
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class OppholdIUtlandetVilkårJsonTest {

    @Test
    fun `serialiserer vurdert opphold i utlandet`() {
        JSONAssert.assertEquals(expectedOppholdIUtlandetVurdert, serialize(oppholdIUtlandet.toJson()), true)
    }

    @Test
    fun `serialiserer ikke vurdert opphold i utlandet`() {
        JSONAssert.assertEquals(
            expectedOppholdIUtlandetIkkeVurdert,
            serialize(OppholdIUtlandetVilkår.IkkeVurdert.toJson()),
            true,
        )
    }

    companion object {
        private val vilkårsvurderingId = UUID.randomUUID()

        internal val vurderingsperiodeOppholdIUtlandet = VurderingsperiodeOppholdIUtlandet.create(
            id = vilkårsvurderingId,
            opprettet = fixedTidspunkt,
            resultat = Resultat.Innvilget,
            grunnlag = OppholdIUtlandetGrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = periode2021,
            ),
            periode = periode2021,
            begrunnelse = "jess",
        )

        internal val oppholdIUtlandet = OppholdIUtlandetVilkår.Vurdert.tryCreate(
            nonEmptyListOf(vurderingsperiodeOppholdIUtlandet),
        ).getOrFail()

        //language=JSON
        internal val expectedOppholdIUtlandetVurdert = """
            {
              "status": "SkalHoldeSegINorge",
              "begrunnelse": "jess"
            } 
        """.trimIndent()

        //language=JSON
        internal val expectedOppholdIUtlandetIkkeVurdert = """
            {
              "status": "Uavklart",
              "begrunnelse": null
            } 
        """.trimIndent()
    }
}
