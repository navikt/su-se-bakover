package no.nav.su.se.bakover.test.vilkår

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.grunnlag.Pensjonsgrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Pensjonsopplysninger
import no.nav.su.se.bakover.domain.vilkår.PensjonsVilkår
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
                periode = periode,
                grunnlag = Pensjonsgrunnlag(
                    id = UUID.randomUUID(),
                    opprettet = opprettet,
                    periode = periode,
                    pensjonsopplysninger = Pensjonsopplysninger(
                        folketrygd = Pensjonsopplysninger.Folketrygd(
                            svar = Pensjonsopplysninger.Svar.Ja,
                        ),
                        andreNorske = Pensjonsopplysninger.AndreNorske(
                            svar = Pensjonsopplysninger.Svar.IkkeAktuelt,
                        ),
                        utenlandske = Pensjonsopplysninger.Utenlandske(
                            svar = Pensjonsopplysninger.Svar.Ja,
                        ),
                    ),
                ),
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
                periode = periode,
                grunnlag = Pensjonsgrunnlag(
                    id = UUID.randomUUID(),
                    opprettet = opprettet,
                    periode = periode,
                    pensjonsopplysninger = Pensjonsopplysninger(
                        folketrygd = Pensjonsopplysninger.Folketrygd(
                            svar = Pensjonsopplysninger.Svar.Ja,
                        ),
                        andreNorske = Pensjonsopplysninger.AndreNorske(
                            svar = Pensjonsopplysninger.Svar.IkkeAktuelt,
                        ),
                        utenlandske = Pensjonsopplysninger.Utenlandske(
                            svar = Pensjonsopplysninger.Svar.Nei,
                        ),
                    ),
                ),
            ),
        ),
    )
}
