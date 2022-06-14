package vurderingsperiode

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.grunnlag.PersonligOppmøteGrunnlag
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodePersonligOppmøte
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import java.util.UUID

fun vurderingsperiodePersonligOppmøteInnvilget(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    resultat: Resultat = Resultat.Innvilget,
    grunnlag: PersonligOppmøteGrunnlag? = null,
    vurderingsperiode: Periode = år(2021),
) = VurderingsperiodePersonligOppmøte.tryCreate(
    id = id,
    opprettet = opprettet,
    resultat = resultat,
    grunnlag = grunnlag,
    vurderingsperiode = vurderingsperiode,
).getOrFail()

fun vurderingsperiodePersonligOppmøteAvslag(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    resultat: Resultat = Resultat.Avslag,
    grunnlag: PersonligOppmøteGrunnlag? = null,
    vurderingsperiode: Periode = år(2021),
) = VurderingsperiodePersonligOppmøte.tryCreate(
    id = id,
    opprettet = opprettet,
    resultat = resultat,
    grunnlag = grunnlag,
    vurderingsperiode = vurderingsperiode,
).getOrFail()
