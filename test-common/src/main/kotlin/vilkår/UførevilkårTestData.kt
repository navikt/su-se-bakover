package no.nav.su.se.bakover.test.vilkårsvurderinger

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.grunnlag.uføregrunnlag
import no.nav.su.se.bakover.test.grunnlag.uføregrunnlagForventetInntekt0
import no.nav.su.se.bakover.test.grunnlag.uføregrunnlagForventetInntekt12000
import vilkår.domain.Vurdering
import vilkår.uføre.domain.UføreVilkår
import vilkår.uføre.domain.Uføregrad
import vilkår.uføre.domain.Uføregrunnlag
import vilkår.uføre.domain.VurderingsperiodeUføre
import java.util.UUID

fun innvilgetUførevilkårForventetInntekt0(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    uføregrunnlag: Uføregrunnlag = uføregrunnlagForventetInntekt0(
        id = UUID.randomUUID(),
        opprettet = opprettet,
        periode = periode,
    ),
): UføreVilkår.Vurdert {
    return UføreVilkår.Vurdert.create(
        vurderingsperioder = nonEmptyListOf(
            VurderingsperiodeUføre.create(
                id = id,
                opprettet = opprettet,
                vurdering = Vurdering.Innvilget,
                grunnlag = uføregrunnlag,
                periode = periode,
            ),
        ),
    )
}

fun innvilgetUførevilkårForventetInntekt12000(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
): UføreVilkår.Vurdert {
    return UføreVilkår.Vurdert.create(
        vurderingsperioder = nonEmptyListOf(
            VurderingsperiodeUføre.create(
                id = UUID.randomUUID(),
                opprettet = opprettet,
                vurdering = Vurdering.Innvilget,
                grunnlag = uføregrunnlagForventetInntekt12000(opprettet = opprettet, periode = periode),
                periode = periode,
            ),
        ),
    )
}

fun innvilgetUførevilkår(
    vurderingsperiodeId: UUID = UUID.randomUUID(),
    grunnlagsId: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    forventetInntekt: Int = 0,
    uføregrad: Uføregrad = Uføregrad.parse(100),
): UføreVilkår.Vurdert {
    return UføreVilkår.Vurdert.create(
        vurderingsperioder = nonEmptyListOf(
            VurderingsperiodeUføre.create(
                id = vurderingsperiodeId,
                opprettet = opprettet,
                vurdering = Vurdering.Innvilget,
                grunnlag = uføregrunnlag(
                    id = grunnlagsId,
                    opprettet = opprettet,
                    periode = periode,
                    forventetInntekt = forventetInntekt,
                    uføregrad = uføregrad,
                ),
                periode = periode,
            ),
        ),
    )
}

fun avslåttUførevilkårUtenGrunnlag(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
): UføreVilkår.Vurdert {
    return UføreVilkår.Vurdert.create(
        vurderingsperioder = nonEmptyListOf(
            VurderingsperiodeUføre.create(
                id = UUID.randomUUID(),
                opprettet = opprettet,
                vurdering = Vurdering.Avslag,
                grunnlag = null,
                periode = periode,
            ),
        ),
    )
}
