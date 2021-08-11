package no.nav.su.se.bakover.domain.vilkår

import arrow.core.left
import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.fixedTidspunkt
import org.junit.jupiter.api.Test

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
}
