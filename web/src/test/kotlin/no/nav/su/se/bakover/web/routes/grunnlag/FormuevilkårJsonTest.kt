package no.nav.su.se.bakover.web.routes.grunnlag

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.time.LocalDate
import java.util.UUID

internal class FormuevilkårJsonTest {

    @Test
    fun `serialize formuevilkårjson`() {
        val bosituasjon = Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
            id = UUID.fromString("5441d6ef-08c7-4a4f-8e4c-d17e1ab95789"),
            opprettet = fixedTidspunkt,
            periode = Periode.create(
                fraOgMed = LocalDate.of(2021, 1, 1),
                tilOgMed = LocalDate.of(2021, 12, 31),
            ),
            fnr = Fnr("12312312345"),
            begrunnelse = "bosituasjonBegrunnelse",
        )
        val vilkår = Vilkår.Formue.Vurdert.createFromVilkårsvurderinger(
            nonEmptyListOf(
                Vurderingsperiode.Formue.create(
                    id = UUID.fromString("2e9a75ea-20ca-47e9-8c31-0c091bb4e316"),
                    opprettet = fixedTidspunkt,
                    resultat = Resultat.Innvilget,
                    grunnlag = Formuegrunnlag.create(
                        id = UUID.fromString("5441d6ef-08c7-4a4f-8e4c-d17e1ab95789"),
                        opprettet = fixedTidspunkt,
                        periode = Periode.create(
                            fraOgMed = LocalDate.of(2021, 1, 1),
                            tilOgMed = LocalDate.of(2021, 6, 30),
                        ),
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
                        begrunnelse = "formueBegrunnelse",
                        bosituasjon = bosituasjon,
                        behandlingsPeriode = Periode.create(
                            fraOgMed = LocalDate.of(2021, 1, 1),
                            tilOgMed = LocalDate.of(2021, 12, 31),
                        ),
                    ),
                    periode = Periode.create(
                        fraOgMed = LocalDate.of(2021, 1, 1),
                        tilOgMed = LocalDate.of(2021, 6, 30),
                    ),
                ),
                Vurderingsperiode.Formue.create(
                    id = UUID.fromString("2403e105-b3fc-435a-a38b-e0a76ef9a73c"),
                    opprettet = fixedTidspunkt,
                    resultat = Resultat.Innvilget,
                    grunnlag = Formuegrunnlag.create(
                        id = UUID.fromString("36ba42b1-c919-4ba6-a90f-9505db28f04d"),
                        opprettet = fixedTidspunkt,
                        periode = Periode.create(
                            fraOgMed = LocalDate.of(2021, 7, 1),
                            tilOgMed = LocalDate.of(2021, 12, 31),
                        ),
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
                        begrunnelse = null,
                        bosituasjon = bosituasjon,
                        behandlingsPeriode = Periode.create(
                            fraOgMed = LocalDate.of(2021, 1, 1),
                            tilOgMed = LocalDate.of(2021, 12, 31),
                        ),
                    ),
                    periode = Periode.create(
                        fraOgMed = LocalDate.of(2021, 7, 1),
                        tilOgMed = LocalDate.of(2021, 12, 31),
                    ),
                ),
            ),
        ).toJson()
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
            },
            "begrunnelse":"formueBegrunnelse"
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
            },
            "begrunnelse":null
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
          "gyldigFra": "2022-05-01",
          "beløp": 55000
      },
      {
         "gyldigFra":"2021-05-01",
         "beløp":53200
      },
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
