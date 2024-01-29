package no.nav.su.se.bakover.test.vilkår

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.fixedTidspunkt
import vilkår.opplysningsplikt.domain.OpplysningspliktBeskrivelse
import vilkår.opplysningsplikt.domain.OpplysningspliktVilkår
import vilkår.opplysningsplikt.domain.Opplysningspliktgrunnlag
import vilkår.opplysningsplikt.domain.VurderingsperiodeOpplysningsplikt
import java.util.UUID

fun tilstrekkeligDokumentert(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
): OpplysningspliktVilkår.Vurdert {
    return OpplysningspliktVilkår.Vurdert.createFromVilkårsvurderinger(
        vurderingsperioder = nonEmptyListOf(
            VurderingsperiodeOpplysningsplikt.create(
                id = id,
                opprettet = opprettet,
                periode = periode,
                grunnlag = Opplysningspliktgrunnlag(
                    id = id,
                    opprettet = opprettet,
                    periode = periode,
                    beskrivelse = OpplysningspliktBeskrivelse.TilstrekkeligDokumentasjon,
                ),
            ),
        ),
    )
}

fun utilstrekkeligDokumentert(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
): OpplysningspliktVilkår.Vurdert {
    return OpplysningspliktVilkår.Vurdert.createFromVilkårsvurderinger(
        vurderingsperioder = nonEmptyListOf(
            VurderingsperiodeOpplysningsplikt.create(
                id = id,
                opprettet = opprettet,
                grunnlag = Opplysningspliktgrunnlag(
                    id = id,
                    opprettet = opprettet,
                    periode = periode,
                    beskrivelse = OpplysningspliktBeskrivelse.UtilstrekkeligDokumentasjon,
                ),
                periode = periode,
            ),
        ),
    )
}
