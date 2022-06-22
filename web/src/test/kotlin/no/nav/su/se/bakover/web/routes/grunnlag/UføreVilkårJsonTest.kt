package no.nav.su.se.bakover.web.routes.grunnlag

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.vilkår.LovligOppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.vilkår.formuevilkårIkkeVurdert
import no.nav.su.se.bakover.web.routes.grunnlag.UføregrunnlagJsonTest.Companion.expectedUføregrunnlagJson
import no.nav.su.se.bakover.web.routes.grunnlag.UføregrunnlagJsonTest.Companion.uføregrunnlag
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.time.format.DateTimeFormatter
import java.util.UUID

class UføreVilkårJsonTest {

    @Test
    fun `serialiserer og deserialiserer vilkårsvurdering for uføre`() {
        JSONAssert.assertEquals(
            expectedVurderingUføreJson,
            serialize(uførevurdering.toJson()), true
        )
        deserialize<UføreVilkårJson>(expectedVurderingUføreJson) shouldBe uførevurdering.toJson()
    }

    companion object {
        private val vilkårsvurderingUføreId = UUID.randomUUID()
        private val vilkårsvurderingUføreOpprettet = fixedTidspunkt

        //language=JSON
        internal val expectedVurderingUføreJson = """
        {
          "vurderinger": [{
            "id": "$vilkårsvurderingUføreId",
            "opprettet": "${DateTimeFormatter.ISO_INSTANT.format(vilkårsvurderingUføreOpprettet)}",
            "resultat": "VilkårOppfylt",
            "grunnlag": $expectedUføregrunnlagJson,
            "periode": {
              "fraOgMed": "2021-01-01",
              "tilOgMed": "2021-12-31"
            }
          }],
          "resultat": "VilkårOppfylt"
        }
        """.trimIndent()

        internal val vurderingsperiodeUføre = Vurderingsperiode.Uføre.create(
            id = vilkårsvurderingUføreId,
            opprettet = vilkårsvurderingUføreOpprettet,
            resultat = Resultat.Innvilget,
            grunnlag = uføregrunnlag,
            periode = år(2021),
        )

        internal val uførevurdering = Vilkår.Uførhet.Vurdert.create(
            vurderingsperioder = nonEmptyListOf(vurderingsperiodeUføre),
        )

        internal val vilkårsvurderinger = Vilkårsvurderinger.Revurdering.Uføre(
            uføre = uførevurdering,
            lovligOpphold = LovligOppholdVilkår.IkkeVurdert,
            formue = formuevilkårIkkeVurdert(),
            utenlandsopphold = UtenlandsoppholdVilkår.IkkeVurdert,
            opplysningsplikt = OpplysningspliktVilkår.IkkeVurdert,
        )
    }
}
