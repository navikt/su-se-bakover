package no.nav.su.se.bakover.test.grunnlag

import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.fixedTidspunkt
import vilkår.lovligopphold.domain.LovligOppholdGrunnlag
import java.util.UUID

fun nyLovligoppholGrunnlag(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
) = LovligOppholdGrunnlag(id = id, opprettet = opprettet, periode = periode)
