package no.nav.su.se.bakover.web.routes.grunnlag

import arrow.core.nonEmptyListOf
import behandling.revurdering.domain.VilkårsvurderingerRevurdering
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.vilkår.InstitusjonsoppholdVilkår
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.vilkår.formuevilkårIkkeVurdert
import no.nav.su.se.bakover.utenlandsopphold.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.web.routes.grunnlag.UføregrunnlagJsonTest.Companion.expectedUføregrunnlagJson
import no.nav.su.se.bakover.web.routes.grunnlag.UføregrunnlagJsonTest.Companion.uføregrunnlag
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import vilkår.common.domain.Vurdering
import vilkår.fastopphold.domain.FastOppholdINorgeVilkår
import vilkår.flyktning.domain.FlyktningVilkår
import vilkår.lovligopphold.domain.LovligOppholdVilkår
import vilkår.opplysningsplikt.domain.OpplysningspliktVilkår
import vilkår.personligoppmøte.domain.PersonligOppmøteVilkår
import vilkår.uføre.domain.UføreVilkår
import vilkår.uføre.domain.VurderingsperiodeUføre
import java.time.format.DateTimeFormatter
import java.util.UUID

class UføreVilkårJsonTest {

    @Test
    fun `serialiserer og deserialiserer vilkårsvurdering for uføre`() {
        JSONAssert.assertEquals(
            expectedVurderingUføreJson,
            serialize(uførevurdering.toJson()),
            true,
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

        internal val vurderingsperiodeUføre = VurderingsperiodeUføre.create(
            id = vilkårsvurderingUføreId,
            opprettet = vilkårsvurderingUføreOpprettet,
            vurdering = Vurdering.Innvilget,
            grunnlag = uføregrunnlag,
            periode = år(2021),
        )

        internal val uførevurdering = UføreVilkår.Vurdert.create(
            vurderingsperioder = nonEmptyListOf(vurderingsperiodeUføre),
        )

        internal val vilkårsvurderinger = VilkårsvurderingerRevurdering.Uføre(
            uføre = uførevurdering,
            lovligOpphold = LovligOppholdVilkår.IkkeVurdert,
            formue = formuevilkårIkkeVurdert(),
            utenlandsopphold = UtenlandsoppholdVilkår.IkkeVurdert,
            opplysningsplikt = OpplysningspliktVilkår.IkkeVurdert,
            flyktning = FlyktningVilkår.IkkeVurdert,
            fastOpphold = FastOppholdINorgeVilkår.IkkeVurdert,
            personligOppmøte = PersonligOppmøteVilkår.IkkeVurdert,
            institusjonsopphold = InstitusjonsoppholdVilkår.IkkeVurdert,
        )
    }
}
