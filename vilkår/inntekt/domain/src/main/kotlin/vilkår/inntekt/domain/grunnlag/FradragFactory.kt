package vilkår.inntekt.domain.grunnlag

import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode

data object FradragFactory {
    fun nyFradragsperiode(
        fradragstype: Fradragstype,
        månedsbeløp: Double,
        periode: Periode,
        utenlandskInntekt: UtenlandskInntekt? = null,
        tilhører: FradragTilhører,
    ): FradragForPeriode {
        return FradragForPeriode(
            periode = periode,
            fradragstype = fradragstype,
            månedsbeløp = månedsbeløp,
            utenlandskInntekt = utenlandskInntekt,
            tilhører = tilhører,
        )
    }

    fun nyMånedsperiode(
        fradragstype: Fradragstype,
        månedsbeløp: Double,
        måned: Måned,
        utenlandskInntekt: UtenlandskInntekt? = null,
        tilhører: FradragTilhører,
    ): FradragForMåned {
        return FradragForMåned(
            måned = måned,
            fradragstype = fradragstype,
            månedsbeløp = månedsbeløp,
            utenlandskInntekt = utenlandskInntekt,
            tilhører = tilhører,
        )
    }

    fun periodiser(fradrag: Fradrag): List<FradragForMåned> =
        fradrag.periode.måneder().map {
            FradragForMåned(
                fradragstype = fradrag.fradragstype,
                månedsbeløp = fradrag.månedsbeløp,
                måned = it,
                utenlandskInntekt = fradrag.utenlandskInntekt,
                tilhører = fradrag.tilhører,
            )
        }
}
