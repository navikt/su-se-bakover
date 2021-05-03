package no.nav.su.se.bakover.domain.vilkår

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import org.junit.jupiter.api.Test

internal class VilkårsvurderingerTest {

    private val uføregrunnlag = Grunnlag.Uføregrunnlag(
        periode = Periode.create(1.januar(2021), 31.desember(2021)),
        uføregrad = Uføregrad.parse(20),
        forventetInntekt = 10_000,
    )

    @Test
    fun `alle vurderingsperioder innvilget gir innvilget vilkår`() {
        Vilkårsvurderinger(
            uføre = Vilkår.Vurdert.Uførhet(
                vurderingsperioder = listOf(
                    Vurderingsperiode.Manuell(
                        resultat = Resultat.Innvilget,
                        grunnlag = uføregrunnlag,
                        periode = Periode.create(1.januar(2021), 31.desember(2021)),
                        begrunnelse = "",
                    ),
                ),
            ),
        ).resultat shouldBe Resultat.Innvilget
    }

    @Test
    fun `ingen vurderingsperioder innvilget gir avslått vilkår`() {
        Vilkårsvurderinger(
            uføre = Vilkår.Vurdert.Uførhet(
                vurderingsperioder = listOf(
                    Vurderingsperiode.Manuell(
                        resultat = Resultat.Avslag,
                        grunnlag = uføregrunnlag,
                        periode = Periode.create(1.januar(2021), 31.desember(2021)),
                        begrunnelse = "",
                    ),
                ),
            ),
        ).resultat shouldBe Resultat.Avslag
    }

    @Test
    fun `ingen vurderingsperioder gir uavklart vilkår`() {
        Vilkårsvurderinger(
            uføre = Vilkår.IkkeVurdert.Uførhet,
        ).resultat shouldBe Resultat.Uavklart
    }

    @Test
    fun `kombinasjon av vurderingsperioder med avlsag og innvilgelse gir avslag`() {
        Vilkårsvurderinger(
            uføre = Vilkår.Vurdert.Uførhet(
                vurderingsperioder = listOf(
                    Vurderingsperiode.Manuell(
                        resultat = Resultat.Innvilget,
                        grunnlag = uføregrunnlag,
                        periode = Periode.create(1.januar(2021), 30.april(2021)),
                        begrunnelse = "",
                    ),
                    Vurderingsperiode.Manuell(
                        resultat = Resultat.Avslag,
                        grunnlag = uføregrunnlag,
                        periode = Periode.create(1.mai(2021), 31.desember(2021)),
                        begrunnelse = "",
                    ),
                ),
            ),
        ).resultat shouldBe Resultat.Avslag
    }
}
