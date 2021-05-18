package no.nav.su.se.bakover.domain.beregning.fradrag

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.CopyArgs

internal data class IkkePeriodisertFradrag(
    private val type: Fradragstype,
    override val månedsbeløp: Double,
    override val periode: Periode,
    override val utenlandskInntekt: UtenlandskInntekt? = null,
    override val tilhører: FradragTilhører,
) : Fradrag {
    init {
        require(månedsbeløp >= 0) { "Fradrag kan ikke være negative" }
    }

    override val fradragstype: Fradragstype = type

    override fun copy(args: CopyArgs.BegrensetTil): Fradrag? {
        return args.begrensTil(periode)?.let { copy(periode = it) }
    }
}
