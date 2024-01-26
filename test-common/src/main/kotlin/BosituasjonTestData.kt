package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Periode
import vilkår.common.domain.grunnlag.Bosituasjon
import java.util.UUID

fun ufullstendigEnslig(periode: Periode): Bosituasjon.Ufullstendig.HarIkkeEps {
    return Bosituasjon.Ufullstendig.HarIkkeEps(
        id = UUID.randomUUID(),
        opprettet = fixedTidspunkt,
        periode = periode,
    )
}

fun fullstendigUtenEPS(periode: Periode): Bosituasjon.Fullstendig {
    return Bosituasjon.Fullstendig.Enslig(
        id = UUID.randomUUID(),
        opprettet = fixedTidspunkt,
        periode = periode,
    )
}

fun fullstendigMedEPS(periode: Periode): Bosituasjon.Fullstendig.EktefellePartnerSamboer {
    return Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
        id = UUID.randomUUID(),
        opprettet = fixedTidspunkt,
        periode = periode,
        fnr = Fnr.generer(),
    )
}
