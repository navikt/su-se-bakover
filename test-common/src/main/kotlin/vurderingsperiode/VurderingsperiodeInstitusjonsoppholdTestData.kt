package no.nav.su.se.bakover.test.vurderingsperiode

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.grunnlag.InstitusjonsoppholdGrunnlag
import no.nav.su.se.bakover.domain.vilkår.Vurdering
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeInstitusjonsopphold
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import java.util.UUID

fun vurderingsperiodeInstitusjonsoppholdInnvilget(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    vurdering: Vurdering = Vurdering.Innvilget,
    grunnlag: InstitusjonsoppholdGrunnlag? = null,
    vurderingsperiode: Periode = år(2021),
) = VurderingsperiodeInstitusjonsopphold.tryCreate(
    id = id,
    opprettet = opprettet,
    vurdering = vurdering,
    grunnlag = grunnlag,
    vurderingsperiode = vurderingsperiode,
).getOrFail()

fun vurderingsperiodeInstitusjonsoppholdAvslag(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    vurdering: Vurdering = Vurdering.Avslag,
    grunnlag: InstitusjonsoppholdGrunnlag? = null,
    vurderingsperiode: Periode = år(2021),
) = VurderingsperiodeInstitusjonsopphold.tryCreate(
    id = id,
    opprettet = opprettet,
    vurdering = vurdering,
    grunnlag = grunnlag,
    vurderingsperiode = vurderingsperiode,
).getOrFail()
