package no.nav.su.se.bakover.test.vurderingsperiode

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.grunnlag.LovligOppholdGrunnlag
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeLovligOpphold
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import java.util.UUID

fun vurderingsperiodeLovligOppholdInnvilget(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    resultat: Resultat = Resultat.Innvilget,
    grunnlag: LovligOppholdGrunnlag? = null,
    vurderingsperiode: Periode = år(2021),
) = VurderingsperiodeLovligOpphold.tryCreate(
    id = id,
    opprettet = opprettet,
    resultat = resultat,
    grunnlag = grunnlag,
    vurderingsperiode = vurderingsperiode,
).getOrFail()

fun vurderingsperiodeLovligOppholdAvslag(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    resultat: Resultat = Resultat.Avslag,
    grunnlag: LovligOppholdGrunnlag? = null,
    vurderingsperiode: Periode = år(2021),
) = VurderingsperiodeLovligOpphold.tryCreate(
    id = id,
    opprettet = opprettet,
    resultat = resultat,
    grunnlag = grunnlag,
    vurderingsperiode = vurderingsperiode,
).getOrFail()
