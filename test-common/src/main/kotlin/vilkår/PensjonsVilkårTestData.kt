package no.nav.su.se.bakover.test.vilkår

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.fixedTidspunkt
import vilkår.common.domain.Vurdering
import vilkår.pensjon.domain.PensjonsVilkår
import vilkår.pensjon.domain.Pensjonsgrunnlag
import vilkår.pensjon.domain.Pensjonsopplysninger
import vilkår.pensjon.domain.VurderingsperiodePensjon
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
                vurdering = Vurdering.Innvilget,
                grunnlag = Pensjonsgrunnlag(
                    id = UUID.randomUUID(),
                    opprettet = opprettet,
                    periode = periode,
                    pensjonsopplysninger = Pensjonsopplysninger(
                        søktPensjonFolketrygd = Pensjonsopplysninger.SøktPensjonFolketrygd(
                            svar = Pensjonsopplysninger.SøktPensjonFolketrygd.Svar.HarSøktPensjonFraFolketrygden,
                        ),
                        søktAndreNorskePensjoner = Pensjonsopplysninger.SøktAndreNorskePensjoner(
                            svar = Pensjonsopplysninger.SøktAndreNorskePensjoner.Svar.IkkeAktuelt,
                        ),
                        søktUtenlandskePensjoner = Pensjonsopplysninger.SøktUtenlandskePensjoner(
                            svar = Pensjonsopplysninger.SøktUtenlandskePensjoner.Svar.HarSøktUtenlandskePensjoner,
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
                vurdering = Vurdering.Avslag,
                grunnlag = Pensjonsgrunnlag(
                    id = UUID.randomUUID(),
                    opprettet = opprettet,
                    periode = periode,
                    pensjonsopplysninger = Pensjonsopplysninger(
                        søktPensjonFolketrygd = Pensjonsopplysninger.SøktPensjonFolketrygd(
                            svar = Pensjonsopplysninger.SøktPensjonFolketrygd.Svar.HarSøktPensjonFraFolketrygden,
                        ),
                        søktAndreNorskePensjoner = Pensjonsopplysninger.SøktAndreNorskePensjoner(
                            svar = Pensjonsopplysninger.SøktAndreNorskePensjoner.Svar.IkkeAktuelt,
                        ),
                        søktUtenlandskePensjoner = Pensjonsopplysninger.SøktUtenlandskePensjoner(
                            svar = Pensjonsopplysninger.SøktUtenlandskePensjoner.Svar.HarIkkeSøktUtenlandskePensjoner,
                        ),
                    ),
                ),
            ),
        ),
    )
}
