package no.nav.su.se.bakover.web.routes.grunnlag

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.tid.april
import no.nav.su.se.bakover.common.domain.tid.desember
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import vilkår.common.domain.Vurdering
import vilkår.utenlandsopphold.domain.vilkår.UtenlandsoppholdVilkår
import vilkår.utenlandsopphold.domain.vilkår.Utenlandsoppholdgrunnlag
import vilkår.utenlandsopphold.domain.vilkår.VurderingsperiodeUtenlandsopphold
import java.util.UUID

internal class UtenlandsoppholdVilkårJsonTest {

    @Test
    fun `serialiserer vurdert opphold i utlandet`() {
        JSONAssert.assertEquals(
            expectedUtenlandsoppholdVurdert,
            serialize(utenlandsopphold.toJson()),
            true,
        )
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
                    vurdering = Vurdering.Avslag,
                    grunnlag = Utenlandsoppholdgrunnlag(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = Periode.create(1.januar(2021), 30.april(2021)),
                    ),
                    periode = Periode.create(1.januar(2021), 30.april(2021)),
                ),
                VurderingsperiodeUtenlandsopphold.create(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    vurdering = Vurdering.Innvilget,
                    grunnlag = null,
                    periode = Periode.create(1.mai(2021), 31.desember(2021)),
                ),
            ),
        ).getOrFail()

        //language=JSON
        internal val expectedUtenlandsoppholdVurdert = """
            {
              "vurderinger": [
                {
                  "status": "SkalVæreMerEnn90DagerIUtlandet",
                  "periode": {
                    "fraOgMed": "2021-01-01",
                    "tilOgMed": "2021-04-30"
                  }
                },
                {
                  "status": "SkalHoldeSegINorge",
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
