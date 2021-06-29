package no.nav.su.se.bakover.test

import arrow.core.Nel
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import java.util.UUID

val fradragsgrunnlagId: UUID = UUID.randomUUID()

/**
 * 1000 per måned / 12000 per år
 */
fun fradragsgrunnlagForventetInntekt1000(
    periode: Periode = periode2021,
    fradrag: Fradrag = FradragFactory.ny(
        type = Fradragstype.ForventetInntekt,
        månedsbeløp = 1000.0,
        periode = periode,
        utenlandskInntekt = null,
        tilhører = FradragTilhører.BRUKER,
    ),
): Grunnlag.Fradragsgrunnlag {
    return Grunnlag.Fradragsgrunnlag(
        id = fradragsgrunnlagId,
        opprettet = fixedTidspunkt,
        fradrag = fradrag,
    )
}

fun fradragsgrunnlagArbeidsinntekt1000(
    periode: Periode = periode2021,
    fradrag: Fradrag = FradragFactory.ny(
        type = Fradragstype.Arbeidsinntekt,
        månedsbeløp = 1000.0,
        periode = periode,
        utenlandskInntekt = null,
        tilhører = FradragTilhører.BRUKER,
    ),
): Grunnlag.Fradragsgrunnlag {
    return Grunnlag.Fradragsgrunnlag(
        id = fradragsgrunnlagId,
        opprettet = fixedTidspunkt,
        fradrag = fradrag,
    )
}

val bosituasjonId: UUID = UUID.randomUUID()

fun bosituasjongrunnlagEnslig(
    periode: Periode = periode2021,
): Grunnlag.Bosituasjon.Fullstendig.Enslig {
    return Grunnlag.Bosituasjon.Fullstendig.Enslig(
        id = bosituasjonId,
        opprettet = fixedTidspunkt,
        periode = periode,
        begrunnelse = "bosituasjongrunnlagEnslig",
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
    periode: Periode = periode2021,
    fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag> = emptyList(),
    bosituasjon: Nel<Grunnlag.Bosituasjon> = nonEmptyListOf(bosituasjongrunnlagEnslig(periode)),
): Grunnlagsdata {
    return Grunnlagsdata(
        fradragsgrunnlag = fradragsgrunnlag,
        bosituasjon = bosituasjon,
    )
}

/**
 * Defaults:
 * periode: 2021
 * fradragsgrunnlag: 1000 kroner i arbeidsinntekt/mnd for bruker
 * bosituasjon: enslig
 */
fun grunnlagsdataEnsligMedFradrag(
    periode: Periode = periode2021,
    fradragsgrunnlag: NonEmptyList<Grunnlag.Fradragsgrunnlag> = nonEmptyListOf(fradragsgrunnlagArbeidsinntekt1000(periode = periode)),
    bosituasjon: Nel<Grunnlag.Bosituasjon> = nonEmptyListOf(bosituasjongrunnlagEnslig(periode)),
): Grunnlagsdata {
    return Grunnlagsdata(
        fradragsgrunnlag = fradragsgrunnlag,
        bosituasjon = bosituasjon,
    )
}
