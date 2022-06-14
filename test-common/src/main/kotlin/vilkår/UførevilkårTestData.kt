package no.nav.su.se.bakover.test.vilkårsvurderinger

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.grunnlag.uføregrunnlag
import no.nav.su.se.bakover.test.grunnlag.uføregrunnlagForventetInntekt0
import no.nav.su.se.bakover.test.grunnlag.uføregrunnlagForventetInntekt12000
import java.util.UUID

fun innvilgetUførevilkårForventetInntekt0(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    uføregrunnlag: Grunnlag.Uføregrunnlag = uføregrunnlagForventetInntekt0(
        id = UUID.randomUUID(),
        opprettet = opprettet,
        periode = periode,
    ),
): Vilkår.Uførhet.Vurdert {
    return Vilkår.Uførhet.Vurdert.create(
        vurderingsperioder = nonEmptyListOf(
            Vurderingsperiode.Uføre.create(
                id = id,
                opprettet = opprettet,
                resultat = Resultat.Innvilget,
                grunnlag = uføregrunnlag,
                periode = periode,
            ),
        ),
    )
}

fun innvilgetUførevilkårForventetInntekt12000(
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
): Vilkår.Uførhet.Vurdert {
    return Vilkår.Uførhet.Vurdert.create(
        vurderingsperioder = nonEmptyListOf(
            Vurderingsperiode.Uføre.create(
                id = UUID.randomUUID(),
                opprettet = opprettet,
                resultat = Resultat.Innvilget,
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
): Vilkår.Uførhet.Vurdert {
    return Vilkår.Uførhet.Vurdert.create(
        vurderingsperioder = nonEmptyListOf(
            Vurderingsperiode.Uføre.create(
                id = vurderingsperiodeId,
                opprettet = opprettet,
                resultat = Resultat.Innvilget,
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
): Vilkår.Uførhet.Vurdert {
    return Vilkår.Uførhet.Vurdert.create(
        vurderingsperioder = nonEmptyListOf(
            Vurderingsperiode.Uføre.create(
                id = UUID.randomUUID(),
                opprettet = opprettet,
                resultat = Resultat.Avslag,
                grunnlag = null,
                periode = periode,
            ),
        ),
    )
}
