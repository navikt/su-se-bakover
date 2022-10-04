package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import java.util.UUID

fun ufullstendigEnslig(periode: Periode): Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEps {
    return Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEps(
        id = UUID.randomUUID(),
        opprettet = fixedTidspunkt,
        periode = periode,
    )
}

fun fullstendigUtenEPS(periode: Periode): Grunnlag.Bosituasjon.Fullstendig {
    return Grunnlag.Bosituasjon.Fullstendig.Enslig(
        id = UUID.randomUUID(),
        opprettet = fixedTidspunkt,
        periode = periode,
    )
}

fun fullstendigMedEPS(periode: Periode): Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer {
    return Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
        id = UUID.randomUUID(),
        opprettet = fixedTidspunkt,
        periode = periode,
        fnr = Fnr.generer(),
    )
}
