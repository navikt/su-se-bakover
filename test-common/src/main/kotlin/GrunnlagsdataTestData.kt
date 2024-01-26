package no.nav.su.se.bakover.test

import arrow.core.Nel
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import beregning.domain.fradrag.FradragTilhører
import beregning.domain.fradrag.Fradragstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.grunnlag.Fradragsgrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import vilkår.common.domain.grunnlag.Bosituasjon
import java.util.UUID

fun fradragsgrunnlagArbeidsinntekt1000(
    periode: Periode = år(2021),
    opprettet: Tidspunkt = fixedTidspunkt,
    tilhører: FradragTilhører = FradragTilhører.BRUKER,
): Fradragsgrunnlag {
    return fradragsgrunnlagArbeidsinntekt(periode = periode, arbeidsinntekt = 1000.0, opprettet = opprettet, tilhører = tilhører)
}

/**
 * For bruker.
 */
fun fradragsgrunnlagArbeidsinntekt(
    id: UUID = UUID.randomUUID(),
    periode: Periode = år(2021),
    arbeidsinntekt: Double,
    tilhører: FradragTilhører = FradragTilhører.BRUKER,
    opprettet: Tidspunkt = fixedTidspunkt,
): Fradragsgrunnlag {
    return lagFradragsgrunnlag(
        id = id,
        type = Fradragstype.Arbeidsinntekt,
        månedsbeløp = arbeidsinntekt,
        periode = periode,
        utenlandskInntekt = null,
        tilhører = tilhører,
        opprettet = opprettet,
    )
}

fun bosituasjongrunnlagEnslig(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
): Bosituasjon.Fullstendig.Enslig {
    return Bosituasjon.Fullstendig.Enslig(
        id = id,
        opprettet = opprettet,
        periode = periode,
    )
}

fun bosituasjonBorMedAndreVoksne(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
): Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen {
    return Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen(
        id = id,
        opprettet = opprettet,
        periode = periode,
    )
}

fun bosituasjonEpsOver67(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
): Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre {
    return Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre(
        id = id,
        opprettet = opprettet,
        periode = periode,
        fnr = fnrOver67,
    )
}

fun bosituasjonEpsUnder67(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    fnr: Fnr = fnrUnder67,
): Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning {
    return Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning(
        id = id,
        opprettet = opprettet,
        periode = periode,
        fnr = fnr,
    )
}

fun bosituasjongrunnlagEpsUførFlyktning(
    id: UUID = UUID.randomUUID(),
    periode: Periode = år(2021),
    epsFnr: Fnr = no.nav.su.se.bakover.test.epsFnr,
): Bosituasjon.Fullstendig.EktefellePartnerSamboer {
    return Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
        id = id,
        fnr = epsFnr,
        opprettet = fixedTidspunkt,
        periode = periode,
    )
}

/**
 * Defaults vil kun fungere for søknadsbehandling, siden vi ikke bruker fradragsgrunnlag der enda.
 * @see grunnlagsdataEnsligMedFradrag()
 *
 * - fradragsgrunnlag: emptyList()
 * - Grunnlag.Bosituasjon.Fullstendig.Enslig
 */
fun grunnlagsdataEnsligUtenFradrag(
    periode: Periode = år(2021),
    fradragsgrunnlag: List<Fradragsgrunnlag> = emptyList(),
    bosituasjon: Nel<Bosituasjon.Fullstendig> = nonEmptyListOf(bosituasjongrunnlagEnslig(periode = periode)),
): Grunnlagsdata {
    return Grunnlagsdata.create(fradragsgrunnlag, bosituasjon)
}

/**
 * Defaults:
 * periode: 2021
 * fradragsgrunnlag: 1000 kroner i arbeidsinntekt/mnd for bruker
 * bosituasjon: enslig
 */
fun grunnlagsdataEnsligMedFradrag(
    periode: Periode = år(2021),
    fradragsgrunnlag: NonEmptyList<Fradragsgrunnlag> = nonEmptyListOf(
        fradragsgrunnlagArbeidsinntekt1000(
            periode = periode,
        ),
    ),
    bosituasjon: Nel<Bosituasjon.Fullstendig> = nonEmptyListOf(bosituasjongrunnlagEnslig(periode = periode)),
): Grunnlagsdata {
    return Grunnlagsdata.create(fradragsgrunnlag, bosituasjon)
}

/**
 * Defaults:
 * periode: 2021
 * fradragsgrunnlag: 1000 kroner i arbeidsinntekt/mnd for bruker
 * bosituasjon: eps ufør flyktning
 */
fun grunnlagsdataMedEpsMedFradrag(
    periode: Periode = år(2021),
    epsFnr: Fnr = Fnr.generer(),
    fradragsgrunnlag: NonEmptyList<Fradragsgrunnlag> = nonEmptyListOf(
        fradragsgrunnlagArbeidsinntekt1000(
            periode = periode,
        ),
        fradragsgrunnlagArbeidsinntekt(
            periode = periode,
            arbeidsinntekt = 1000.0,
            tilhører = FradragTilhører.EPS,
        ),
    ),
    bosituasjon: Nel<Bosituasjon.Fullstendig.EktefellePartnerSamboer> = nonEmptyListOf(
        bosituasjongrunnlagEpsUførFlyktning(
            epsFnr = epsFnr,
            periode = periode,
        ),
    ),
): Grunnlagsdata {
    return Grunnlagsdata.create(fradragsgrunnlag, bosituasjon)
}

fun arbeidsinntekt(periode: Periode, tilhører: FradragTilhører): Fradragsgrunnlag {
    return lagFradragsgrunnlag(
        type = Fradragstype.Arbeidsinntekt,
        månedsbeløp = 5000.0,
        periode = periode,
        utenlandskInntekt = null,
        tilhører = tilhører,
    )
}
