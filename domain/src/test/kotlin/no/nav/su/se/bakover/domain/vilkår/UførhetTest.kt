package no.nav.su.se.bakover.domain.vilkår

import arrow.core.Nel
import io.kotest.assertions.arrow.either.shouldBeLeft
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import org.junit.jupiter.api.Test

internal class UførhetTest {
    @Test
    fun `validerer at vurderingsperioder ikke overlapper`() {
        Vilkår.Vurdert.Uførhet.tryCreate(
            vurderingsperioder = Nel.of(
                Vurderingsperiode.Manuell.create(
                    resultat = Resultat.Innvilget,
                    grunnlag = null,
                    periode = Periode.create(1.januar(2021), 31.desember(2021)),
                    begrunnelse = "",
                ),
                Vurderingsperiode.Manuell.create(
                    resultat = Resultat.Avslag,
                    grunnlag = null,
                    periode = Periode.create(1.januar(2021), 31.desember(2021)),
                    begrunnelse = "",
                ),
            ),
        ) shouldBeLeft Vilkår.Vurdert.Uførhet.UgyldigUførevilkår.OverlappendeVurderingsperioder
    }
}
