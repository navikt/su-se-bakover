package no.nav.su.se.bakover.domain.beregning.fradrag

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import java.util.UUID

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

    fun persistert(
        id: UUID,
        opprettet: Tidspunkt,
        type: Fradragstype,
        beløp: Double,
        periode: Periode,
        utenlandskInntekt: UtenlandskInntekt? = null
    ): Fradrag = PersistertFradrag(
        id = id,
        tidspunkt = opprettet,
        fradrag = ny(
            periode = periode,
            beløp = beløp,
            type = type,
            utenlandskInntekt = utenlandskInntekt
        )
    )
}
