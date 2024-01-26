package no.nav.su.se.bakover.web.routes.grunnlag

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.juli
import no.nav.su.se.bakover.common.extensions.juni
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.vilkår.FormueVilkår
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeFormue
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.formuegrenserFactoryTestPåDato
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import vilkår.common.domain.Vurdering
import vilkår.common.domain.grunnlag.Bosituasjon
import java.util.UUID

internal class FormuevilkårJsonTest {

    @Test
    fun `serialize formuevilkårjson`() {
        val janDes = år(2021)
        val janJun = Periode.create(1.januar(2021), 30.juni(2021))
        val julDes = Periode.create(1.juli(2021), 31.desember(2021))
        val bosituasjonJanJun = Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
            id = UUID.fromString("5441d6ef-08c7-4a4f-8e4c-d17e1ab95789"),
            opprettet = fixedTidspunkt,
            periode = janDes,
            fnr = Fnr("12312312345"),
        )
        val bosituasjonJulDes = Bosituasjon.Fullstendig.Enslig(
            id = UUID.fromString("5441d6ef-08c7-4a4f-8e4c-d17e1ab95790"),
            opprettet = fixedTidspunkt,
            periode = janDes,
        )
        val vilkår = FormueVilkår.Vurdert.createFromVilkårsvurderinger(
            nonEmptyListOf(
                VurderingsperiodeFormue.create(
                    id = UUID.fromString("2e9a75ea-20ca-47e9-8c31-0c091bb4e316"),
                    opprettet = fixedTidspunkt,
                    vurdering = Vurdering.Innvilget,
                    grunnlag = Formuegrunnlag.create(
                        id = UUID.fromString("5441d6ef-08c7-4a4f-8e4c-d17e1ab95789"),
                        opprettet = fixedTidspunkt,
                        periode = janJun,
                        epsFormue = Formuegrunnlag.Verdier.create(
                            verdiIkkePrimærbolig = 1,
                            verdiEiendommer = 2,
                            verdiKjøretøy = 3,
                            innskudd = 4,
                            verdipapir = 5,
                            pengerSkyldt = 6,
                            kontanter = 7,
                            depositumskonto = 2,
                        ),
                        søkersFormue = Formuegrunnlag.Verdier.create(
                            verdiIkkePrimærbolig = 9,
                            verdiEiendommer = 10,
                            verdiKjøretøy = 11,
                            innskudd = 12,
                            verdipapir = 13,
                            pengerSkyldt = 14,
                            kontanter = 15,
                            depositumskonto = 2,
                        ),
                        bosituasjon = bosituasjonJanJun,
                        behandlingsPeriode = janDes,
                    ),
                    periode = janJun,
                ),
                VurderingsperiodeFormue.create(
                    id = UUID.fromString("2403e105-b3fc-435a-a38b-e0a76ef9a73c"),
                    opprettet = fixedTidspunkt,
                    vurdering = Vurdering.Innvilget,
                    grunnlag = Formuegrunnlag.create(
                        id = UUID.fromString("36ba42b1-c919-4ba6-a90f-9505db28f04d"),
                        opprettet = fixedTidspunkt,
                        periode = julDes,
                        epsFormue = null,
                        søkersFormue = Formuegrunnlag.Verdier.create(
                            verdiIkkePrimærbolig = 1,
                            verdiEiendommer = 2,
                            verdiKjøretøy = 3,
                            innskudd = 4,
                            verdipapir = 5,
                            pengerSkyldt = 6,
                            kontanter = 7,
                            depositumskonto = 2,
                        ),
                        bosituasjon = bosituasjonJulDes,
                        behandlingsPeriode = janDes,
                    ),
                    periode = julDes,
                ),
            ),
        ).toJson(formuegrenserFactoryTestPåDato())
        //language=JSON
        val expectedVilkårJson = """
{
   "vurderinger":[
      {
         "id":"2e9a75ea-20ca-47e9-8c31-0c091bb4e316",
         "opprettet":"2021-01-01T01:02:03.456789Z",
         "resultat":"VilkårOppfylt",
         "grunnlag":{
            "epsFormue":{
               "verdiIkkePrimærbolig":1,
               "verdiEiendommer":2,
               "verdiKjøretøy":3,
               "innskudd":4,
               "verdipapir":5,
               "pengerSkyldt":6,
               "kontanter":7,
               "depositumskonto":2
            },
            "søkersFormue":{
               "verdiIkkePrimærbolig":9,
               "verdiEiendommer":10,
               "verdiKjøretøy":11,
               "innskudd":12,
               "verdipapir":13,
               "pengerSkyldt":14,
               "kontanter":15,
               "depositumskonto":2
            }
         },
         "periode":{
            "fraOgMed":"2021-01-01",
            "tilOgMed":"2021-06-30"
         }
      },
      {
         "id":"2403e105-b3fc-435a-a38b-e0a76ef9a73c",
         "opprettet":"2021-01-01T01:02:03.456789Z",
         "resultat":"VilkårOppfylt",
         "grunnlag":{
            "epsFormue":null,
            "søkersFormue":{
               "verdiIkkePrimærbolig":1,
               "verdiEiendommer":2,
               "verdiKjøretøy":3,
               "innskudd":4,
               "verdipapir":5,
               "pengerSkyldt":6,
               "kontanter":7,
               "depositumskonto":2
            }
         },
         "periode":{
            "fraOgMed":"2021-07-01",
            "tilOgMed":"2021-12-31"
         }
      }
   ],
   "resultat":"VilkårOppfylt",
   "formuegrenser":[
      {
         "gyldigFra":"2020-05-01",
         "beløp":50676
      }
   ]
}
        """.trimIndent()
        JSONAssert.assertEquals(expectedVilkårJson, serialize(vilkår), true)
    }
}
