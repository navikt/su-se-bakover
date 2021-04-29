package no.nav.su.se.bakover.web.routes.grunnlag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import no.nav.su.se.bakover.web.fixedClock
import no.nav.su.se.bakover.web.routes.grunnlag.søknadsbehandling.GrunnlagsdataSøknadsbehandlingJson
import no.nav.su.se.bakover.web.routes.grunnlag.søknadsbehandling.toSøknadsbehandlingJson
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.time.format.DateTimeFormatter
import java.util.UUID

internal class GrunnlagsdataJsonTest {

    @Test
    fun `serialiserer og deserialiserer runnlagsdata`() {
        JSONAssert.assertEquals(expectedGrunnlagsdataJson, serialize(grunnlagsdata.toSøknadsbehandlingJson()), true)
        deserialize<GrunnlagsdataSøknadsbehandlingJson>(expectedGrunnlagsdataJson) shouldBe grunnlagsdata.toSøknadsbehandlingJson()
    }

    @Test
    fun `serialiserer og deserialiserer vilkårsvurderinger`() {
        JSONAssert.assertEquals(expectedVilkårsvurderingJson, serialize(vilkårsvurderinger.toJson()), true)
        deserialize<VilkårsvurderingerJson>(expectedVilkårsvurderingJson) shouldBe vilkårsvurderinger.toJson()
    }
}

internal val uføregrunnlagId = UUID.randomUUID()
internal val uføregrunnlagOpprettet = Tidspunkt.now(fixedClock)

//language=JSON
internal val expectedUføregrunnlagJson = """
        {
          "id": "$uføregrunnlagId",
          "opprettet": "${DateTimeFormatter.ISO_INSTANT.format(uføregrunnlagOpprettet)}",
          "periode": {
            "fraOgMed": "2021-01-01",
            "tilOgMed": "2021-12-31"
          },
          "uføregrad": 50,
          "forventetInntekt": 12000
        }
""".trimIndent()

//language=JSON
private val expectedGrunnlagsdataJson = """
        {
          "uføre": $expectedUføregrunnlagJson
        }
""".trimIndent()

internal val vilkårsvurderingUføreId = UUID.randomUUID()
internal val vilkårsvurderingUføreOpprettet = Tidspunkt.now(fixedClock)

//language=JSON
internal val expectedVurderingUføreJson = """
    {
      "vilkår": "Uførhet",
      "vurdering": {
        "id": "$vilkårsvurderingUføreId",
        "opprettet": "${DateTimeFormatter.ISO_INSTANT.format(vilkårsvurderingUføreOpprettet)}",
        "resultat": "VilkårOppfylt",
        "grunnlag": $expectedUføregrunnlagJson,
        "periode": {
          "fraOgMed": "2021-01-01",
          "tilOgMed": "2021-12-31"
        },
        "begrunnelse": "text"
      },
      "resultat": "VilkårOppfylt"
    }
""".trimIndent()

private val expectedVilkårsvurderingJson = """
    {
        "uføre": $expectedVurderingUføreJson
    }
""".trimIndent()

internal val uføregrunnlag = Grunnlag.Uføregrunnlag(
    id = uføregrunnlagId,
    opprettet = uføregrunnlagOpprettet,
    periode = Periode.create(1.januar(2021), 31.desember(2021)),
    uføregrad = Uføregrad.parse(50),
    forventetInntekt = 12000,
)

internal val grunnlagsdata = Grunnlagsdata(
    uføregrunnlag = listOf(uføregrunnlag),
)

internal val vurderingsperiodeUføre = Vurderingsperiode.Manuell(
    id = vilkårsvurderingUføreId,
    opprettet = vilkårsvurderingUføreOpprettet,
    resultat = Resultat.Innvilget,
    grunnlag = uføregrunnlag,
    periode = Periode.create(1.januar(2021), 31.desember(2021)),
    begrunnelse = "text",
)

internal val uførevurdering = Vilkår.Vurdert.Uførhet(
    vurderingsperioder = listOf(vurderingsperiodeUføre),
)

internal val vilkårsvurderinger = Vilkårsvurderinger(
    uføre = uførevurdering,
)
