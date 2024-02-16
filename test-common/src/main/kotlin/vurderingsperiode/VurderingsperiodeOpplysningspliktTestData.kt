package no.nav.su.se.bakover.test.vurderingsperiode

import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.grunnlag.nyOpplysningspliktGrunnlag
import vilkår.opplysningsplikt.domain.Opplysningspliktgrunnlag
import vilkår.opplysningsplikt.domain.VurderingsperiodeOpplysningsplikt
import java.util.UUID

fun nyVurderingsperiodeOpplysningsplikt(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    grunnlag: Opplysningspliktgrunnlag = nyOpplysningspliktGrunnlag(),
): VurderingsperiodeOpplysningsplikt = VurderingsperiodeOpplysningsplikt.create(
    id = id,
    opprettet = opprettet,
    periode = periode,
    grunnlag = grunnlag,
)
