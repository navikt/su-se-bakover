package no.nav.su.se.bakover.domain.beregning.fradrag

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.Fradragstype
import no.nav.su.se.bakover.domain.beregning.UtenlandskInntekt
import java.util.UUID

object FradragFactory {
    fun domene(
        type: Fradragstype,
        beløp: Double,
        periode: Periode,
        utenlandskInntekt: UtenlandskInntekt? = null
    ): IFradrag {
        return Fradrag(
            periode = periode,
            type = type,
            beløp = beløp,
            utenlandskInntekt = utenlandskInntekt
        )
    }

    fun db(
        id: UUID,
        opprettet: Tidspunkt,
        type: Fradragstype,
        beløp: Double,
        periode: Periode,
        utenlandskInntekt: UtenlandskInntekt? = null
    ): IFradrag = FradragDbWrapper(
        id = id,
        tidspunkt = opprettet,
        fradrag = domene(
            periode = periode,
            beløp = beløp,
            type = type,
            utenlandskInntekt = utenlandskInntekt
        )
    )
}
