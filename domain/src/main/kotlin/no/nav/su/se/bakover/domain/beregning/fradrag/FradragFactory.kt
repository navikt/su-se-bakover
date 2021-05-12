package no.nav.su.se.bakover.domain.beregning.fradrag

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode

object FradragFactory {
    fun ny(
        opprettet: Tidspunkt,
        type: Fradragstype,
        månedsbeløp: Double,
        periode: Periode,
        utenlandskInntekt: UtenlandskInntekt? = null,
        tilhører: FradragTilhører,
    ): Fradrag {
        return IkkePeriodisertFradrag(
            opprettet = opprettet,
            periode = periode,
            fradragstype = type,
            månedsbeløp = månedsbeløp,
            utenlandskInntekt = utenlandskInntekt,
            tilhører = tilhører,
        )
    }

    fun periodiser(fradrag: Fradrag): List<Fradrag> =
        fradrag.periode.tilMånedsperioder().map {
            PeriodisertFradrag(
                opprettet = fradrag.opprettet,
                fradragstype = fradrag.fradragstype,
                månedsbeløp = fradrag.månedsbeløp,
                periode = it,
                utenlandskInntekt = fradrag.utenlandskInntekt,
                tilhører = fradrag.tilhører,
            )
        }
}
