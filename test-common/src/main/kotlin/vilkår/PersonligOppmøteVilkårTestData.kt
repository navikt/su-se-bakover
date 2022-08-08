package vilkår

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.grunnlag.PersonligOppmøteGrunnlag
import no.nav.su.se.bakover.domain.grunnlag.PersonligOppmøteÅrsak
import no.nav.su.se.bakover.domain.vilkår.PersonligOppmøteVilkår
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodePersonligOppmøte
import no.nav.su.se.bakover.test.fixedTidspunkt
import java.util.UUID

fun personligOppmøtevilkårInnvilget(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
): PersonligOppmøteVilkår.Vurdert {
    return PersonligOppmøteVilkår.Vurdert(
        vurderingsperioder = nonEmptyListOf(
            VurderingsperiodePersonligOppmøte(
                id = id,
                opprettet = opprettet,
                periode = periode,
                grunnlag = PersonligOppmøteGrunnlag(
                    id = UUID.randomUUID(),
                    opprettet = opprettet,
                    periode = periode,
                    årsak = PersonligOppmøteÅrsak.IkkeMøttMenMidlertidigUnntakFraOppmøteplikt,
                ),
            ),
        ),
    )
}

fun personligOppmøtevilkårAvslag(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
): PersonligOppmøteVilkår.Vurdert {
    return PersonligOppmøteVilkår.Vurdert(
        vurderingsperioder = nonEmptyListOf(
            VurderingsperiodePersonligOppmøte(
                id = id,
                opprettet = opprettet,
                periode = periode,
                grunnlag = PersonligOppmøteGrunnlag(
                    id = UUID.randomUUID(),
                    opprettet = opprettet,
                    periode = periode,
                    årsak = PersonligOppmøteÅrsak.IkkeMøttPersonlig,
                ),
            ),
        ),
    )
}
