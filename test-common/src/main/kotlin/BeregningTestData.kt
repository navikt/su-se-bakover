package no.nav.su.se.bakover.test

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.BeregningFactory
import no.nav.su.se.bakover.domain.beregning.Sats.Companion.utledSats
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.utledBeregningsstrategi
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import java.util.UUID

val beregningId: UUID = UUID.randomUUID()

/**
 * Defaultverdier:
 * periode: 2021
 * bosituasjon: bosituasjongrunnlagEnslig (høy sats)
 * fradrag: forventet inntekt for bruker 0
 */
fun beregning(
    periode: Periode = periode2021,
    bosituasjon: Grunnlag.Bosituasjon.Fullstendig = bosituasjongrunnlagEnslig(periode),
    fradrag: NonEmptyList<Fradrag> = nonEmptyListOf(
        FradragFactory.ny(
            type = Fradragstype.ForventetInntekt,
            månedsbeløp = 0.0,
            periode = periode,
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
        ),
    ),
) = BeregningFactory.ny(
    id = beregningId,
    opprettet = fixedTidspunkt,
    periode = periode,
    sats = bosituasjon.utledSats(),
    fradrag = fradrag,
    fradragStrategy = bosituasjon.utledBeregningsstrategi().fradragStrategy(),
    begrunnelse = "beregning",
)
