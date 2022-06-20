package no.nav.su.se.bakover.test.vurderingsperiode

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.grunnlag.FastOppholdINorgeGrunnlag
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeFastOppholdINorge
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import java.util.UUID

fun vurderingsperiodeFastOppholdInnvilget(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    resultat: Resultat = Resultat.Innvilget,
    grunnlag: FastOppholdINorgeGrunnlag? = null,
    vurderingsperiode: Periode = år(2021),
) = VurderingsperiodeFastOppholdINorge.tryCreate(
    id = id,
    opprettet = opprettet,
    resultat = resultat,
    grunnlag = grunnlag,
    vurderingsperiode = vurderingsperiode,
).getOrFail()

fun vurderingsperiodeFastOppholdAvslag(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    resultat: Resultat = Resultat.Avslag,
    grunnlag: FastOppholdINorgeGrunnlag? = null,
    vurderingsperiode: Periode = år(2021),
) = VurderingsperiodeFastOppholdINorge.tryCreate(
    id = id,
    opprettet = opprettet,
    resultat = resultat,
    grunnlag = grunnlag,
    vurderingsperiode = vurderingsperiode,
).getOrFail()
