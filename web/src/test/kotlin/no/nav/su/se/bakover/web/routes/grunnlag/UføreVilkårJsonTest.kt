package no.nav.su.se.bakover.web.routes.grunnlag

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.fixedTidspunkt
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
            },
            "begrunnelse": "text"
          }],
          "resultat": "VilkårOppfylt"
        }
        """.trimIndent()

        internal val vurderingsperiodeUføre = Vurderingsperiode.Uføre.create(
            id = vilkårsvurderingUføreId,
            opprettet = vilkårsvurderingUføreOpprettet,
            resultat = Resultat.Innvilget,
            grunnlag = uføregrunnlag,
            periode = Periode.create(1.januar(2021), 31.desember(2021)),
            begrunnelse = "text",
        )

        internal val uførevurdering = Vilkår.Uførhet.Vurdert.create(
            vurderingsperioder = nonEmptyListOf(vurderingsperiodeUføre),
        )

        internal val vilkårsvurderinger = Vilkårsvurderinger.Revurdering(
            uføre = uførevurdering,
            formue = Vilkår.Formue.IkkeVurdert,
            utenlandsopphold = UtenlandsoppholdVilkår.IkkeVurdert
        )
    }
}
