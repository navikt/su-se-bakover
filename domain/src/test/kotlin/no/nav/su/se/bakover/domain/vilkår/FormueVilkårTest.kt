package no.nav.su.se.bakover.domain.vilkår

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.vilkår.Vilkår.Formue.Vurdert.Companion.slåSammenVurderingsperiode
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.empty
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Test
import java.util.UUID

internal class FormueVilkårTest {

    @Test
    fun `slår sammen tilstøtende og like formueperioder`() {
        val f1 = lagFormueVurderingsperiode(periodeInnenfor2021 = Periode.create(1.januar(2021), 31.januar(2021)))
        val f2 = lagFormueVurderingsperiode(periodeInnenfor2021 = Periode.create(1.februar(2021), 28.februar(2021)))
        val f3 = lagFormueVurderingsperiode(
            periodeInnenfor2021 = Periode.create(1.mars(2021), 31.mars(2021)), resultat = Resultat.Avslag,
            bosituasjon = Grunnlag.Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen(
                id = UUID.randomUUID(), opprettet = fixedTidspunkt,
                periode = Periode.create(1.mars(2021), 31.mars(2021)),
                begrunnelse = null,
            ),
        )

        val actual = nonEmptyListOf(f1, f2, f3).slåSammenVurderingsperiode()
        actual.size shouldBe 2
        actual.first() shouldBe lagFormueVurderingsperiode(
            id = actual.first().id,
            periodeInnenfor2021 = Periode.create(1.januar(2021), 28.februar(2021)),
            grunnlagsId = actual.first().grunnlag.id,
        )
        actual.last() shouldBe lagFormueVurderingsperiode(
            id = actual.last().id,
            resultat = Resultat.Avslag,
            periodeInnenfor2021 = Periode.create(1.mars(2021), 31.mars(2021)),
            grunnlagsId = actual.last().grunnlag.id,
        )
    }

    @Test
    fun `2 formue-perioder som tilsøtter og er lik`() {
        val f1 = lagFormueVurderingsperiode(periodeInnenfor2021 = Periode.create(1.januar(2021), 31.januar(2021)))
        val f2 = lagFormueVurderingsperiode(periodeInnenfor2021 = Periode.create(1.februar(2021), 28.februar(2021)))

        f1.tilstøterOgErLik(f2) shouldBe true
    }

    @Test
    fun `2 formue-perioder som ikke tilsøtter, men er lik`() {
        val f1 = lagFormueVurderingsperiode(periodeInnenfor2021 = Periode.create(1.januar(2021), 31.januar(2021)))
        val f2 = lagFormueVurderingsperiode(periodeInnenfor2021 = Periode.create(1.mars(2021), 31.mars(2021)))

        f1.tilstøterOgErLik(f2) shouldBe false
    }

    @Test
    fun `2 formue-perioder som tilsøtter, men resultat er ulik`() {
        val f1 = lagFormueVurderingsperiode(periodeInnenfor2021 = Periode.create(1.januar(2021), 31.januar(2021)))
        val f2 = lagFormueVurderingsperiode(
            resultat = Resultat.Avslag,
            periodeInnenfor2021 = Periode.create(1.februar(2021), 28.februar(2021)),
        )

        f1.tilstøterOgErLik(f2) shouldBe false
    }

    @Test
    fun `2 formue-perioder som tilsøtter, men grunnlag er ulik`() {
        val f1 = lagFormueVurderingsperiode(periodeInnenfor2021 = Periode.create(1.januar(2021), 31.januar(2021)))
        val f2 = lagFormueVurderingsperiode(
            periodeInnenfor2021 = Periode.create(1.februar(2021), 28.februar(2021)),
            grunnlag = Formuegrunnlag.create(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = Periode.create(1.februar(2021), 28.februar(2021)),
                epsFormue = null,
                søkersFormue = Formuegrunnlag.Verdier.empty().copy(
                    verdiEiendommer = 100,
                ),
                begrunnelse = null,
                bosituasjon = Grunnlag.Bosituasjon.Fullstendig.Enslig(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    periode = Periode.create(1.februar(2021), 28.februar(2021)),
                    begrunnelse = null,
                ),
                Periode.create(1.januar(2021), 31.desember(2021)),
            ),
        )

        f1.tilstøterOgErLik(f2) shouldBe false
    }

    @Test
    fun `2 formue-perioder som tilsøtter, grunnalgs-begrunnelse som er tom eller null behandles som lik`() {
        val f1 = lagFormueVurderingsperiode(periodeInnenfor2021 = Periode.create(1.januar(2021), 31.januar(2021)))
        val f2 = lagFormueVurderingsperiode(
            periodeInnenfor2021 = Periode.create(1.februar(2021), 28.februar(2021)),
            grunnlag = Formuegrunnlag.create(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = Periode.create(1.februar(2021), 28.februar(2021)),
                epsFormue = null,
                søkersFormue = Formuegrunnlag.Verdier.empty(),
                begrunnelse = "",
                bosituasjon = Grunnlag.Bosituasjon.Fullstendig.Enslig(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    periode = Periode.create(1.februar(2021), 28.februar(2021)),
                    begrunnelse = null,
                ),
                Periode.create(1.januar(2021), 31.desember(2021)),
            ),
        )

        f1.tilstøterOgErLik(f2) shouldBe true
    }

    private fun lagFormueVurderingsperiode(
        id: UUID = UUID.randomUUID(),
        tidspunkt: Tidspunkt = fixedTidspunkt,
        resultat: Resultat = Resultat.Innvilget,
        periodeInnenfor2021: Periode,
        bosituasjon: Grunnlag.Bosituasjon.Fullstendig = Grunnlag.Bosituasjon.Fullstendig.Enslig(
            id = id,
            opprettet = fixedTidspunkt,
            periode = periodeInnenfor2021,
            begrunnelse = null,
        ),
        grunnlagsId: UUID = id,
        grunnlag: Formuegrunnlag = Formuegrunnlag.create(
            id = grunnlagsId,
            periode = periodeInnenfor2021,
            opprettet = fixedTidspunkt,
            epsFormue = null,
            søkersFormue = Formuegrunnlag.Verdier.empty(),
            begrunnelse = null,
            behandlingsPeriode = Periode.create(1.januar(2021), 31.desember(2021)),
            bosituasjon = bosituasjon,
        ),
    ): Vurderingsperiode.Formue {
        return Vurderingsperiode.Formue.create(
            id = id, opprettet = tidspunkt, resultat = resultat,
            grunnlag = grunnlag,
            periode = periodeInnenfor2021,
        )
    }
}
