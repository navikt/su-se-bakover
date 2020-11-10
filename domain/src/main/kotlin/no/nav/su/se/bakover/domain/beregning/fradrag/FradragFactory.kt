package no.nav.su.se.bakover.domain.beregning.fradrag

import no.nav.su.se.bakover.common.periode.Periode

object FradragFactory {
    fun ny(
        type: Fradragstype,
        beløp: Double,
        periode: Periode,
        utenlandskInntekt: UtenlandskInntekt? = null
    ): Fradrag {
        return PeriodeFradrag(
            periode = periode,
            type = type,
            beløp = beløp,
            utenlandskInntekt = utenlandskInntekt
        )
    }
}
