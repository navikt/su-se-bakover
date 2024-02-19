package no.nav.su.se.bakover.test.grunnlag

import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.fixedTidspunkt
import vilkår.personligoppmøte.domain.PersonligOppmøteGrunnlag
import vilkår.personligoppmøte.domain.PersonligOppmøteÅrsak
import java.util.UUID

fun nyPersonligoppmøteGrunnlag(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    årsak: PersonligOppmøteÅrsak = PersonligOppmøteÅrsak.MøttPersonlig,
) = PersonligOppmøteGrunnlag(
    id = id,
    opprettet = opprettet,
    periode = periode,
    årsak = årsak,
)
