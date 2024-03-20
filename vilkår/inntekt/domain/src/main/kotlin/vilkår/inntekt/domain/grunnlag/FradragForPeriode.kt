package vilkår.inntekt.domain.grunnlag

import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.tid.periode.Periode
import java.math.BigDecimal

/**
 * Et fradrag for en periode lengre enn en måned (i noen tilfeller er perioden bare en måned).
 * Brukes for eksempel av [no.nav.su.se.bakover.domain.beregning.BeregningMedFradragBeregnetMånedsvis]
 * Dersom fradraget er for kun en måned, se [FradragForMåned].
 */
data class FradragForPeriode(
    override val fradragstype: Fradragstype,
    override val månedsbeløp: Double,
    override val periode: Periode,
    override val utenlandskInntekt: UtenlandskInntekt? = null,
    override val tilhører: FradragTilhører,
) : Fradrag {

    init {
        require(månedsbeløp >= 0) { "Fradrag kan ikke være negative" }
    }

    override fun tilFradragForMåned(): List<FradragForMåned> = this.periode.måneder().map {
        FradragForMåned(
            fradragstype = fradragstype,
            månedsbeløp = månedsbeløp,
            måned = it,
            utenlandskInntekt = utenlandskInntekt,
            tilhører = tilhører,
        )
    }

    override fun tilFradragForPeriode(): FradragForPeriode = this

    override fun oppdaterBeløp(beløp: BigDecimal): Fradrag = this.copy(månedsbeløp = beløp.toDouble())

    override fun copy(args: CopyArgs.Snitt): Fradrag? {
        return args.snittFor(periode)?.let { copy(periode = it) }
    }
}
