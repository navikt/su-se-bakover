package no.nav.su.se.bakover.domain.beregning.fradrag

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.CopyArgs

internal data class IkkePeriodisertFradrag(
    override val opprettet: Tidspunkt,
    override val fradragstype: Fradragstype,
    override val månedsbeløp: Double,
    override val periode: Periode,
    override val utenlandskInntekt: UtenlandskInntekt? = null,
    override val tilhører: FradragTilhører,
) : Fradrag {

    init {
        require(månedsbeløp >= 0) { "Fradrag kan ikke være negative" }
    }

    override fun copy(args: CopyArgs.Tidslinje) = when (args) {
        CopyArgs.Tidslinje.Full -> {
            this.copy()
        }
        is CopyArgs.Tidslinje.NyPeriode -> {
            this.copy(periode = args.periode)
        }
    }
}
