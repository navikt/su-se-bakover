package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon
import java.util.UUID

fun ufullstendigEnslig(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = stønadsperiode2021.periode,
): Bosituasjon.Ufullstendig.HarIkkeEps = Bosituasjon.Ufullstendig.HarIkkeEps(
    id = id,
    opprettet = opprettet,
    periode = periode,
)

fun ufullstendigMedEps(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = stønadsperiode2021.periode,
): Bosituasjon.Ufullstendig.HarEps = Bosituasjon.Ufullstendig.HarEps(
    id = id,
    opprettet = opprettet,
    periode = periode,
    fnr = epsFnr,
)

fun fullstendigUtenEPS(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = stønadsperiode2021.periode,
): Bosituasjon.Fullstendig.Enslig = Bosituasjon.Fullstendig.Enslig(
    id = id,
    opprettet = opprettet,
    periode = periode,
)

fun fullstendigMedEPSUnder67UførFlyktning(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = stønadsperiode2021.periode,
    fnr: Fnr = Fnr.generer(),
): Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning =
    Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
        id = id,
        opprettet = opprettet,
        periode = periode,
        fnr = fnr,
    )

fun fullstendigMedEPSUnder67IkkeUførFlyktning(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = stønadsperiode2021.periode,
): Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning =
    Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning(
        id = id,
        opprettet = opprettet,
        periode = periode,
        fnr = Fnr.generer(),
    )

fun fullstendigMedEPSOver67(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = stønadsperiode2021.periode,
): Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre =
    Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre(
        id = id,
        opprettet = opprettet,
        periode = periode,
        fnr = Fnr.generer(),
    )

fun fullstendigMedVoksne(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = stønadsperiode2021.periode,
): Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen =
    Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen(
        id = id,
        opprettet = opprettet,
        periode = periode,
    )
