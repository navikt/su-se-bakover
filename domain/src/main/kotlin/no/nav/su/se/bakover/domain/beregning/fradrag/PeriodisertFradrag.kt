package no.nav.su.se.bakover.domain.beregning.fradrag

import no.nav.su.se.bakover.common.periode.Periode

internal data class PeriodisertFradrag(
    private val type: Fradragstype,
    override val månedsbeløp: Double,
    override val periode: Periode,
    override val utenlandskInntekt: UtenlandskInntekt? = null,
    override val tilhører: FradragTilhører
) : Fradrag {
    init {
        require(månedsbeløp >= 0) { "Fradrag kan ikke være negative" }
        require(periode.getAntallMåneder() == 1) { "Periodiserte fradrag kan bare gjelde for en enkelt måned" }
    }

    override val fradragstype: Fradragstype = type
}
