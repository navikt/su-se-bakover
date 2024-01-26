package vilkår

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.fixedTidspunkt
import vilkår.personligoppmøte.domain.PersonligOppmøteGrunnlag
import vilkår.personligoppmøte.domain.PersonligOppmøteVilkår
import vilkår.personligoppmøte.domain.PersonligOppmøteÅrsak
import vilkår.personligoppmøte.domain.VurderingsperiodePersonligOppmøte
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
