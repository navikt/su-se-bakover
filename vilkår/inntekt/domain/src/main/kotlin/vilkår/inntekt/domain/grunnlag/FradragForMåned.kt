package vilkår.inntekt.domain.grunnlag

import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.tilMåned
import java.math.BigDecimal

/**
 * Et fradrag for en spesifikk måned.
 * Brukes for eksempel av [no.nav.su.se.bakover.domain.beregning.Månedsberegning]
 * Dersom du trenger et fradrag for en lengre periode, bruk [FradragForPeriode].
 */
data class FradragForMåned(
    override val fradragstype: Fradragstype,
    override val månedsbeløp: Double,
    val måned: Måned,
    override val utenlandskInntekt: UtenlandskInntekt? = null,
    override val tilhører: FradragTilhører,
) : Fradrag {

    init {
        require(månedsbeløp >= 0.0) { "Fradrag kan ikke være negative" }
    }

    override fun oppdaterBeløp(beløp: BigDecimal): Fradrag = this.copy(månedsbeløp = beløp.toDouble())

    override val periode: Måned = måned

    override fun copy(args: CopyArgs.Snitt): Fradrag? {
        return args.snittFor(periode)?.let { copy(måned = it.tilMåned()) }
    }

    fun nyPeriode(periode: Periode): FradragForPeriode {
        return FradragForPeriode(
            fradragstype = fradragstype,
            månedsbeløp = månedsbeløp,
            periode = periode,
            utenlandskInntekt = utenlandskInntekt,
            tilhører = tilhører,
        )
    }
}
