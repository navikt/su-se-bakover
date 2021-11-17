package no.nav.su.se.bakover.web.routes.grunnlag

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.grunnlag.Utenlandsoppholdgrunnlag
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeUtenlandsopphold
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.periode2021
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class UtenlandsoppholdVilkårJsonTestTest {

    @Test
    fun `serialiserer vurdert opphold i utlandet`() {
        JSONAssert.assertEquals(expectedUtenlandsoppholdVurdert, serialize(utenlandsopphold.toJson()), true)
    }

    @Test
    fun `serialiserer ikke vurdert opphold i utlandet`() {
        JSONAssert.assertEquals(
            expectedUtenlandsoppholdIkkeVurdert,
            serialize(UtenlandsoppholdVilkår.IkkeVurdert.toJson()),
            true,
        )
    }

    companion object {
        private val vilkårsvurderingId = UUID.randomUUID()

        internal val vurderingsperiodeUtenlandsopphold = VurderingsperiodeUtenlandsopphold.create(
            id = vilkårsvurderingId,
            opprettet = fixedTidspunkt,
            resultat = Resultat.Innvilget,
            grunnlag = Utenlandsoppholdgrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = periode2021,
            ),
            periode = periode2021,
            begrunnelse = "jess",
        )

        internal val utenlandsopphold = UtenlandsoppholdVilkår.Vurdert.tryCreate(
            nonEmptyListOf(vurderingsperiodeUtenlandsopphold),
        ).getOrFail()

        //language=JSON
        internal val expectedUtenlandsoppholdVurdert = """
            {
              "status": "SkalHoldeSegINorge",
              "begrunnelse": "jess"
            } 
        """.trimIndent()

        //language=JSON
        internal val expectedUtenlandsoppholdIkkeVurdert = """
            {
              "status": "Uavklart",
              "begrunnelse": null
            } 
        """.trimIndent()
    }
}
