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
import no.nav.su.se.bakover.domain.avslåttFormueVilkår
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.fixedTidspunkt
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.innvilgetFormueVilkår
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.test.create
import org.junit.jupiter.api.Test

internal class VilkårsvurderingerTest {

    private val uføregrunnlag = Grunnlag.Uføregrunnlag(
        periode = Periode.create(1.januar(2021), 31.desember(2021)),
        uføregrad = Uføregrad.parse(20),
        forventetInntekt = 10_000,
        opprettet = fixedTidspunkt,
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
                        opprettet = fixedTidspunkt,
                    ),
                ),
            ),
            formue = innvilgetFormueVilkår(vurderingsperiode),
        ).resultat shouldBe Resultat.Innvilget
    }

    @Test
    fun `alle avslag, gir avslag`() {
        Vilkårsvurderinger(
            uføre = Vilkår.Uførhet.Vurdert.create(
                vurderingsperioder = nonEmptyListOf(
                    Vurderingsperiode.Uføre.create(
                        resultat = Resultat.Avslag,
                        grunnlag = uføregrunnlag,
                        periode = Periode.create(1.januar(2021), 31.desember(2021)),
                        begrunnelse = "",
                        opprettet = fixedTidspunkt,
                    ),
                ),
            ),
            formue = avslåttFormueVilkår(Periode.create(1.januar(2021), 31.desember(2021))),
        ).resultat shouldBe Resultat.Avslag
    }

    @Test
    fun `ingen vurderingsperioder gir uavklart vilkår`() {
        Vilkårsvurderinger.IkkeVurdert.let {
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
                            opprettet = fixedTidspunkt,
                        ),
                        periode = Periode.create(1.januar(2021), 30.april(2021)),
                        begrunnelse = "",
                        opprettet = fixedTidspunkt,
                    ),
                    Vurderingsperiode.Uføre.create(
                        resultat = Resultat.Avslag,
                        grunnlag = Grunnlag.Uføregrunnlag(
                            periode = Periode.create(1.mai(2021), 31.desember(2021)),
                            uføregrad = Uføregrad.parse(20),
                            forventetInntekt = 10_000,
                            opprettet = fixedTidspunkt,
                        ),
                        periode = Periode.create(1.mai(2021), 31.desember(2021)),
                        begrunnelse = "",
                        opprettet = fixedTidspunkt,
                    ),
                ),
            ),
            formue = innvilgetFormueVilkår(Periode.create(1.januar(2021), 31.desember(2021))),
        ).let {
            it.resultat shouldBe Resultat.Avslag
            it.tidligsteDatoForAvslag() shouldBe 1.mai(2021)
        }
    }

    @Test
    fun `oppdaterer perioden på grunnlaget riktig`() {
        val gammelPeriode = Periode.create(1.januar(2021), 30.april(2021))
        val nyPeriode = Periode.create(1.februar(2021), 31.mars(2021))

        val vilkårsvurdering = Vilkårsvurderinger(
            uføre = Vilkår.Uførhet.Vurdert.create(
                vurderingsperioder = nonEmptyListOf(
                    Vurderingsperiode.Uføre.create(
                        resultat = Resultat.Avslag,
                        grunnlag = Grunnlag.Uføregrunnlag(
                            periode = gammelPeriode,
                            uføregrad = Uføregrad.parse(20),
                            forventetInntekt = 10_000,
                            opprettet = fixedTidspunkt,
                        ),
                        periode = gammelPeriode,
                        begrunnelse = "",
                        opprettet = fixedTidspunkt,
                    ),
                ),
            ),
            formue = innvilgetFormueVilkår(gammelPeriode),
        )

        val actual = vilkårsvurdering.oppdaterStønadsperiode(Stønadsperiode.create(nyPeriode, "test"))
        actual.uføre.grunnlag.first().periode shouldBe nyPeriode
        (actual.uføre as Vilkår.Uførhet.Vurdert).vurderingsperioder.first().periode shouldBe nyPeriode
        actual.formue.grunnlag.first().periode shouldBe nyPeriode
        (actual.formue as Vilkår.Formue.Vurdert).vurderingsperioder.first().periode shouldBe nyPeriode
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
                            opprettet = fixedTidspunkt,
                        ),
                        periode = Periode.create(1.januar(2021), 30.april(2021)),
                        begrunnelse = "",
                        opprettet = fixedTidspunkt,
                    ),
                ),
            ),
        )
        vilkårsvurdering.utledOpphørsgrunner() shouldBe listOf(Opphørsgrunn.UFØRHET)
        vilkårsvurdering.tidligsteDatoForAvslag() shouldBe 1.januar(2021)
    }

    @Test
    fun `ikke vurderte vilkår gir ikke opphørsgrunn`() {
        val vilkårsvurdering = Vilkårsvurderinger(
            uføre = Vilkår.Uførhet.IkkeVurdert,
            formue = Vilkår.Formue.IkkeVurdert
        )
        vilkårsvurdering.utledOpphørsgrunner() shouldBe emptyList()
    }

    @Test
    fun `uførhet inngangsvilkår blir mappet til riktig avslagsgrunn`() {
        Inngangsvilkår.Uførhet.tilOpphørsgrunn() shouldBe Opphørsgrunn.UFØRHET
    }

    @Test
    fun `formue som er avslag blir utledet`() {
        val periode = Periode.create(1.januar(2021), 31.desember(2021))
        val vilkårsvurdering = Vilkårsvurderinger(
            uføre =
            Vilkår.Uførhet.Vurdert.create(
                nonEmptyListOf(
                    Vurderingsperiode.Uføre.create(
                        resultat = Resultat.Innvilget,
                        grunnlag = Grunnlag.Uføregrunnlag(
                            periode = periode,
                            uføregrad = Uføregrad.parse(20),
                            forventetInntekt = 10_000,
                            opprettet = fixedTidspunkt,
                        ),
                        periode = periode,
                        begrunnelse = "",
                        opprettet = fixedTidspunkt,
                    ),
                ),
            ),
            formue = avslåttFormueVilkår(periode),
        )
        vilkårsvurdering.utledOpphørsgrunner() shouldBe listOf(Opphørsgrunn.FORMUE)
        vilkårsvurdering.tidligsteDatoForAvslag() shouldBe 1.januar(2021)
    }
}
