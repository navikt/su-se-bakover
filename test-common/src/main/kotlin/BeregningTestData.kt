package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.BeregningFactory
import no.nav.su.se.bakover.domain.beregning.Sats.Companion.utledSats
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.utledBeregningsstrategi
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import java.util.UUID

val beregningId: UUID = UUID.randomUUID()

/**
 * Defaultverdier:
 * periode: 2021
 * bosituasjon: bosituasjongrunnlagEnslig
 * fradrag: ingen
 */
fun beregning(
    periode: Periode = periode2021,
    bosituasjon: Grunnlag.Bosituasjon.Fullstendig = bosituasjongrunnlagEnslig(periode),
    fradrag: List<Fradrag> = emptyList(),
) = BeregningFactory.ny(
    id = beregningId,
    opprettet = fixedTidspunkt,
    periode = periode,
    sats = bosituasjon.utledSats(),
    fradrag = fradrag,
    fradragStrategy = bosituasjon.utledBeregningsstrategi().fradragStrategy(),
    begrunnelse = "beregning",
)
/*
  beregning = BeregningMedFradragBeregnetMånedsvis(
        id = UUID.fromString(beregningId),
        opprettet = fixedTidspunkt,
        sats = Sats.ORDINÆR,
        månedsberegninger = listOf(
            PersistertMånedsberegning(
                periode = periode,
                sats = Sats.ORDINÆR,
                fradrag = fradrag,
                sumYtelse = 3,
                sumFradrag = 1.2,
                benyttetGrunnbeløp = 66,
                satsbeløp = 4.1,
                fribeløpForEps = 0.0,
            ),
        ),
        fradrag = fradrag,
        sumYtelse = 3,
        sumFradrag = 2.1,
        periode = periode,
        fradragStrategyName = FradragStrategyName.Enslig,
        begrunnelse = "har en begrunnelse for beregning",
    ),
 */
