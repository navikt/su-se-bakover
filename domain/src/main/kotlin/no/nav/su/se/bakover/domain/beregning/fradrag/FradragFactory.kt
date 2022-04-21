package no.nav.su.se.bakover.domain.beregning.fradrag

import no.nav.su.se.bakover.common.periode.Periode

object FradragFactory {
    fun ny(
        fradragskategoriWrapper: FradragskategoriWrapper,
        månedsbeløp: Double,
        periode: Periode,
        utenlandskInntekt: UtenlandskInntekt? = null,
        tilhører: FradragTilhører
    ): Fradrag {
        return IkkePeriodisertFradrag(
            periode = periode,
            type = fradragskategoriWrapper,
            månedsbeløp = månedsbeløp,
            utenlandskInntekt = utenlandskInntekt,
            tilhører = tilhører
        )
    }

    fun periodiser(fradrag: Fradrag): List<Fradrag> =
        fradrag.periode.tilMånedsperioder().map {
            FradragForMåned(
                type = fradrag.fradragskategoriWrapper,
                månedsbeløp = fradrag.månedsbeløp,
                måned = it,
                utenlandskInntekt = fradrag.utenlandskInntekt,
                tilhører = fradrag.tilhører
            )
        }
}
