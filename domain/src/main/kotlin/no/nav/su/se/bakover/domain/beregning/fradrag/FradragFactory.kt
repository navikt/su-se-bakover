package no.nav.su.se.bakover.domain.beregning.fradrag

import no.nav.su.se.bakover.common.periode.Periode

object FradragFactory {
    fun ny(
        type: Fradragstype,
        beløp: Double,
        periode: Periode,
        utenlandskInntekt: UtenlandskInntekt? = null,
        tilhører: FradragTilhører
    ): Fradrag {
        return PeriodeFradrag(
            periode = periode,
            type = type,
            beløp = beløp,
            utenlandskInntekt = utenlandskInntekt,
            tilhører = tilhører
        )
    }

    fun periodiser(fradrag: Fradrag): List<Fradrag> =
        fradrag.getPeriode().tilMånedsperioder().map {
            PeriodisertFradrag(
                type = fradrag.getFradragstype(),
                beløp = fradrag.getTotaltFradrag() / fradrag.getPeriode().getAntallMåneder(),
                periode = it,
                utenlandskInntekt = fradrag.getUtenlandskInntekt(),
                tilhører = fradrag.getTilhører()
            )
        }
}
