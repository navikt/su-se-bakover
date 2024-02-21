package no.nav.su.se.bakover.test.grunnlag

import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.stønadsperiode2021
import vilkår.fastopphold.domain.FastOppholdINorgeGrunnlag
import java.util.UUID

fun nyFastOppholdINorgeGrunnlag(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = stønadsperiode2021.periode,
): FastOppholdINorgeGrunnlag = FastOppholdINorgeGrunnlag(
    id = id,
    opprettet = opprettet,
    periode = periode,
)
