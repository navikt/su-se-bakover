package vurderingsperiode

import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeFamiliegjenforening
import no.nav.su.se.bakover.test.fixedTidspunkt
import vilkår.common.domain.Vurdering
import vilkår.common.domain.grunnlag.Grunnlag
import java.util.UUID

fun vurderingsperiodeFamiliegjenforeningInnvilget(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    vurdering: Vurdering = Vurdering.Innvilget,
    grunnlag: Grunnlag? = null,
    periode: Periode = år(2021),
) = VurderingsperiodeFamiliegjenforening.create(
    id = id,
    opprettet = opprettet,
    vurdering = vurdering,
    grunnlag = grunnlag,
    periode = periode,
)

fun vurderingsperiodeFamiliegjenforeningAvslag(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    vurdering: Vurdering = Vurdering.Avslag,
    grunnlag: Grunnlag? = null,
    periode: Periode = år(2021),
) = VurderingsperiodeFamiliegjenforening.create(
    id = id,
    opprettet = opprettet,
    vurdering = vurdering,
    grunnlag = grunnlag,
    periode = periode,
)
