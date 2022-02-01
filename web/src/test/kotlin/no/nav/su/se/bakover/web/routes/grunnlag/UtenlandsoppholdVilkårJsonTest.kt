package no.nav.su.se.bakover.web.routes.grunnlag

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.grunnlag.Utenlandsoppholdgrunnlag
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeUtenlandsopphold
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class UtenlandsoppholdVilkårJsonTest {

    @Test
    fun `serialiserer vurdert opphold i utlandet`() {
        JSONAssert.assertEquals(expectedUtenlandsoppholdVurdert, serialize(utenlandsopphold.toJson()), true)
    }

    @Test
    fun `ikke-vurdert utenlandsopphold gir null`() {
        UtenlandsoppholdVilkår.IkkeVurdert.toJson() shouldBe null
    }

    companion object {
        internal val utenlandsopphold = UtenlandsoppholdVilkår.Vurdert.tryCreate(
            nonEmptyListOf(
                VurderingsperiodeUtenlandsopphold.create(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    resultat = Resultat.Avslag,
                    grunnlag = Utenlandsoppholdgrunnlag(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = Periode.create(1.januar(2021), 30.april(2021)),
                    ),
                    periode = Periode.create(1.januar(2021), 30.april(2021)),
                    begrunnelse = "jamba",
                ),
                VurderingsperiodeUtenlandsopphold.create(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    resultat = Resultat.Innvilget,
                    grunnlag = null,
                    periode = Periode.create(1.mai(2021), 31.desember(2021)),
                    begrunnelse = "jess",
                ),
            ),
        ).getOrFail()

        //language=JSON
        internal val expectedUtenlandsoppholdVurdert = """
            {
              "vurderinger": [
                {
                  "status": "SkalVæreMerEnn90DagerIUtlandet",
                  "begrunnelse": "jamba",
                  "periode": {
                    "fraOgMed": "2021-01-01",
                    "tilOgMed": "2021-04-30"
                  }
                },
                {
                  "status": "SkalHoldeSegINorge",
                  "begrunnelse": "jess",
                  "periode": {
                    "fraOgMed": "2021-05-01",
                    "tilOgMed": "2021-12-31"
                  }
                }
              ],
              "status": "SkalVæreMerEnn90DagerIUtlandet"
            } 
        """.trimIndent()
    }
}
