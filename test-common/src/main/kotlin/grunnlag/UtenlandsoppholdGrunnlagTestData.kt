package no.nav.su.se.bakover.test.grunnlag

import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.utenlandsopphold.domain.vilkår.Utenlandsoppholdgrunnlag
import java.util.UUID

fun nyUtenlandsoppholdgrunnlag(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
): Utenlandsoppholdgrunnlag = Utenlandsoppholdgrunnlag(
    id = id,
    opprettet = opprettet,
    periode = periode,
)
