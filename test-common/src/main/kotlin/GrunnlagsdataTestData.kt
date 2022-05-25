package no.nav.su.se.bakover.test

import arrow.core.Nel
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import java.util.UUID

fun fradragsgrunnlagArbeidsinntekt1000(
    periode: Periode = år(2021),
): Grunnlag.Fradragsgrunnlag {
    return fradragsgrunnlagArbeidsinntekt(periode = periode, arbeidsinntekt = 1000.0)
}

/**
 * For bruker.
 */
fun fradragsgrunnlagArbeidsinntekt(
    id: UUID = UUID.randomUUID(),
    periode: Periode = år(2021),
    arbeidsinntekt: Double,
    tilhører: FradragTilhører = FradragTilhører.BRUKER,
): Grunnlag.Fradragsgrunnlag {
    return lagFradragsgrunnlag(
        id = id,
        type = Fradragstype.Arbeidsinntekt,
        månedsbeløp = arbeidsinntekt,
        periode = periode,
        utenlandskInntekt = null,
        tilhører = tilhører,
    )
}

fun bosituasjongrunnlagEnslig(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
): Grunnlag.Bosituasjon.Fullstendig.Enslig {
    return Grunnlag.Bosituasjon.Fullstendig.Enslig(
        id = id,
        opprettet = opprettet,
        periode = periode,
    )
}

fun bosituasjongrunnlagEpsUførFlyktning(
    id: UUID = UUID.randomUUID(),
    periode: Periode = år(2021),
    epsFnr: Fnr = no.nav.su.se.bakover.test.epsFnr,
): Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer {
    return Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
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
    fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag> = emptyList(),
    bosituasjon: Nel<Grunnlag.Bosituasjon> = nonEmptyListOf(bosituasjongrunnlagEnslig(periode = periode)),
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
    fradragsgrunnlag: NonEmptyList<Grunnlag.Fradragsgrunnlag> = nonEmptyListOf(
        fradragsgrunnlagArbeidsinntekt1000(
            periode = periode,
        ),
    ),
    bosituasjon: Nel<Grunnlag.Bosituasjon> = nonEmptyListOf(bosituasjongrunnlagEnslig(periode = periode)),
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
    fradragsgrunnlag: NonEmptyList<Grunnlag.Fradragsgrunnlag> = nonEmptyListOf(
        fradragsgrunnlagArbeidsinntekt1000(
            periode = periode,
        ),
    ),
    bosituasjon: Nel<Grunnlag.Bosituasjon> = nonEmptyListOf(
        bosituasjongrunnlagEpsUførFlyktning(
            epsFnr = epsFnr,
            periode = periode,
        ),
    ),
): Grunnlagsdata {
    return Grunnlagsdata.create(fradragsgrunnlag, bosituasjon)
}

fun arbeidsinntekt(periode: Periode, tilhører: FradragTilhører): Grunnlag.Fradragsgrunnlag {
    return lagFradragsgrunnlag(
        type = Fradragstype.Arbeidsinntekt,
        månedsbeløp = 5000.0,
        periode = periode,
        utenlandskInntekt = null,
        tilhører = tilhører,
    )
}
