package no.nav.su.se.bakover.domain.vilkår

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import org.junit.jupiter.api.Test

internal class VilkårsvurderingerTest {

    private val uføregrunnlag = Grunnlag.Uføregrunnlag(
        periode = Periode.create(1.januar(2021), 31.desember(2021)),
        uføregrad = Uføregrad.parse(20),
        forventetInntekt = 10_000,
    )

    private fun formueVilkår(periode: Periode) = Vilkår.Formue.Vurdert.create(
        grunnlag = nonEmptyListOf(
            Formuegrunnlag.create(
                periode = periode,
                epsFormue = null,
                søkersFormue = Formuegrunnlag.Verdier(
                    verdiIkkePrimærbolig = 0,
                    verdiEiendommer = 0,
                    verdiKjøretøy = 0,
                    innskudd = 0,
                    verdipapir = 0,
                    pengerSkyldt = 0,
                    kontanter = 0,
                    depositumskonto = 0,
                ),
                begrunnelse = null,
            ),
        ),
    )

    @Test
    fun `alle vurderingsperioder innvilget gir innvilget vilkår`() {
        val vurderingsperiode = Periode.create(1.januar(2021), 31.desember(2021))
        Vilkårsvurderinger(
            uføre = Vilkår.Uførhet.Vurdert.create(
                vurderingsperioder = nonEmptyListOf(
                    Vurderingsperiode.Uføre.create(
                        resultat = Resultat.Innvilget,
                        grunnlag = uføregrunnlag,
                        periode = vurderingsperiode,
                        begrunnelse = "",
                    ),
                ),
            ),
            formue = formueVilkår(vurderingsperiode),
        ).resultat shouldBe Resultat.Innvilget
    }

    @Test
    fun `ingen vurderingsperioder innvilget gir avslått vilkår`() {
        Vilkårsvurderinger(
            uføre = Vilkår.Uførhet.Vurdert.create(
                vurderingsperioder = nonEmptyListOf(
                    Vurderingsperiode.Uføre.create(
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
            uføre = Vilkår.Uførhet.IkkeVurdert,
        ).let {
            it.resultat shouldBe Resultat.Uavklart
            it.tidligsteDatoForAvslag() shouldBe null
        }
    }

    @Test
    fun `kombinasjon av vurderingsperioder med avslag og innvilgelse gir avslag`() {
        Vilkårsvurderinger(
            uføre = Vilkår.Uførhet.Vurdert.create(
                vurderingsperioder = nonEmptyListOf(
                    Vurderingsperiode.Uføre.create(
                        resultat = Resultat.Innvilget,
                        grunnlag = Grunnlag.Uføregrunnlag(
                            periode = Periode.create(1.januar(2021), 30.april(2021)),
                            uføregrad = Uføregrad.parse(20),
                            forventetInntekt = 10_000,
                        ),
                        periode = Periode.create(1.januar(2021), 30.april(2021)),
                        begrunnelse = "",
                    ),
                    Vurderingsperiode.Uføre.create(
                        resultat = Resultat.Avslag,
                        grunnlag = Grunnlag.Uføregrunnlag(
                            periode = Periode.create(1.mai(2021), 31.desember(2021)),
                            uføregrad = Uføregrad.parse(20),
                            forventetInntekt = 10_000,
                        ),
                        periode = Periode.create(1.mai(2021), 31.desember(2021)),
                        begrunnelse = "",
                    ),
                ),
            ),
        ).let {
            it.resultat shouldBe Resultat.Avslag
            it.tidligsteDatoForAvslag() shouldBe 1.mai(2021)
        }
    }

    @Test
    fun `oppdaterer perioden på grunnlagen riktig`() {
        val nyPeriode = Periode.create(1.februar(2021), 31.mars(2021))
        val vilkårsvurdering = Vilkårsvurderinger(
            uføre = Vilkår.Uførhet.Vurdert.create(
                vurderingsperioder = nonEmptyListOf(
                    Vurderingsperiode.Uføre.create(
                        resultat = Resultat.Innvilget,
                        grunnlag = Grunnlag.Uføregrunnlag(
                            periode = Periode.create(1.januar(2021), 30.april(2021)),
                            uføregrad = Uføregrad.parse(20),
                            forventetInntekt = 10_000,
                        ),
                        periode = Periode.create(1.januar(2021), 30.april(2021)),
                        begrunnelse = "",
                    ),
                    Vurderingsperiode.Uføre.create(
                        resultat = Resultat.Avslag,
                        grunnlag = Grunnlag.Uføregrunnlag(
                            periode = Periode.create(1.mai(2021), 31.desember(2021)),
                            uføregrad = Uføregrad.parse(20),
                            forventetInntekt = 10_000,
                        ),
                        periode = Periode.create(1.mai(2021), 31.desember(2021)),
                        begrunnelse = "",
                    ),
                ),
            ),
        )

        val actual = vilkårsvurdering.oppdaterStønadsperiode(Stønadsperiode.create(nyPeriode, "test"))
        actual.grunnlagsdata.uføregrunnlag.first().periode shouldBe nyPeriode
        actual.tidligsteDatoForAvslag() shouldBe 1.februar(2021)
    }

    @Test
    fun `uførhet som er avslag blir utledet`() {
        val vilkårsvurdering = Vilkårsvurderinger(
            uføre = Vilkår.Uførhet.Vurdert.create(
                vurderingsperioder = nonEmptyListOf(
                    Vurderingsperiode.Uføre.create(
                        resultat = Resultat.Avslag,
                        grunnlag = Grunnlag.Uføregrunnlag(
                            periode = Periode.create(1.januar(2021), 30.april(2021)),
                            uføregrad = Uføregrad.parse(20),
                            forventetInntekt = 10_000,
                        ),
                        periode = Periode.create(1.januar(2021), 30.april(2021)),
                        begrunnelse = "",
                    ),
                ),
            ),
        )
        vilkårsvurdering.utledOpphørsgrunner() shouldBe listOf(Opphørsgrunn.UFØRHET)
        vilkårsvurdering.tidligsteDatoForAvslag() shouldBe 1.januar(2021)
    }

    @Test
    fun `ikke vurderte vilkår gir ikke opphørtsgrunn`() {
        val vilkårsvurdering = Vilkårsvurderinger(
            uføre = Vilkår.Uførhet.IkkeVurdert,
        )
        vilkårsvurdering.utledOpphørsgrunner() shouldBe emptyList()
    }

    @Test
    fun `uførhet inngangsvilkår blir mappet til riktig avslagsgrunn`() {
        Inngangsvilkår.Uførhet.tilOpphørsgrunn() shouldBe Opphørsgrunn.UFØRHET
    }
}
