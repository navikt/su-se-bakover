package no.nav.su.se.bakover.test.vilkår

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.grunnlag.OpplysningspliktBeskrivelse
import no.nav.su.se.bakover.domain.grunnlag.Opplysningspliktgrunnlag
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeOpplysningsplikt
import no.nav.su.se.bakover.test.fixedTidspunkt
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
