package no.nav.su.se.bakover.domain.vilkår

import arrow.core.left
import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.fixedTidspunkt
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.vilkår.Vilkår.Uførhet.Vurdert.Companion.slåSammenVurderingsperiode
import org.junit.jupiter.api.Test
import java.util.UUID

internal class UførhetTest {
    @Test
    fun `validerer at vurderingsperioder ikke overlapper`() {
        Vilkår.Uførhet.Vurdert.tryCreate(
            vurderingsperioder = nonEmptyListOf(
                Vurderingsperiode.Uføre.create(
                    resultat = Resultat.Innvilget,
                    opprettet = fixedTidspunkt,
                    grunnlag = null,
                    periode = Periode.create(1.januar(2021), 31.desember(2021)),
                    begrunnelse = "",
                ),
                Vurderingsperiode.Uføre.create(
                    resultat = Resultat.Avslag,
                    opprettet = fixedTidspunkt,
                    grunnlag = null,
                    periode = Periode.create(1.januar(2021), 31.desember(2021)),
                    begrunnelse = "",
                ),
            ),
        ) shouldBe Vilkår.Uførhet.Vurdert.UgyldigUførevilkår.OverlappendeVurderingsperioder.left()
    }

    @Test
    fun `slår sammen tilstøtende og like vurderingsperioder`() {
        val v1 = lagUføreVurderingsperiode(periode = Periode.create(1.januar(2021), 31.januar(2021)))
        val v2 = lagUføreVurderingsperiode(periode = Periode.create(1.februar(2021), 28.februar(2021)))
        val v3 = lagUføreVurderingsperiode(resultat = Resultat.Avslag, periode = Periode.create(1.mars(2021), 31.mars(2021)))

        val actual = nonEmptyListOf(v1, v2, v3).slåSammenVurderingsperiode()
        actual.size shouldBe 2
        actual.first() shouldBe Vurderingsperiode.Uføre.create(
            id = actual.first().id,
            opprettet = fixedTidspunkt,
            resultat = Resultat.Innvilget,
            grunnlag = null,
            periode = Periode.create(1.januar(2021), 28.februar(2021)),
            begrunnelse = null,
        )
        actual.last() shouldBe Vurderingsperiode.Uføre.create(
            id = actual.last().id,
            opprettet = fixedTidspunkt,
            resultat = Resultat.Avslag,
            grunnlag = null,
            periode = Periode.create(1.mars(2021), 31.mars(2021)),
            begrunnelse = null,
        )
    }

    @Test
    fun `2 uføre-perioder som tilstøter og er lik`() {
        val v1 = lagUføreVurderingsperiode(periode = Periode.create(1.januar(2021), 31.januar(2021)))
        val v2 = lagUføreVurderingsperiode(periode = Periode.create(1.februar(2021), 28.februar(2021)))

        v1.tilstøterOgErLik(v2)
    }

    @Test
    fun `2 uføre-perioder som ikke tilstøter, men er lik`() {
        val v1 = lagUføreVurderingsperiode(periode = Periode.create(1.januar(2021), 31.januar(2021)))
        val v2 = lagUføreVurderingsperiode(periode = Periode.create(1.mars(2021), 31.mars(2021)))

        v1.tilstøterOgErLik(v2) shouldBe true
    }

    @Test
    fun `2 uføre-perioder som tilstøter, men grunnlag er ulik`() {
        val v1 = lagUføreVurderingsperiode(periode = Periode.create(1.januar(2021), 31.januar(2021)))
        val v2 = lagUføreVurderingsperiode(
            periode = Periode.create(1.februar(2021), 28.februar(2021)),
            grunnlag = Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = Periode.create(1.februar(2021), 28.februar(2021)),
                uføregrad = Uføregrad.parse(1),
                forventetInntekt = 0,
            )
        )

        v1.tilstøterOgErLik(v2) shouldBe false
    }

    private fun lagUføreVurderingsperiode(
        periode: Periode,
        tidspunkt: Tidspunkt = fixedTidspunkt,
        resultat: Resultat = Resultat.Innvilget,
        grunnlag: Grunnlag.Uføregrunnlag? = null,
        begrunnelse: String? = null,
    ): Vurderingsperiode.Uføre {
        return Vurderingsperiode.Uføre.create(
            opprettet = tidspunkt,
            resultat = resultat,
            grunnlag = grunnlag,
            periode = periode,
            begrunnelse = begrunnelse,
        )
    }
}
