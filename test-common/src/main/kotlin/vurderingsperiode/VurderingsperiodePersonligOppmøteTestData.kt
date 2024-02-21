package no.nav.su.se.bakover.test.vurderingsperiode

import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.grunnlag.nyPersonligoppmøteGrunnlag
import vilkår.personligoppmøte.domain.PersonligOppmøteGrunnlag
import vilkår.personligoppmøte.domain.VurderingsperiodePersonligOppmøte
import java.util.UUID

fun nyVurderingsperodePersonligOppmøte(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    grunnlag: PersonligOppmøteGrunnlag = nyPersonligoppmøteGrunnlag(),
) = VurderingsperiodePersonligOppmøte(
    id = id,
    opprettet = opprettet,
    periode = periode,
    grunnlag = grunnlag,
)
