package no.nav.su.se.bakover.domain.beregning.fradrag

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.CopyArgs

internal data class PeriodisertFradrag(
    private val type: Fradragstype,
    override val månedsbeløp: Double,
    override val periode: Periode,
    override val utenlandskInntekt: UtenlandskInntekt? = null,
    override val tilhører: FradragTilhører,
) : Fradrag {
    init {
        require(månedsbeløp >= 0) { "Fradrag kan ikke være negative" }
        require(periode.getAntallMåneder() == 1) { "Periodiserte fradrag kan bare gjelde for en enkelt måned" }
    }

    override val fradragstype: Fradragstype = type

    override fun copy(args: CopyArgs.Snitt): Fradrag? {
        return args.snittFor(periode)?.let { copy(periode = it) }
    }

    internal fun forskyv(måneder: Int): PeriodisertFradrag {
        return copy(
            type = type,
            månedsbeløp = månedsbeløp,
            periode = periode.forskyv(måneder),
            utenlandskInntekt = utenlandskInntekt,
            tilhører = tilhører,
        )
    }
}
