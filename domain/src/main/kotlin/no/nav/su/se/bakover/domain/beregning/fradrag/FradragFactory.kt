package no.nav.su.se.bakover.domain.beregning.fradrag

import no.nav.su.se.bakover.common.periode.Periode

object FradragFactory {
    fun ny(
        type: Fradragstype,
        månedsbeløp: Double,
        periode: Periode,
        utenlandskInntekt: UtenlandskInntekt? = null,
        tilhører: FradragTilhører
    ): Fradrag {
        return IkkePeriodisertFradrag(
            periode = periode,
            type = type,
            månedsbeløp = månedsbeløp,
            utenlandskInntekt = utenlandskInntekt,
            tilhører = tilhører
        )
    }

    fun periodiser(fradrag: Fradrag): List<Fradrag> =
        fradrag.periode.tilMånedsperioder().map {
            PeriodisertFradrag(
                type = fradrag.fradragstype,
                månedsbeløp = fradrag.månedsbeløp,
                periode = it,
                utenlandskInntekt = fradrag.utenlandskInntekt,
                tilhører = fradrag.tilhører
            )
        }
}
