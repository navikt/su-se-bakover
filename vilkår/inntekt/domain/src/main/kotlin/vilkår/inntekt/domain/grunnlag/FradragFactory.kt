package vilkår.inntekt.domain.grunnlag

import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifiseringer
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

    fun nyUføreFradrag(
        forventetInntekt: Int,
        periode: Periode,
    ): FradragForPeriode {
        val månedsbeløp = forventetInntekt / 12.0
        return FradragForPeriode(
            periode = periode,
            fradragstype = Fradragstype.ForventetInntekt,
            månedsbeløp = månedsbeløp,
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
            benyttetRegel = Regelspesifiseringer.REGEL_FRADRAG_MED_UFØRE.benyttRegelspesifisering(
                verdi = månedsbeløp.toString(),
                avhengigeRegler = listOf(
                    // TODO bjg
                ),
            ),
        )
    }
}
