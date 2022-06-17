package no.nav.su.se.bakover.test.vilkår

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.vilkår.PensjonsVilkår
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodePensjon
import no.nav.su.se.bakover.test.fixedTidspunkt
import java.util.UUID

fun pensjonsVilkårInnvilget(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
): PensjonsVilkår.Vurdert {
    return PensjonsVilkår.Vurdert.createFromVilkårsvurderinger(
        vurderingsperioder = nonEmptyListOf(
            VurderingsperiodePensjon.create(
                id = id,
                opprettet = opprettet,
                resultat = Resultat.Innvilget,
                grunnlag = null,
                periode = periode,
            ),
        ),
    )
}

fun pensjonsVilkårAvslag(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
): PensjonsVilkår.Vurdert {
    return PensjonsVilkår.Vurdert.createFromVilkårsvurderinger(
        vurderingsperioder = nonEmptyListOf(
            VurderingsperiodePensjon.create(
                id = id,
                opprettet = opprettet,
                resultat = Resultat.Avslag,
                grunnlag = null,
                periode = periode,
            ),
        ),
    )
}
